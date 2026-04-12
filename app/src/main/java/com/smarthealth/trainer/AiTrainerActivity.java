package com.smarthealth.trainer;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.smarthealth.databinding.ActivityAiTrainerBinding;
import com.smarthealth.utils.GeminiHelper;

import java.util.ArrayList;
import java.util.List;

import com.smarthealth.utils.FirebaseHelper;

/**
 * AI Step-by-Step Exercise Trainer.
 * Uses Gemini to generate step-by-step exercise instructions
 * and allows the user to navigate through them one at a time.
 */
public class AiTrainerActivity extends AppCompatActivity {

    private ActivityAiTrainerBinding binding;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private List<String> steps = new ArrayList<>();
    private int currentStep = 0;
    private String selectedExercise = "Squats";

    // User profile (loaded from Firestore)
    private String workoutLocation = "home"; // "gym" or "home"
    private String fitnessGoal = "maintain";
    private String fitnessLevel = "beginner";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAiTrainerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        loadUserProfile();
        setupUI();
    }

    private void setupUI() {
        binding.btnBack.setOnClickListener(v -> finish());

        // Exercise selection
        binding.chipGroupExercise.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == binding.chipBicepCurl.getId()) {
                selectedExercise = "Bicep Curls";
            } else if (id == binding.chipSquat.getId()) {
                selectedExercise = "Squats";
            } else if (id == binding.chipPushUp.getId()) {
                selectedExercise = "Push-ups";
            }
            // Reset when exercise changes
            steps.clear();
            currentStep = 0;
            binding.tvStepInstruction.setText("Select an exercise and tap 'Generate Steps' to get started.");
            binding.tvStepNumber.setText("?");
            binding.progressSteps.setProgress(0);
            binding.tvStepIndicator.setText("Step 0 of 0");
            binding.btnPrevious.setEnabled(false);
            binding.btnNext.setEnabled(false);
            binding.btnGenerate.setVisibility(View.VISIBLE);
        });

        // Generate button
        binding.btnGenerate.setOnClickListener(v -> generateSteps());

        // Navigation
        binding.btnPrevious.setOnClickListener(v -> {
            if (currentStep > 0) {
                currentStep--;
                displayStep();
            }
        });

        binding.btnNext.setOnClickListener(v -> {
            if (currentStep < steps.size() - 1) {
                currentStep++;
                displayStep();
            } else {
                // Last step — show completion
                binding.tvStepNumber.setText("✓");
                binding.tvStepInstruction.setText(
                        "🎉 Great job! You've completed all steps for " + selectedExercise + ".\n\n" +
                        "Now try performing the exercise with proper form. " +
                        "You can use the AI Camera Workout feature to get real-time feedback!"
                );
                binding.progressSteps.setProgress(100);
                binding.btnNext.setEnabled(false);
            }
        });
    }

    private void generateSteps() {
        binding.progressLoading.setVisibility(View.VISIBLE);
        binding.tvStepInstruction.setText("Generating step-by-step guide...");
        binding.btnGenerate.setEnabled(false);

        String locationContext;
        if ("gym".equalsIgnoreCase(workoutLocation)) {
            locationContext = "The user works out at a GYM with access to machines, barbells, " +
                    "dumbbells, cables, and full equipment. Include machine/weight-based variations " +
                    "where appropriate.";
        } else {
            locationContext = "The user works out at HOME with minimal/no equipment. Focus on " +
                    "bodyweight variations and movements that need no machines.";
        }

        String goalContext = "maintain";
        if ("lose_weight".equals(fitnessGoal)) goalContext = "lose weight (fat loss)";
        else if ("build_muscle".equals(fitnessGoal)) goalContext = "build muscle (hypertrophy)";

        String prompt = "You are a professional fitness trainer. Generate a detailed step-by-step guide " +
                "for performing " + selectedExercise + " with perfect form.\n\n" +
                locationContext + "\n" +
                "User goal: " + goalContext + " | Level: " + fitnessLevel + "\n\n" +
                "Requirements:\n" +
                "- Provide exactly 6 steps\n" +
                "- Each step should be 1-3 sentences\n" +
                "- Include setup, execution, and form cues\n" +
                "- Mention sets, reps, and rest time recommendations\n" +
                "- Include progressive overload tip (how to progress over weeks)\n" +
                "- Total workout should fit within 60 minutes\n" +
                "- Mention common mistakes to avoid in relevant steps\n" +
                "- Use clear, " + fitnessLevel + "-friendly language\n\n" +
                "Format EXACTLY like this (use plain text, no markdown, no bold):\n" +
                "STEP 1: Starting Position\n" +
                "Stand with your feet shoulder-width apart.\n\n" +
                "STEP 2: The Movement\n" +
                "Lower your body slowly.\n\n" +
                "Continue this exact format for all 6 steps. " +
                "Do NOT use markdown formatting like ** or ##. Plain text only.";

        GeminiHelper.getInstance().generateText(prompt, new GeminiHelper.GeminiCallback() {
            @Override
            public void onSuccess(String response) {
                mainHandler.post(() -> {
                    binding.progressLoading.setVisibility(View.GONE);
                    binding.btnGenerate.setEnabled(true);
                    parseSteps(response);
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    binding.progressLoading.setVisibility(View.GONE);
                    binding.btnGenerate.setEnabled(true);
                    // Use GeminiHelper fallback (should already provide steps)
                    // but also show a toast for context
                    Toast.makeText(AiTrainerActivity.this,
                            "Using offline guide: " + error, Toast.LENGTH_SHORT).show();
                    binding.tvStepInstruction.setText("AI unavailable. Tap 'Generate Steps' to try again.");
                    binding.btnGenerate.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void parseSteps(String response) {
        steps.clear();

        if (response == null || response.trim().isEmpty()) {
            binding.tvStepInstruction.setText("Empty response. Please try again.");
            binding.btnGenerate.setVisibility(View.VISIBLE);
            return;
        }

        // Clean up markdown formatting Gemini might add despite instructions
        String cleaned = response
                .replace("**", "")
                .replace("##", "")
                .replace("# ", "")
                .trim();

        // Strategy 1: Split by "Step N:" or "STEP N:" (case-insensitive)
        String[] parts = cleaned.split("(?i)\\bstep\\s*\\d+\\s*[:\\-]");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && trimmed.length() > 10) {
                steps.add(trimmed);
            }
        }

        // Strategy 2: Try numbered list ("1.", "2.", "1)", etc.)
        if (steps.isEmpty()) {
            parts = cleaned.split("(?m)^\\s*\\d+[.)\\-]\\s+");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty() && trimmed.length() > 10) {
                    steps.add(trimmed);
                }
            }
        }

        // Strategy 3: Split by double newline
        if (steps.isEmpty()) {
            parts = cleaned.split("\n\\s*\n");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty() && trimmed.length() > 10) {
                    steps.add(trimmed);
                }
            }
        }

        // Strategy 4: Show whole response as one step
        if (steps.isEmpty()) {
            steps.add(cleaned);
        }

        currentStep = 0;
        binding.btnGenerate.setVisibility(View.GONE);
        displayStep();
    }

    private void displayStep() {
        if (steps.isEmpty()) return;

        String stepText = steps.get(currentStep);
        binding.tvStepNumber.setText(String.valueOf(currentStep + 1));
        binding.tvStepInstruction.setText(stepText);

        int progress = (int) (((currentStep + 1) * 100.0) / steps.size());
        binding.progressSteps.setProgress(progress);
        binding.tvStepIndicator.setText("Step " + (currentStep + 1) + " of " + steps.size());

        binding.btnPrevious.setEnabled(currentStep > 0);
        binding.btnNext.setEnabled(true);
        binding.btnNext.setText(currentStep < steps.size() - 1 ? "Next →" : "Complete ✓");
    }

    private void loadUserProfile() {
        String uid = FirebaseHelper.getInstance().getCurrentUid();
        if (uid == null) return;

        FirebaseHelper.getInstance().usersCollection().document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) return;
                    String loc = doc.getString("workoutLocation");
                    if (loc != null) workoutLocation = loc;
                    String goal = doc.getString("fitnessGoal");
                    if (goal != null) fitnessGoal = goal;
                    String level = doc.getString("fitnessLevel");
                    if (level != null) fitnessLevel = level;
                });
    }
}
