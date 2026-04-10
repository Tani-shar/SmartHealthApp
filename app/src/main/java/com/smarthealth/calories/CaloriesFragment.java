package com.smarthealth.calories;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Query;
import com.smarthealth.calories.scan.FoodScannerActivity;
import com.smarthealth.foodai.FoodImageAnalysisActivity;
import com.smarthealth.databinding.FragmentCaloriesBinding;
import com.smarthealth.models.MealLog;
import com.smarthealth.utils.FirebaseHelper;
import java.text.SimpleDateFormat;
import java.util.*;

import com.smarthealth.social.ActivityFeedHelper;

public class CaloriesFragment extends Fragment {

    private FragmentCaloriesBinding binding;
    private MealAdapter adapter;
    private final List<MealLog> mealList = new ArrayList<>();
    private int totalCaloriesToday = 0;
    private int calorieTarget = 2000;
    private String fitnessGoal = "maintain";
    private String todayDate;

    private final ActivityResultLauncher<Intent> scanLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                Intent data = result.getData();
                String name = data.getStringExtra(FoodScannerActivity.EXTRA_FOOD_NAME);
                int    cal  = data.getIntExtra(FoodScannerActivity.EXTRA_CALORIES, 0);
                double pro  = data.getDoubleExtra(FoodScannerActivity.EXTRA_PROTEIN, 0);
                double carb = data.getDoubleExtra(FoodScannerActivity.EXTRA_CARBS, 0);
                double fat  = data.getDoubleExtra(FoodScannerActivity.EXTRA_FAT, 0);
                // Open dialog pre-filled with scanned data
                openAddMealDialog(name, cal, pro, carb, fat);
            }
        });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentCaloriesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        .format(new Date());

        adapter = new MealAdapter(mealList, this::deleteMeal);
        binding.recyclerMeals.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerMeals.setAdapter(adapter);

        binding.fabAddMeal.setOnClickListener(v -> openAddMealDialog(null, 0, 0, 0, 0));
        binding.btnScanFood.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), FoodScannerActivity.class);
            scanLauncher.launch(intent);
        });

        // AI Food Scanner
        binding.btnAiFoodScanner.setOnClickListener(v -> {
            startActivity(new Intent(getActivity(), FoodImageAnalysisActivity.class));
        });

        loadCalorieTarget();
        loadRecentMeals();
    }

    private void loadCalorieTarget() {
        String uid = FirebaseHelper.getInstance().getCurrentUid();
        if (uid == null) return;
        FirebaseHelper.getInstance().usersCollection().document(uid)
            .get()
            .addOnSuccessListener(doc -> {
                if (!isAdded() || doc == null) return;
                Long target = doc.getLong("dailyCalorieTarget");
                if (target != null) {
                    calorieTarget = target.intValue();
                    updateCalorieDisplay();
                }
                String goal = doc.getString("fitnessGoal");
                if (goal != null) fitnessGoal = goal;
            });
    }

    private void loadRecentMeals() {
        String uid = FirebaseHelper.getInstance().getCurrentUid();
        if (uid == null) return;

        // Load last 5 days of meals
        long fiveDaysAgoMillis = System.currentTimeMillis() - (5L * 24 * 60 * 60 * 1000);

        FirebaseHelper.getInstance().mealLogsCollection(uid)
            .whereGreaterThanOrEqualTo("timestamp", fiveDaysAgoMillis)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener((snapshots, e) -> {
                if (!isAdded() || snapshots == null) return;
                mealList.clear();
                totalCaloriesToday = 0;
                for (DocumentSnapshot doc : snapshots) {
                    MealLog meal = doc.toObject(MealLog.class);
                    if (meal != null) {
                        meal.setId(doc.getId());
                        mealList.add(meal);
                        // Sum only today's calories for the summary card
                        if (todayDate.equals(meal.getDate())) {
                            totalCaloriesToday += meal.getCalories();
                        }
                    }
                }
                adapter.notifyDataSetChanged();
                updateCalorieDisplay();
            });
    }

    private void updateCalorieDisplay() {
        if (!isAdded()) return;
        binding.tvCaloriesConsumed.setText(totalCaloriesToday + " kcal");
        binding.tvCaloriesTarget.setText("/ " + calorieTarget + " kcal");

        int remaining = calorieTarget - totalCaloriesToday;
        binding.tvCaloriesRemaining.setText(
            remaining >= 0
            ? remaining + " kcal remaining"
            : Math.abs(remaining) + " kcal over target");

        int progress = (int) Math.min(100, (totalCaloriesToday / (double) calorieTarget) * 100);
        binding.progressCalories.setProgress(progress);

        // Check calorie alerts
        if (getContext() != null && totalCaloriesToday > 0) {
            CalorieAlertHelper.checkAndAlert(getContext(), totalCaloriesToday, calorieTarget, fitnessGoal);
        }
    }

    private void openAddMealDialog(String prefillName, int prefillCal,
                                   double prefillProtein, double prefillCarbs, double prefillFat) {
        AddMealDialogFragment dialog = AddMealDialogFragment.newInstance(
            prefillName, prefillCal, prefillProtein, prefillCarbs, prefillFat,
            meal -> {
                String uid = FirebaseHelper.getInstance().getCurrentUid();
                if (uid == null) return;
                FirebaseHelper.getInstance().mealLogsCollection(uid).add(meal);

                // Post to friends' activity feed
                FirebaseHelper.getInstance().usersCollection().document(uid).get()
                    .addOnSuccessListener(doc -> {
                        if (doc == null) return;
                        String name = doc.getString("displayName");
                        if (name == null) name = "Someone";
                        ActivityFeedHelper.postMealLogged(uid, name,
                            meal.getFoodName(), meal.getCalories(), meal.getPhotoUrl());
                    });
            });
        dialog.show(getChildFragmentManager(), "AddMeal");
    }

    private void deleteMeal(MealLog meal) {
        String uid = FirebaseHelper.getInstance().getCurrentUid();
        if (uid == null || meal.getId() == null) return;
        FirebaseHelper.getInstance().mealLogsCollection(uid)
            .document(meal.getId()).delete();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
