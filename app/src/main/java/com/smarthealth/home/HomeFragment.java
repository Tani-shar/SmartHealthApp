package com.smarthealth.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.smarthealth.R;
import com.smarthealth.databinding.FragmentHomeBinding;
import com.smarthealth.notifications.ReminderScheduler;
import com.smarthealth.posedetection.LiveWorkoutActivity;
import com.smarthealth.trainer.AiTrainerActivity;
import com.smarthealth.utils.BmiUtils;
import com.smarthealth.utils.FirebaseHelper;
import java.text.SimpleDateFormat;
import java.util.*;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private String todayDate;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        
        loadUserData();

        binding.btnGoToSteps.setOnClickListener(v ->
            Navigation.findNavController(v).navigate(R.id.stepsFragment));

        binding.btnGoToSocial.setOnClickListener(v ->
            Navigation.findNavController(v).navigate(R.id.socialFragment));

        binding.btnGoToAi.setOnClickListener(v ->
            Navigation.findNavController(v).navigate(R.id.aiSuggestionsFragment));

        binding.btnGoToLiveWorkout.setOnClickListener(v ->
            startActivity(new Intent(getActivity(), LiveWorkoutActivity.class)));

        binding.btnGoToTrainer.setOnClickListener(v ->
            startActivity(new Intent(getActivity(), AiTrainerActivity.class)));

        binding.switchNotifications.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) {
                ReminderScheduler.scheduleDailyReminders(requireContext());
            } else {
                ReminderScheduler.cancelAllReminders(requireContext());
            }
            saveNotificationPref(checked);
        });
    }

    private void loadUserData() {
        String uid = FirebaseHelper.getInstance().getCurrentUid();
        if (uid == null) return;

        FirebaseHelper.getInstance().usersCollection().document(uid)
            .get()
            .addOnSuccessListener(doc -> {
                if (!isAdded() || doc == null || !doc.exists()) return;

                String name    = doc.getString("displayName");
                Double bmi     = doc.getDouble("bmiCurrent");
                String cat     = doc.getString("bmiCategory");
                Long calTarget = doc.getLong("dailyCalorieTarget");
                Long steps     = doc.getLong("stepsToday_" + todayDate);
                Boolean notifs = doc.getBoolean("notificationsEnabled");

                binding.tvGreeting.setText("Hello, " + (name != null ? name : "there") + "!");

                if (bmi != null) {
                    binding.tvBmiValue.setText(String.format(Locale.getDefault(), "%.1f", bmi));
                    binding.tvBmiCategory.setText(cat != null ? cat : "—");
                    binding.tvHealthTip.setText(BmiUtils.getHealthTip(cat != null ? cat : ""));
                }

                if (calTarget != null) {
                    binding.tvCalorieTarget.setText(calTarget + " kcal / day");
                }

                binding.tvStepsToday.setText((steps != null ? steps : 0) + " steps today");
                
                binding.switchNotifications.setChecked(notifs == null || notifs);
            });
    }

    private void saveNotificationPref(boolean enabled) {
        String uid = FirebaseHelper.getInstance().getCurrentUid();
        if (uid == null) return;
        FirebaseHelper.getInstance().usersCollection()
            .document(uid).update("notificationsEnabled", enabled);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
