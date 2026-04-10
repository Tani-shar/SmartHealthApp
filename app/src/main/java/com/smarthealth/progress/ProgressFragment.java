package com.smarthealth.progress;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.*;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.firebase.firestore.DocumentSnapshot;
import com.smarthealth.databinding.FragmentProgressBinding;
import com.smarthealth.models.BmiLog;
import com.smarthealth.models.MealLog;
import com.smarthealth.utils.FirebaseHelper;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.*;

public class ProgressFragment extends Fragment {

    private FragmentProgressBinding binding;
    private String userName = "User";
    private double currentBmi = 0;
    private String bmiCategory = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentProgressBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        loadUserInfo();
        loadBmiChart();
        loadCalorieChart();
        loadBurnedChart();
        binding.btnShareProgress.setOnClickListener(v -> shareProgress());
    }

    private void loadUserInfo() {
        String uid = FirebaseHelper.getInstance().getCurrentUid();
        if (uid == null) return;
        FirebaseHelper.getInstance().usersCollection().document(uid).get()
            .addOnSuccessListener(doc -> {
                if (!isAdded() || doc == null) return;
                String name = doc.getString("displayName");
                if (name != null) userName = name;
                Double bmi = doc.getDouble("bmiCurrent");
                if (bmi != null) currentBmi = bmi;
                String cat = doc.getString("bmiCategory");
                if (cat != null) bmiCategory = cat;
            });
    }

    private void loadBmiChart() {
        String uid = FirebaseHelper.getInstance().getCurrentUid();
        if (uid == null) return;

        FirebaseHelper.getInstance().bmiLogsCollection(uid)
            .orderBy("timestamp").limit(7).get()
            .addOnSuccessListener(snapshots -> {
                if (!isAdded() || snapshots == null) return;
                List<Entry> entries = new ArrayList<>();
                List<String> labels = new ArrayList<>();
                int i = 0;
                SimpleDateFormat sdf = new SimpleDateFormat("MMM d", Locale.getDefault());
                for (DocumentSnapshot doc : snapshots) {
                    BmiLog log = doc.toObject(BmiLog.class);
                    if (log != null) {
                        entries.add(new Entry(i, (float) log.getBmi()));
                        labels.add(sdf.format(new Date(log.getTimestamp())));
                        i++;
                    }
                }
                if (entries.isEmpty()) {
                    binding.tvNoBmiData.setVisibility(View.VISIBLE);
                    binding.bmiChart.setVisibility(View.GONE);
                    return;
                }
                LineDataSet dataSet = new LineDataSet(entries, "BMI");
                dataSet.setColor(Color.parseColor("#1A73E8"));
                dataSet.setCircleColor(Color.parseColor("#1A73E8"));
                dataSet.setLineWidth(2f);
                dataSet.setCircleRadius(4f);
                dataSet.setDrawValues(true);
                dataSet.setValueTextSize(10f);
                dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
                binding.bmiChart.setData(new LineData(dataSet));
                binding.bmiChart.getDescription().setEnabled(false);
                binding.bmiChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
                binding.bmiChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
                binding.bmiChart.getXAxis().setGranularity(1f);
                binding.bmiChart.getLegend().setEnabled(true);
                binding.bmiChart.animateX(800);
                binding.bmiChart.invalidate();
            });
    }

    private void loadCalorieChart() {
        String uid = FirebaseHelper.getInstance().getCurrentUid();
        if (uid == null) return;

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat labelSdf = new SimpleDateFormat("EEE", Locale.getDefault());
        Calendar cal = Calendar.getInstance();

        Map<String, Integer> calMap = new LinkedHashMap<>();
        List<String> dateLabels = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            cal.setTime(new Date());
            cal.add(Calendar.DAY_OF_YEAR, -i);
            calMap.put(sdf.format(cal.getTime()), 0);
            dateLabels.add(labelSdf.format(cal.getTime()));
        }

        FirebaseHelper.getInstance().mealLogsCollection(uid)
            .whereGreaterThanOrEqualTo("date", sdf.format(getDateDaysAgo(6))).get()
            .addOnSuccessListener(snapshots -> {
                if (!isAdded() || snapshots == null) return;
                for (DocumentSnapshot doc : snapshots) {
                    MealLog meal = doc.toObject(MealLog.class);
                    if (meal != null && calMap.containsKey(meal.getDate()))
                        calMap.put(meal.getDate(), calMap.get(meal.getDate()) + meal.getCalories());
                }
                List<BarEntry> entries = new ArrayList<>();
                int idx = 0;
                for (int val : calMap.values()) entries.add(new BarEntry(idx++, val));
                BarDataSet dataSet = new BarDataSet(entries, "Calories Consumed");
                dataSet.setColor(Color.parseColor("#43A047"));
                dataSet.setValueTextSize(9f);
                binding.calorieChart.setData(new BarData(dataSet));
                binding.calorieChart.getDescription().setEnabled(false);
                binding.calorieChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(dateLabels));
                binding.calorieChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
                binding.calorieChart.getXAxis().setGranularity(1f);
                binding.calorieChart.getAxisRight().setEnabled(false);
                binding.calorieChart.animateY(800);
                binding.calorieChart.invalidate();
            });
    }

    private void loadBurnedChart() {
        String uid = FirebaseHelper.getInstance().getCurrentUid();
        if (uid == null) return;

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat labelSdf = new SimpleDateFormat("EEE", Locale.getDefault());
        
        Map<String, Double> burnedMap = new LinkedHashMap<>();
        List<String> dateLabels = new ArrayList<>();
        List<String> dateKeys = new ArrayList<>();
        
        for (int i = 6; i >= 0; i--) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, -i);
            String key = sdf.format(cal.getTime());
            burnedMap.put(key, 0.0);
            dateKeys.add(key);
            dateLabels.add(labelSdf.format(cal.getTime()));
        }

        FirebaseHelper.getInstance().usersCollection().document(uid).get()
            .addOnSuccessListener(userDoc -> {
                if (!isAdded() || userDoc == null) return;

                // 1. Add calories from steps (approx 0.04 kcal per step)
                for (String key : dateKeys) {
                    Long steps = userDoc.getLong("stepsToday_" + key);
                    if (steps != null) {
                        burnedMap.put(key, burnedMap.get(key) + (steps * 0.04));
                    }
                }

                // 2. Add calories from workout logs
                long weekAgoMillis = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
                FirebaseHelper.getInstance().workoutLogsCollection(uid)
                    .whereGreaterThanOrEqualTo("timestamp", weekAgoMillis)
                    .get()
                    .addOnSuccessListener(workoutSnaps -> {
                        if (!isAdded() || workoutSnaps == null) return;
                        
                        for (DocumentSnapshot doc : workoutSnaps) {
                            Long calories = doc.getLong("caloriesBurned");
                            Long ts = doc.getLong("timestamp");
                            if (calories != null && ts != null) {
                                String dateKey = sdf.format(new Date(ts));
                                if (burnedMap.containsKey(dateKey)) {
                                    burnedMap.put(dateKey, burnedMap.get(dateKey) + calories);
                                }
                            }
                        }

                        // Render chart
                        List<BarEntry> entries = new ArrayList<>();
                        int idx = 0;
                        for (double val : burnedMap.values()) {
                            entries.add(new BarEntry(idx++, (float) val));
                        }

                        BarDataSet dataSet = new BarDataSet(entries, "Calories Burned");
                        dataSet.setColor(Color.parseColor("#F44336")); // Red for burned
                        dataSet.setValueTextSize(9f);
                        
                        binding.burnedChart.setData(new BarData(dataSet));
                        binding.burnedChart.getDescription().setEnabled(false);
                        binding.burnedChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(dateLabels));
                        binding.burnedChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
                        binding.burnedChart.getXAxis().setGranularity(1f);
                        binding.burnedChart.getAxisRight().setEnabled(false);
                        binding.burnedChart.animateY(1000);
                        binding.burnedChart.invalidate();
                    });
            });
    }

    private void shareProgress() {
        try {
            View shareView = binding.cardShareSummary;
            shareView.setVisibility(View.VISIBLE);

            binding.tvShareName.setText(userName + "'s Health Progress");
            binding.tvShareBmi.setText(currentBmi > 0
                ? String.format(Locale.getDefault(), "BMI: %.1f (%s)", currentBmi, bmiCategory)
                : "BMI: Not yet calculated");
            binding.tvShareDate.setText("Shared on " +
                new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(new Date()));

            shareView.measure(
                View.MeasureSpec.makeMeasureSpec(shareView.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            shareView.layout(0, 0, shareView.getMeasuredWidth(), shareView.getMeasuredHeight());

            Bitmap bitmap = Bitmap.createBitmap(shareView.getWidth(),
                shareView.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            shareView.draw(canvas);

            File cachePath = new File(requireContext().getCacheDir(), "images");
            cachePath.mkdirs();
            File imageFile = new File(cachePath, "progress_share.png");
            FileOutputStream stream = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            stream.close();

            Uri imageUri = FileProvider.getUriForFile(requireContext(),
                requireContext().getPackageName() + ".fileprovider", imageFile);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
            shareIntent.putExtra(Intent.EXTRA_TEXT,
                "Check out my health progress with Smart Health! 💪\n" +
                "BMI: " + (currentBmi > 0 ? String.format(Locale.getDefault(), "%.1f", currentBmi) : "N/A") +
                " | Category: " + bmiCategory);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share Progress"));

        } catch (Exception e) {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT,
                "Check out my health progress with Smart Health! 💪\n" +
                "Name: " + userName + "\n" +
                "BMI: " + (currentBmi > 0 ? String.format(Locale.getDefault(), "%.1f", currentBmi) : "N/A") +
                " | Category: " + bmiCategory + "\n" +
                "Tracked with Smart Health App");
            startActivity(Intent.createChooser(shareIntent, "Share Progress"));
        }
    }

    private Date getDateDaysAgo(int days) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -days);
        return c.getTime();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
