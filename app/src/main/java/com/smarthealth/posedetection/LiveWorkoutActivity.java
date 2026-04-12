package com.smarthealth.posedetection;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;
import com.smarthealth.databinding.ActivityLiveWorkoutBinding;
import com.smarthealth.utils.FirebaseHelper;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Full-screen camera workout activity with real-time pose detection,
 * rep counting, form validation, and timer management.
 *
 * Camera lifecycle fixes:
 * - ProcessCameraProvider stored as field to prevent GC
 * - isCameraBound flag prevents duplicate bindings
 * - onResume() rebinds if needed
 * - Pose analysis runs off main thread (on cameraExecutor)
 */
public class LiveWorkoutActivity extends AppCompatActivity {

    private static final String TAG = "LiveWorkout";
    private static final int CAMERA_PERMISSION_CODE = 200;

    private ActivityLiveWorkoutBinding binding;
    private ExecutorService cameraExecutor;
    private ExecutorService analysisExecutor;
    private PoseDetector poseDetector;

    // Camera lifecycle management
    private ProcessCameraProvider cameraProvider;
    private boolean isCameraBound = false;

    private final PoseAnalyzer poseAnalyzer = new PoseAnalyzer();
    private final FormValidator formValidator = new FormValidator();

    // Workout state
    private boolean isWorkoutActive = false;
    private boolean isTimerPaused = false;
    private long workoutStartTime = 0;
    private long pausedDuration = 0;
    private long pauseStartTime = 0;

