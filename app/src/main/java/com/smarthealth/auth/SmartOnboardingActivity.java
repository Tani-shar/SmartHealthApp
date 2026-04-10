package com.smarthealth.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.smarthealth.MainActivity;
import com.smarthealth.databinding.ActivitySmartOnboardingBinding;
import com.smarthealth.utils.BmiUtils;
import com.smarthealth.utils.FirebaseHelper;
import com.smarthealth.utils.GeminiHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * Smart Onboarding with AI-generated fitness plan.
 * Collects: height, weight, goal, experience, injuries.
 * Uses Gemini to generate a personalized plan.
 */
public class SmartOnboardingActivity extends AppCompatActivity {

    private ActivitySmartOnboardingBinding binding;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private int currentStep = 1;
    private static final int TOTAL_STEPS = 5;

    // Collected data
    private String gender = "male";
    private String fitnessGoal = "maintain";
    private String experienceLevel = "beginner";
    private String injuryArea = "none";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySmartOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupSpinners();
        setupChipListeners();
        setupNavigation();
        showStep(1);
    }

    private void setupSpinners() {
        ArrayAdapter<String> activityAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"Sedentary", "Lightly Active", "Moderately Active", "Very Active", "Extra Active"});
        activityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerActivity.setAdapter(activityAdapter);
    }

    private void setupChipListeners() {
        // Goal chips
        binding.chipGroupGoal.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == binding.chipLoseWeight.getId()) fitnessGoal = "lose_weight";
            else if (id == binding.chipMaintain.getId()) fitnessGoal = "maintain";
            else if (id == binding.chipBuildMuscle.getId()) fitnessGoal = "build_muscle";
        });

        // Experience chips
        binding.chipGroupExperience.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == binding.chipBeginner.getId()) experienceLevel = "beginner";
            else if (id == binding.chipIntermediate.getId()) experienceLevel = "intermediate";
            else if (id == binding.chipAdvanced.getId()) experienceLevel = "advanced";
        });

        // Injury chips
        binding.chipGroupInjury.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == binding.chipNoInjury.getId()) injuryArea = "none";
            else if (id == binding.chipKnee.getId()) injuryArea = "knee";
            else if (id == binding.chipShoulder.getId()) injuryArea = "shoulder";
            else if (id == binding.chipBack.getId()) injuryArea = "back";
        });
    }

    private void setupNavigation() {
        binding.btnNextStep.setOnClickListener(v -> {
            if (currentStep < TOTAL_STEPS) {
                if (currentStep == 1 && !validateStep1()) return;
                currentStep++;
                showStep(currentStep);
                if (currentStep == TOTAL_STEPS) {
                    generateAiPlan();
                }
            } else {
                // Final step — save and go to main
                saveAndFinish();
            }
        });

        binding.btnPrevStep.setOnClickListener(v -> {
            if (currentStep > 1) {
                currentStep--;
                showStep(currentStep);
            }
        });
    }

    private boolean validateStep1() {
        String age = binding.etAge.getText().toString().trim();
        String height = binding.etHeight.getText().toString().trim();
        String weight = binding.etWeight.getText().toString().trim();

        if (age.isEmpty() || height.isEmpty() || weight.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void showStep(int step) {
        // Hide all steps
        binding.step1.setVisibility(View.GONE);
        binding.step2.setVisibility(View.GONE);
        binding.step3.setVisibility(View.GONE);
        binding.step4.setVisibility(View.GONE);
        binding.step5.setVisibility(View.GONE);

        // Show current step
        switch (step) {
            case 1: binding.step1.setVisibility(View.VISIBLE); break;
            case 2: binding.step2.setVisibility(View.VISIBLE); break;
            case 3: binding.step3.setVisibility(View.VISIBLE); break;
            case 4: binding.step4.setVisibility(View.VISIBLE); break;
            case 5: binding.step5.setVisibility(View.VISIBLE); break;
        }

        // Update progress
        int progress = (step * 100) / TOTAL_STEPS;
        binding.progressOnboarding.setProgress(progress);
        binding.tvStepLabel.setText("Step " + step + " of " + TOTAL_STEPS);

        // Navigation buttons
        binding.btnPrevStep.setVisibility(step > 1 ? View.VISIBLE : View.GONE);
        if (step == TOTAL_STEPS) {
            binding.btnNextStep.setText("Get Started 🚀");
        } else {
            binding.btnNextStep.setText("Next →");
        }

        // Get gender from radio group
        gender = binding.rbMale.isChecked() ? "male"
                : binding.rbFemale.isChecked() ? "female" : "other";
    }

    private void generateAiPlan() {
        binding.progressAiPlan.setVisibility(View.VISIBLE);
        binding.tvAiStatus.setText("🤖 AI is generating your personalized plan...");
        binding.cardAiPlan.setVisibility(View.GONE);
        binding.btnNextStep.setEnabled(false);

        int age = Integer.parseInt(binding.etAge.getText().toString().trim());
        double heightCm = Double.parseDouble(binding.etHeight.getText().toString().trim());
        double weightKg = Double.parseDouble(binding.etWeight.getText().toString().trim());
        double bmi = BmiUtils.calculateBmi(weightKg, heightCm);

        String[] activityKeys = {"sedentary", "light", "moderate", "active", "very_active"};
        String activityLevel = activityKeys[binding.spinnerActivity.getSelectedItemPosition()];

        String prompt = "You are a certified fitness and nutrition coach. " +
                "Generate a personalized fitness plan for this user:\n\n" +
                "Profile:\n" +
                "- Age: " + age + ", Gender: " + gender + "\n" +
                "- Height: " + heightCm + " cm, Weight: " + weightKg + " kg, BMI: " + String.format("%.1f", bmi) + "\n" +
                "- Fitness Goal: " + formatGoal(fitnessGoal) + "\n" +
                "- Experience Level: " + experienceLevel + "\n" +
                "- Activity Level: " + activityLevel + "\n" +
                "- Injuries: " + (injuryArea.equals("none") ? "None" : injuryArea) + "\n\n" +
                "Provide:\n" +
                "1. 📊 Daily Calorie Target (number)\n" +
                "2. 🥗 Macro Distribution (protein/carbs/fat percentages)\n" +
                "3. 🏋️ 5-Day Workout Plan (exercise name, sets, reps for each day)\n" +
                "4. 🍎 Top 5 Foods to Prioritize and Top 5 to Avoid\n\n" +
                "Consider the injury area and adjust exercises accordingly.\n" +
                "Keep the plan practical and achievable. Format with emojis and clear sections.";

        GeminiHelper.getInstance().generateText(prompt, new GeminiHelper.GeminiCallback() {
            @Override
            public void onSuccess(String response) {
                mainHandler.post(() -> {
                    binding.progressAiPlan.setVisibility(View.GONE);
                    binding.tvAiStatus.setText("✅ Your plan is ready!");
                    binding.cardAiPlan.setVisibility(View.VISIBLE);
                    binding.tvAiPlan.setText(response);
                    binding.btnNextStep.setEnabled(true);
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    binding.progressAiPlan.setVisibility(View.GONE);
                    binding.tvAiStatus.setText("⚠️ Could not generate plan: " + error);
                    binding.btnNextStep.setEnabled(true);
                });
            }
        });
    }

    private void saveAndFinish() {
        String uid = FirebaseHelper.getInstance().getCurrentUid();
        if (uid == null) return;

        int age = Integer.parseInt(binding.etAge.getText().toString().trim());
        double heightCm = Double.parseDouble(binding.etHeight.getText().toString().trim());
        double weightKg = Double.parseDouble(binding.etWeight.getText().toString().trim());

        String[] activityKeys = {"sedentary", "light", "moderate", "active", "very_active"};
        String activityLevel = activityKeys[binding.spinnerActivity.getSelectedItemPosition()];

        double bmi = BmiUtils.calculateBmi(weightKg, heightCm);
        String bmiCategory = BmiUtils.getCategory(bmi);
        int calories = BmiUtils.calculateDailyCalories(weightKg, heightCm, age, gender, activityLevel);

        Map<String, Object> updates = new HashMap<>();
        updates.put("age", age);
        updates.put("gender", gender);
        updates.put("heightCm", heightCm);
        updates.put("weightKg", weightKg);
        updates.put("bmiCurrent", bmi);
        updates.put("bmiCategory", bmiCategory);
        updates.put("activityLevel", activityLevel);
        updates.put("fitnessGoal", fitnessGoal);
        updates.put("dailyCalorieTarget", calories);
        updates.put("experienceLevel", experienceLevel);
        updates.put("injuryArea", injuryArea);

        // Save AI plan if available
        String aiPlan = binding.tvAiPlan.getText().toString();
        if (!aiPlan.isEmpty()) {
            updates.put("aiWorkoutPlan", aiPlan);
        }

        binding.btnNextStep.setEnabled(false);
        FirebaseHelper.getInstance().usersCollection().document(uid)
                .update(updates)
                .addOnSuccessListener(v -> {
                    startActivity(new Intent(this, MainActivity.class));
                    finishAffinity();
                })
                .addOnFailureListener(e -> {
                    binding.btnNextStep.setEnabled(true);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private String formatGoal(String goal) {
        switch (goal) {
            case "lose_weight": return "Lose Weight";
            case "maintain": return "Maintain Weight";
            case "build_muscle": return "Build Muscle";
            default: return goal;
        }
    }
}
