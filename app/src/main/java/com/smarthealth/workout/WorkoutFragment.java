package com.smarthealth.workout;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.smarthealth.databinding.FragmentWorkoutBinding;
import com.smarthealth.models.WorkoutExercise;
import com.smarthealth.posedetection.LiveWorkoutActivity;
import com.smarthealth.posedetection.VideoAnalysisActivity;
import com.smarthealth.utils.BmiUtils;
import com.smarthealth.utils.FirebaseHelper;
import java.util.List;
import java.util.Locale;

public class WorkoutFragment extends Fragment {

    private FragmentWorkoutBinding binding;
    private long startTime = 0;
    private boolean isWorkoutActive = false;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private String currentCategory = "Normal Weight";
    private double userWeight = 70.0;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isWorkoutActive) {
                long millis = System.currentTimeMillis() - startTime;
                int seconds = (int) (millis / 1000);
                int minutes = seconds / 60;
                seconds = seconds % 60;
                binding.tvTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
                timerHandler.postDelayed(this, 500);
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentWorkoutBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        loadWorkouts();

        binding.btnStartWorkout.setOnClickListener(v -> startWorkout());
        binding.btnStopWorkout.setOnClickListener(v -> stopWorkout());

        // AI Camera Workout
        binding.btnLiveWorkout.setOnClickListener(v -> {
            startActivity(new Intent(getActivity(), LiveWorkoutActivity.class));
        });

        // Analyze Video
        binding.btnAnalyzeVideo.setOnClickListener(v -> {
            startActivity(new Intent(getActivity(), VideoAnalysisActivity.class));
        });
    }

    private void startWorkout() {
        isWorkoutActive = true;
        startTime = System.currentTimeMillis();
        binding.btnStartWorkout.setVisibility(View.GONE);
        binding.cardActiveWorkout.setVisibility(View.VISIBLE);
        timerHandler.post(timerRunnable);
        Toast.makeText(getContext(), "Workout started! Good luck!", Toast.LENGTH_SHORT).show();
    }

    private void stopWorkout() {
        isWorkoutActive = false;
        timerHandler.removeCallbacks(timerRunnable);
        
        long durationMillis = System.currentTimeMillis() - startTime;
        int durationMinutes = (int) (durationMillis / (1000 * 60));
        
        // Use at least 1 minute for calorie calculation if they stopped it early
        int calcMinutes = durationMinutes > 0 ? durationMinutes : 1;
        double caloriesBurned = calculateCaloriesBurned(calcMinutes);

        showWorkoutSummary(durationMinutes, caloriesBurned);

        binding.btnStartWorkout.setVisibility(View.VISIBLE);
        binding.cardActiveWorkout.setVisibility(View.GONE);
        binding.tvTimer.setText("00:00");
    }

    private double calculateCaloriesBurned(int minutes) {
        // MET (Metabolic Equivalent of Task) estimation
        double met = 5.0; // General moderate intensity
        if (currentCategory.contains("Obese")) {
            met = 3.5; // Adjusted for lower intensity recommendations
        } else if (currentCategory.equals("Normal Weight") || currentCategory.equals("Underweight")) {
            met = 6.5; // Adjusted for higher intensity strength/cardio
        }
        
        // Formula: Calories = MET * weight(kg) * duration(hours)
        return met * userWeight * (minutes / 60.0);
    }

    private void showWorkoutSummary(int minutes, double calories) {
        new AlertDialog.Builder(requireContext())
            .setTitle("Workout Complete! 🎉")
            .setMessage(String.format(Locale.getDefault(),
                "Great job! You stayed active.\n\nDuration: %d min\nEstimated Burn: %.1f kcal",
                minutes, calories))
            .setPositiveButton("Finish", null)
            .show();
            
        saveWorkoutToFirebase(minutes, calories);
    }

    private void saveWorkoutToFirebase(int minutes, double calories) {
        String uid = FirebaseHelper.getInstance().getCurrentUid();
        if (uid == null) return;
        
        java.util.Map<String, Object> log = new java.util.HashMap<>();
        log.put("durationMinutes", minutes);
        log.put("caloriesBurned", (int)calories);
        log.put("timestamp", System.currentTimeMillis());
        log.put("type", "General Workout (" + currentCategory + ")");

        FirebaseHelper.getInstance().workoutLogsCollection(uid).add(log);
    }

    private void loadWorkouts() {
        String uid = FirebaseHelper.getInstance().getCurrentUid();
        if (uid == null) return;

        FirebaseHelper.getInstance().usersCollection().document(uid)
            .get()
            .addOnSuccessListener(doc -> {
                if (!isAdded() || doc == null || !doc.exists()) return;
                
                currentCategory = doc.getString("bmiCategory");
                if (currentCategory == null) currentCategory = "Normal Weight";
                
                Double weight = doc.getDouble("weightKg");
                if (weight != null) userWeight = weight;

                binding.tvWorkoutTitle.setText("Workouts for: " + currentCategory);

                List<WorkoutExercise> exercises = BmiUtils.getWorkoutRecommendations(currentCategory);
                WorkoutAdapter adapter = new WorkoutAdapter(exercises);
                binding.recyclerWorkouts.setLayoutManager(new LinearLayoutManager(getContext()));
                binding.recyclerWorkouts.setAdapter(adapter);
            });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isWorkoutActive = false;
        timerHandler.removeCallbacks(timerRunnable);
        binding = null;
    }
}
