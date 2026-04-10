package com.smarthealth.steps;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.view.*;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.smarthealth.databinding.FragmentStepsBinding;
import com.smarthealth.utils.FirebaseHelper;
import java.text.SimpleDateFormat;
import java.util.*;

public class StepsFragment extends Fragment implements SensorEventListener {

    private FragmentStepsBinding binding;
    private SensorManager sensorManager;
    private Sensor stepSensor;
    private boolean sensorAvailable = false;

    private int stepsToday    = 0;
    private int stepsAtReset  = 0;  // Baseline sensor value for today
    private int stepGoal      = 8000;
    private String todayDate;
    private SharedPreferences prefs;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                setupStepSensor();
            } else {
                if (isAdded()) {
                    binding.tvSensorStatus.setText("Permission denied. Step tracking disabled.");
                    Toast.makeText(getContext(), "Activity recognition permission is required", Toast.LENGTH_LONG).show();
                }
            }
        });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentStepsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        prefs = requireContext().getSharedPreferences("StepPrefs", Context.MODE_PRIVATE);

        sensorManager = (SensorManager) requireActivity().getSystemService(Context.SENSOR_SERVICE);
        stepSensor    = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);

        if (stepSensor != null) {
            sensorAvailable = true;
            checkPermissionAndSetup();
        } else {
            binding.tvSensorStatus.setText("No step sensor found on this device");
        }

        loadLocalData();
        loadStepGoalFromFirebase();

        binding.btnSetGoal.setOnClickListener(v -> showSetGoalDialog());
    }

    private void loadLocalData() {
        String savedDate = prefs.getString("lastDate", "");
        if (todayDate.equals(savedDate)) {
            stepsAtReset = prefs.getInt("baseline", 0);
            stepsToday = prefs.getInt("todaySteps", 0);
        } else {
            // New day
            stepsAtReset = 0;
            stepsToday = 0;
            prefs.edit().putString("lastDate", todayDate)
                    .putInt("baseline", 0)
                    .putInt("todaySteps", 0)
                    .apply();
        }
        updateStepDisplay();
    }

    private void checkPermissionAndSetup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACTIVITY_RECOGNITION)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION);
            } else {
                setupStepSensor();
            }
        } else {
            setupStepSensor();
        }
    }

    private void setupStepSensor() {
        if (isAdded()) binding.tvSensorStatus.setText("Pedometer active");
        registerListener();
    }

    private void registerListener() {
        if (sensorAvailable && stepSensor != null) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    private void loadStepGoalFromFirebase() {
        String uid = FirebaseHelper.getInstance().getCurrentUid();
        if (uid == null) return;

        FirebaseHelper.getInstance().usersCollection().document(uid).get()
            .addOnSuccessListener(doc -> {
                if (!isAdded() || doc == null) return;
                Long goal = doc.getLong("stepGoal");
                if (goal != null) {
                    stepGoal = goal.intValue();
                    updateStepDisplay();
                }
            });
    }

    private void updateStepDisplay() {
        if (!isAdded()) return;
        binding.tvStepCount.setText(String.valueOf(stepsToday));
        binding.tvStepGoal.setText("Goal: " + stepGoal + " steps");

        int progress = (int) Math.min(100, (stepsToday / (double) stepGoal) * 100);
        binding.progressSteps.setProgress(progress);
        binding.tvStepPercent.setText(progress + "% of daily goal");

        double caloriesBurned = stepsToday * 0.04;
        binding.tvCaloriesBurned.setText(String.format(Locale.getDefault(), "~%.0f kcal burned", caloriesBurned));

        double distanceKm = (stepsToday * 0.762) / 1000.0;
        binding.tvDistance.setText(String.format(Locale.getDefault(), "~%.2f km walked", distanceKm));

        if (stepsToday >= stepGoal) binding.tvMotivation.setText("🎉 Goal reached! Amazing work!");
        else if (stepsToday >= stepGoal * 0.75) binding.tvMotivation.setText("Almost there! Keep going! 💪");
        else if (stepsToday >= stepGoal * 0.5) binding.tvMotivation.setText("Halfway there! Great progress!");
        else binding.tvMotivation.setText("Every step counts. Keep moving! 🚶");
    }

    private void showSetGoalDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext());
        builder.setTitle("Set Daily Step Goal");
        final android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(stepGoal));
        builder.setView(input);
        builder.setPositiveButton("Save", (dialog, which) -> {
            String val = input.getText().toString().trim();
            if (!val.isEmpty()) {
                stepGoal = Integer.parseInt(val);
                String uid = FirebaseHelper.getInstance().getCurrentUid();
                if (uid != null) {
                    FirebaseHelper.getInstance().usersCollection().document(uid).update("stepGoal", stepGoal);
                }
                updateStepDisplay();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            int totalSteps = (int) event.values[0];

            if (stepsAtReset == 0 || totalSteps < stepsAtReset) {
                // Initial baseline for today OR device was rebooted (sensor reset to 0)
                stepsAtReset = totalSteps;
                prefs.edit().putInt("baseline", stepsAtReset).apply();
            }

            int newStepsToday = totalSteps - stepsAtReset;
            if (newStepsToday != stepsToday) {
                stepsToday = newStepsToday;
                prefs.edit().putInt("todaySteps", stepsToday).apply();
                if (isAdded()) updateStepDisplay();
                
                // Sync to Firebase occasionally (every 100 steps)
                if (stepsToday % 100 == 0) {
                    saveStepsToFirebase();
                }
            }
        }
    }

    private void saveStepsToFirebase() {
        String uid = FirebaseHelper.getInstance().getCurrentUid();
        if (uid == null) return;
        FirebaseHelper.getInstance().usersCollection().document(uid)
            .update("stepsToday_" + todayDate, stepsToday);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onResume() {
        super.onResume();
        if (sensorAvailable) registerListener();
    }

    @Override
    public void onPause() {
        super.onPause();
        saveStepsToFirebase(); // Final sync
        if (sensorAvailable) sensorManager.unregisterListener(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
