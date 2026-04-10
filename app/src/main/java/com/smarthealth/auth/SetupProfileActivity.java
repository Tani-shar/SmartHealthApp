package com.smarthealth.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.smarthealth.MainActivity;
import com.smarthealth.databinding.ActivitySetupProfileBinding;
import com.smarthealth.utils.BmiUtils;
import com.smarthealth.utils.FirebaseHelper;

public class SetupProfileActivity extends AppCompatActivity {

    private ActivitySetupProfileBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySetupProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Activity level spinner
        ArrayAdapter<String> activityAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"Sedentary", "Lightly Active", "Moderately Active", "Very Active", "Extra Active"});
        activityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerActivity.setAdapter(activityAdapter);

        // Fitness goal spinner
        ArrayAdapter<String> goalAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"Lose Weight", "Maintain Weight", "Build Muscle"});
        goalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerGoal.setAdapter(goalAdapter);

        binding.btnSaveProfile.setOnClickListener(v -> saveProfile());
    }

    private void saveProfile() {
        String ageStr    = binding.etAge.getText().toString().trim();
        String heightStr = binding.etHeight.getText().toString().trim();
        String weightStr = binding.etWeight.getText().toString().trim();

        if (ageStr.isEmpty() || heightStr.isEmpty() || weightStr.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        int    age      = Integer.parseInt(ageStr);
        double heightCm = Double.parseDouble(heightStr);
        double weightKg = Double.parseDouble(weightStr);

        String gender = binding.rbMale.isChecked() ? "male"
                      : binding.rbFemale.isChecked() ? "female" : "other";

        String[] activityKeys = {"sedentary","light","moderate","active","very_active"};
        String activityLevel  = activityKeys[binding.spinnerActivity.getSelectedItemPosition()];

        String[] goalKeys = {"lose_weight","maintain","build_muscle"};
        String fitnessGoal = goalKeys[binding.spinnerGoal.getSelectedItemPosition()];

        double bmi         = BmiUtils.calculateBmi(weightKg, heightCm);
        String bmiCategory = BmiUtils.getCategory(bmi);
        int    calories    = BmiUtils.calculateDailyCalories(weightKg, heightCm, age, gender, activityLevel);

        String uid = FirebaseHelper.getInstance().getCurrentUid();
        if (uid == null) return;

        setLoading(true);
        FirebaseHelper.getInstance().usersCollection().document(uid)
            .update(
                "age",                age,
                "gender",             gender,
                "heightCm",           heightCm,
                "weightKg",           weightKg,
                "bmiCurrent",         bmi,
                "bmiCategory",        bmiCategory,
                "activityLevel",      activityLevel,
                "fitnessGoal",        fitnessGoal,
                "dailyCalorieTarget", calories
            )
            .addOnSuccessListener(v -> {
                setLoading(false);
                startActivity(new Intent(this, MainActivity.class));
                finishAffinity();
            })
            .addOnFailureListener(e -> {
                setLoading(false);
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
    }

    private void setLoading(boolean loading) {
        binding.btnSaveProfile.setEnabled(!loading);
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}
