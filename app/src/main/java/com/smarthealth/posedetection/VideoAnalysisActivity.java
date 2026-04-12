package com.smarthealth.posedetection;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;
import com.smarthealth.databinding.ActivityVideoAnalysisBinding;
import com.smarthealth.utils.GeminiHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Activity that allows user to select a workout video from gallery,
 * extracts frames, runs pose detection, counts reps, detects mistakes,
 * and sends summary to Gemini for coaching feedback.
 */
public class VideoAnalysisActivity extends AppCompatActivity {

    private static final String TAG = "VideoAnalysis";
    private static final long FRAME_INTERVAL_MS = 250; // Extract a frame every 250ms

    private ActivityVideoAnalysisBinding binding;
    private PoseDetector poseDetector;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private PoseAnalyzer.ExerciseType selectedExercise = PoseAnalyzer.ExerciseType.SQUAT;

    private final ActivityResultLauncher<String> videoPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    onVideoSelected(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVideoAnalysisBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Init pose detector (single image mode for video frames)
        PoseDetectorOptions options = new PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE)
                .build();
        poseDetector = PoseDetection.getClient(options);

        setupUI();
    }

    private void setupUI() {
        binding.btnBack.setOnClickListener(v -> finish());

        // Exercise chip selection
        binding.chipGroupExercise.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == binding.chipBicepCurl.getId()) {
                selectedExercise = PoseAnalyzer.ExerciseType.BICEP_CURL;
            } else if (id == binding.chipSquat.getId()) {
                selectedExercise = PoseAnalyzer.ExerciseType.SQUAT;
            } else if (id == binding.chipPushUp.getId()) {
                selectedExercise = PoseAnalyzer.ExerciseType.PUSH_UP;
            }
        });

        binding.btnSelectVideo.setOnClickListener(v -> {
            videoPickerLauncher.launch("video/*");
        });
    }

    private void onVideoSelected(Uri videoUri) {
        // Show thumbnail preview
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(this, videoUri);
            Bitmap thumbnail = retriever.getFrameAtTime(0);
            if (thumbnail != null) {
                binding.cardVideoPreview.setVisibility(View.VISIBLE);
                binding.ivVideoThumbnail.setImageBitmap(thumbnail);
            }
            retriever.release();
        } catch (Exception e) {
            Log.e(TAG, "Error loading thumbnail", e);
        }

        // Start analysis
        binding.cardProgress.setVisibility(View.VISIBLE);
        binding.resultsSection.setVisibility(View.GONE);
        binding.tvProgressStatus.setText("Extracting frames...");
        binding.progressBarHorizontal.setProgress(0);

        executor.execute(() -> analyzeVideo(videoUri));
    }

    private void analyzeVideo(Uri videoUri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, videoUri);

            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long durationMs = durationStr != null ? Long.parseLong(durationStr) : 0;

            if (durationMs == 0) {
                mainHandler.post(() -> {
                    Toast.makeText(this, "Could not read video duration", Toast.LENGTH_SHORT).show();
                    binding.cardProgress.setVisibility(View.GONE);
                });
                return;
            }

            // Extract frames
            List<Bitmap> frames = new ArrayList<>();
            long totalFrames = durationMs / FRAME_INTERVAL_MS;

            for (long timeMs = 0; timeMs < durationMs; timeMs += FRAME_INTERVAL_MS) {
                Bitmap frame = retriever.getFrameAtTime(
                        timeMs * 1000, // microseconds
                        MediaMetadataRetriever.OPTION_CLOSEST);
                if (frame != null) {
                    frames.add(frame);
                }

                int progress = (int) ((timeMs * 50) / durationMs); // 0-50% is extraction
                mainHandler.post(() -> {
                    binding.progressBarHorizontal.setProgress(progress);
                    binding.tvProgressStatus.setText("Extracting frames... " + frames.size());
                });
            }

            retriever.release();

            mainHandler.post(() -> binding.tvProgressStatus.setText("Running pose detection..."));

            // Process frames through pose detection
            processFrames(frames);

        } catch (Exception e) {
            Log.e(TAG, "Video analysis error", e);
            mainHandler.post(() -> {
                Toast.makeText(this, "Error analyzing video: " + e.getMessage(), Toast.LENGTH_LONG).show();
                binding.cardProgress.setVisibility(View.GONE);
            });
            try { retriever.release(); } catch (Exception ignored) {}
        }
    }

    private void processFrames(List<Bitmap> frames) {
        PoseAnalyzer analyzer = new PoseAnalyzer();
        analyzer.setExercise(selectedExercise);

        FormValidator validator = new FormValidator();

        List<String> allMistakes = new ArrayList<>();
        Map<String, Integer> mistakeCounts = new HashMap<>();
        int totalGoodFrames = 0;
        int totalAnalyzedFrames = 0;
        int processedCount = 0;

        for (int i = 0; i < frames.size(); i++) {
            Bitmap frame = frames.get(i);
            final int frameIndex = i;

            try {
                InputImage image = InputImage.fromBitmap(frame, 0);
                // Synchronous-ish processing via Tasks
                com.google.android.gms.tasks.Task<Pose> task = poseDetector.process(image);
                // Block on result (we're already on executor thread)
                com.google.android.gms.tasks.Tasks.await(task);
                Pose pose = task.getResult();

                if (pose != null) {
                    totalAnalyzedFrames++;
                    analyzer.analyzePose(pose);

                    FormValidator.FormFeedback feedback = validator.validateForm(pose, selectedExercise);
                    if (feedback.isCorrect) {
                        totalGoodFrames++;
                    } else {
                        String mistake = feedback.message;
                        mistakeCounts.put(mistake, mistakeCounts.getOrDefault(mistake, 0) + 1);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Frame " + frameIndex + " processing error", e);
            }

            processedCount++;
            int progress = 50 + (int) ((processedCount * 50.0) / frames.size());
            mainHandler.post(() -> {
                binding.progressBarHorizontal.setProgress(progress);
                binding.tvProgressStatus.setText("Analyzing frame " + (frameIndex + 1) + "/" + frames.size());
            });
        }

        // Build results
        int reps = analyzer.getRepCount();
        int formScore = totalAnalyzedFrames > 0 
                ? (int) ((totalGoodFrames * 100.0) / totalAnalyzedFrames)
                : 0;

        // Build mistake list
        for (Map.Entry<String, Integer> entry : mistakeCounts.entrySet()) {
            allMistakes.add(entry.getKey() + " (" + entry.getValue() + " frames)");
        }

        int incompleteReps = Math.max(0, reps / 5); // Rough estimate

        VideoAnalysisResult result = new VideoAnalysisResult(
                getExerciseName(selectedExercise),
                reps,
                Math.max(0, reps - incompleteReps),
                incompleteReps,
                allMistakes,
                formScore
        );

        // Display results
        mainHandler.post(() -> displayResults(result));
    }

    private void displayResults(VideoAnalysisResult result) {
        binding.cardProgress.setVisibility(View.GONE);
        binding.resultsSection.setVisibility(View.VISIBLE);

        binding.tvRepCount.setText(String.valueOf(result.getTotalReps()));
        binding.tvFormScore.setText(result.getFormScore() + "%");

        // Mistakes
        if (result.getMistakes() != null && !result.getMistakes().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String mistake : result.getMistakes()) {
                sb.append("• ").append(mistake).append("\n");
            }
            binding.tvMistakes.setText(sb.toString().trim());
        } else {
            binding.tvMistakes.setText("✅ No significant issues detected. Great form!");
        }

        // Send to Gemini for coaching
        binding.progressAiCoaching.setVisibility(View.VISIBLE);
        binding.tvAiCoaching.setText("Generating AI coaching feedback...");

        String prompt = "You are a professional fitness coach. Analyze this workout summary and provide " +
                "clear, actionable improvement tips. Be encouraging but specific.\n\n" +
                result.toSummary() + "\n\n" +
                "Provide:\n1. Overall assessment (1-2 sentences)\n" +
                "2. Top 3 specific corrections with how to fix them\n" +
                "3. One positive highlight\n" +
                "Keep response concise and motivational.";

        GeminiHelper.getInstance().generateText(prompt, new GeminiHelper.GeminiCallback() {
            @Override
            public void onSuccess(String response) {
                mainHandler.post(() -> {
                    binding.progressAiCoaching.setVisibility(View.GONE);
                    binding.tvAiCoaching.setText(response);
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    binding.progressAiCoaching.setVisibility(View.GONE);
                    // Provide useful coaching feedback based on form score
                    String fallbackCoaching = generateOfflineCoaching(result);
                    binding.tvAiCoaching.setText(fallbackCoaching);
                });
            }
        });
    }

    /**
     * Generate offline coaching feedback based on the analysis result.
     */
    private String generateOfflineCoaching(VideoAnalysisResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("⚠️ AI coaching unavailable. Here's automated feedback:\n\n");

        int score = result.getFormScore();
        if (score >= 80) {
            sb.append("📊 Overall: Excellent form! You're performing " +
                    result.getExercise() + " with great technique.\n\n");
        } else if (score >= 60) {
            sb.append("📊 Overall: Good effort on " + result.getExercise() +
                    "! Some areas need improvement.\n\n");
        } else {
            sb.append("📊 Overall: Your " + result.getExercise() +
                    " form needs work. Focus on the corrections below.\n\n");
        }

        sb.append("🔧 Tips:\n");
        sb.append("1. Focus on controlled, slow movements rather than speed\n");
        sb.append("2. Maintain proper breathing (exhale on exertion)\n");
        sb.append("3. Keep your core engaged throughout\n\n");

        if (result.getMistakes() != null && !result.getMistakes().isEmpty()) {
            sb.append("⚠️ Key areas to fix:\n");
            for (String mistake : result.getMistakes()) {
                sb.append("• ").append(mistake).append("\n");
            }
            sb.append("\n");
        }

        sb.append("⭐ Keep it up! Consistency beats perfection.\n");
        sb.append("\nTry again for personalized AI coaching.");
        return sb.toString();
    }

    private String getExerciseName(PoseAnalyzer.ExerciseType type) {
        switch (type) {
            case BICEP_CURL: return "Bicep Curls";
            case SQUAT: return "Squats";
            case PUSH_UP: return "Push-ups";
            default: return "Exercise";
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        poseDetector.close();
    }
}