    // Timer
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isWorkoutActive) {
                long elapsed;
                if (isTimerPaused) {
                    elapsed = pauseStartTime - workoutStartTime - pausedDuration;
                } else {
                    elapsed = System.currentTimeMillis() - workoutStartTime - pausedDuration;
                }
                int seconds = (int) (elapsed / 1000);
                int minutes = seconds / 60;
                seconds = seconds % 60;
                binding.tvTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
                timerHandler.postDelayed(this, 500);
            }
        }
    };

    // Form error tracking for pause logic
    private int consecutiveBadFrames = 0;
    private static final int BAD_FRAME_THRESHOLD = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on during workout
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        binding = ActivityLiveWorkoutBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 2 threads: one for ML Kit frame intake, one for backpressure headroom
        cameraExecutor = Executors.newFixedThreadPool(2);
        // Separate executor for pose analysis so it never starves the camera pipeline
        analysisExecutor = Executors.newSingleThreadExecutor();

        // Initialize Pose Detector in STREAM_MODE for real-time
        PoseDetectorOptions options = new PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build();
        poseDetector = PoseDetection.getClient(options);

        // Load injury setting
        loadInjurySetting();

        setupUI();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }
    }

    private void loadInjurySetting() {
        String uid = FirebaseHelper.getInstance().getCurrentUid();
        if (uid == null) return;

        FirebaseHelper.getInstance().usersCollection().document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        String injury = doc.getString("injuryArea");
                        if (injury != null) {
                            formValidator.setInjuryArea(injury);
                        }
                    }
                });
    }

    private void setupUI() {
        // Default exercise
        poseAnalyzer.setExercise(PoseAnalyzer.ExerciseType.SQUAT);
        binding.poseOverlay.setExerciseName("Squats");
        binding.poseOverlay.setFrontCamera(true);

        // Exercise chip selection
        binding.chipGroupExercise.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == binding.chipBicepCurl.getId()) {
                poseAnalyzer.setExercise(PoseAnalyzer.ExerciseType.BICEP_CURL);
                binding.poseOverlay.setExerciseName("Bicep Curls");
            } else if (id == binding.chipSquat.getId()) {
                poseAnalyzer.setExercise(PoseAnalyzer.ExerciseType.SQUAT);
                binding.poseOverlay.setExerciseName("Squats");
            } else if (id == binding.chipPushUp.getId()) {
                poseAnalyzer.setExercise(PoseAnalyzer.ExerciseType.PUSH_UP);
                binding.poseOverlay.setExerciseName("Push-ups");
            }
        });

        // Start button
        binding.btnStart.setOnClickListener(v -> startWorkout());

        // Stop button
        binding.btnStop.setOnClickListener(v -> stopWorkout());

        // Back button
        binding.btnBack.setOnClickListener(v -> {
            if (isWorkoutActive) {
                new AlertDialog.Builder(this)
                        .setTitle("End Workout?")
                        .setMessage("Your current workout progress will be saved.")
                        .setPositiveButton("End", (d, w) -> {
                            stopWorkout();
                            finish();
                        })
                        .setNegativeButton("Continue", null)
                        .show();
            } else {
                finish();
            }
        });
    }

    private void startCamera() {
        // Prevent duplicate camera binding
        if (isCameraBound) return;

        binding.loadingOverlay.setVisibility(View.VISIBLE);

        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                cameraProvider = future.get();

                // Fix random shutdowns on many devices
                binding.previewView.setImplementationMode(
                        androidx.camera.view.PreviewView.ImplementationMode.COMPATIBLE);

                // Preview
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.previewView.getSurfaceProvider());

                // Image analysis for pose detection — KEEP_ONLY_LATEST is critical
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                analysis.setAnalyzer(cameraExecutor, this::processFrame);

                // Use front camera for self-monitoring
                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                // Unbind before rebinding to prevent duplicate use cases
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, analysis);
                isCameraBound = true;

                binding.loadingOverlay.setVisibility(View.GONE);

            } catch (Exception e) {
                Log.e(TAG, "Camera init error", e);
                binding.loadingOverlay.setVisibility(View.GONE);
                Toast.makeText(this, "Camera error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Rebind camera if it was unbound (e.g., after onPause in some devices)
        if (!isCameraBound && cameraProvider != null
                && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }

    @androidx.annotation.OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
    private void processFrame(ImageProxy imageProxy) {
        // Guard: if activity is finishing, just close and bail
        if (isFinishing() || isDestroyed()) {
            imageProxy.close();
            return;
        }

        if (imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        // Frame skipping for performance — lightweight counter, no blocking
        if (!poseAnalyzer.shouldProcessFrame()) {
            imageProxy.close();
            return;
        }

        final int imgWidth = imageProxy.getWidth();
        final int imgHeight = imageProxy.getHeight();

        InputImage image = InputImage.fromMediaImage(
                imageProxy.getImage(),
                imageProxy.getImageInfo().getRotationDegrees()
        );

        // ML Kit process() is async (Task-based).
        // imageProxy.close() MUST happen in addOnCompleteListener — the ONLY
        // callback guaranteed to fire exactly once regardless of success/failure.
        poseDetector.process(image)
                .addOnSuccessListener(pose -> {
                    // Dispatch heavy work to analysisExecutor — never block
                    // the camera pipeline or the ML Kit callback thread
                    analysisExecutor.execute(() -> {
                        if (isFinishing() || isDestroyed()) return;

                        if (isWorkoutActive) {
                            int reps = poseAnalyzer.analyzePose(pose);

                            FormValidator.FormFeedback feedback =
                                    formValidator.validateForm(pose, poseAnalyzer.getCurrentExercise());

                            handleFormBasedTimer(feedback);

                            runOnUiThread(() -> {
                                if (!isFinishing() && !isDestroyed()) {
                                    binding.poseOverlay.updatePose(pose, feedback, reps,
                                            imgWidth, imgHeight);
                                }
                            });
                        } else {
                            FormValidator.FormFeedback idleFeedback =
                                    new FormValidator.FormFeedback(true, "Press START to begin", 0);
                            runOnUiThread(() -> {
                                if (!isFinishing() && !isDestroyed()) {
                                    binding.poseOverlay.updatePose(pose, idleFeedback, 0,
                                            imgWidth, imgHeight);
                                }
                            });
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Pose detection failed", e);
                })
                .addOnCompleteListener(task -> {
                    // 🔴 ALWAYS close here — guaranteed single invocation
                    imageProxy.close();
                });
    }

    private void handleFormBasedTimer(FormValidator.FormFeedback feedback) {
        if (feedback.severity == 2) {
            consecutiveBadFrames++;
            if (consecutiveBadFrames >= BAD_FRAME_THRESHOLD && !isTimerPaused) {
                // Pause timer on bad form
                isTimerPaused = true;
                pauseStartTime = System.currentTimeMillis();
                runOnUiThread(() -> binding.poseOverlay.setTimerPaused(true));
            }
        } else {
            if (isTimerPaused) {
                // Resume timer on good form
                pausedDuration += System.currentTimeMillis() - pauseStartTime;
                isTimerPaused = false;
                runOnUiThread(() -> binding.poseOverlay.setTimerPaused(false));
            }
            consecutiveBadFrames = 0;
        }
    }

    private void startWorkout() {
        isWorkoutActive = true;
        isTimerPaused = false;
        pausedDuration = 0;
        consecutiveBadFrames = 0;
        workoutStartTime = System.currentTimeMillis();
        poseAnalyzer.reset();

        binding.btnStart.setVisibility(View.GONE);
        binding.btnStop.setVisibility(View.VISIBLE);

        timerHandler.post(timerRunnable);
        Toast.makeText(this, "Workout started! 💪", Toast.LENGTH_SHORT).show();
    }

    private void stopWorkout() {
        isWorkoutActive = false;
        timerHandler.removeCallbacks(timerRunnable);

        long totalDuration;
        if (isTimerPaused) {
            totalDuration = pauseStartTime - workoutStartTime - pausedDuration;
        } else {
            totalDuration = System.currentTimeMillis() - workoutStartTime - pausedDuration;
        }
        int durationMinutes = (int) (totalDuration / (1000 * 60));
        if (durationMinutes < 1) durationMinutes = 1;

        int reps = poseAnalyzer.getRepCount();
        String exerciseName = getExerciseDisplayName(poseAnalyzer.getCurrentExercise());

        // Save to Firebase
        saveWorkoutLog(exerciseName, reps, durationMinutes);

        // Show summary
        new AlertDialog.Builder(this)
                .setTitle("Workout Complete! 🎉")
                .setMessage(String.format(Locale.getDefault(),
                        "Exercise: %s\nReps: %d\nDuration: %d min",
                        exerciseName, reps, durationMinutes))
                .setPositiveButton("Done", (d, w) -> finish())
                .setNegativeButton("New Workout", (d, w) -> {
                    poseAnalyzer.reset();
                    binding.tvTimer.setText("00:00");
                    binding.btnStart.setVisibility(View.VISIBLE);
                    binding.btnStop.setVisibility(View.GONE);
                })
                .setCancelable(false)
                .show();
    }

    private void saveWorkoutLog(String exerciseName, int reps, int durationMinutes) {
        String uid = FirebaseHelper.getInstance().getCurrentUid();
        if (uid == null) return;

        Map<String, Object> log = new HashMap<>();
        log.put("type", "AI Camera - " + exerciseName);
        log.put("exercise", exerciseName);
        log.put("reps", reps);
        log.put("durationMinutes", durationMinutes);
        log.put("timestamp", System.currentTimeMillis());
        log.put("source", "live_camera");

        FirebaseHelper.getInstance().workoutLogsCollection(uid).add(log);
    }

    private String getExerciseDisplayName(PoseAnalyzer.ExerciseType type) {
        switch (type) {
            case BICEP_CURL: return "Bicep Curls";
            case SQUAT: return "Squats";
            case PUSH_UP: return "Push-ups";
            default: return "Exercise";
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isWorkoutActive = false;
        timerHandler.removeCallbacks(timerRunnable);
        // Unbind camera explicitly and reset flag
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            isCameraBound = false;
        }
        cameraExecutor.shutdown();
        analysisExecutor.shutdown();
        poseDetector.close();
    }
}
