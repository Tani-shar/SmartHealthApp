package com.smarthealth.profile;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.*;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.bumptech.glide.Glide;
import com.smarthealth.MainActivity;
import com.smarthealth.R;
import com.smarthealth.databinding.FragmentProfileBinding;
import com.smarthealth.injury.InjuryModeManager;
import com.smarthealth.notifications.ReminderScheduler;
import com.smarthealth.utils.FirebaseHelper;
import java.util.Locale;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private int breakfastHour = 8, breakfastMin = 0;
    private int workoutHour = 18, workoutMin = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        loadUserData();

        binding.btnBreakfastTime.setOnClickListener(v -> showTimePicker(true));
        binding.btnWorkoutTime.setOnClickListener(v -> showTimePicker(false));

        binding.btnLogout.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).logout();
            }
        });

        // Injury Mode chips
        binding.chipGroupInjury.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            String injury;
            if (id == binding.chipKnee.getId()) injury = "knee";
            else if (id == binding.chipShoulder.getId()) injury = "shoulder";
            else if (id == binding.chipBack.getId()) injury = "back";
            else injury = "none";

            InjuryModeManager.getInstance().saveToFirestore(injury);
            Toast.makeText(getContext(),
                    "none".equals(injury) ? "Injury mode disabled" : "Injury mode: " + injury,
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void loadUserData() {
        String uid = FirebaseHelper.getInstance().getCurrentUid();
        if (uid == null) return;

        FirebaseHelper.getInstance().usersCollection().document(uid).get()
            .addOnSuccessListener(doc -> {
                if (!isAdded() || doc == null) return;
                
                String name = doc.getString("displayName");
                String email = doc.getString("email");
                String photo = doc.getString("photoUrl");
                
                binding.tvName.setText(name != null ? name : "User");
                binding.tvEmail.setText(email != null ? email : "");

                if (photo != null && !photo.isEmpty()) {
                    Glide.with(this).load(photo).into(binding.ivProfile);
                }

                // Load saved reminder times if they exist
                Long bH = doc.getLong("remind_breakfast_h");
                Long bM = doc.getLong("remind_breakfast_m");
                if (bH != null && bM != null) {
                    breakfastHour = bH.intValue();
                    breakfastMin = bM.intValue();
                    binding.btnBreakfastTime.setText(String.format(Locale.getDefault(), "%02d:%02d", breakfastHour, breakfastMin));
                }

                Long wH = doc.getLong("remind_workout_h");
                Long wM = doc.getLong("remind_workout_m");
                if (wH != null && wM != null) {
                    workoutHour = wH.intValue();
                    workoutMin = wM.intValue();
                    binding.btnWorkoutTime.setText(String.format(Locale.getDefault(), "%02d:%02d", workoutHour, workoutMin));
                }

                // Load injury mode
                String injury = doc.getString("injuryArea");
                if (injury != null) {
                    switch (injury) {
                        case "knee": binding.chipKnee.setChecked(true); break;
                        case "shoulder": binding.chipShoulder.setChecked(true); break;
                        case "back": binding.chipBack.setChecked(true); break;
                        default: binding.chipNoInjury.setChecked(true); break;
                    }
                    InjuryModeManager.getInstance().setInjury(injury);
                }
            });
    }

    private void showTimePicker(boolean isBreakfast) {
        int hour = isBreakfast ? breakfastHour : workoutHour;
        int minute = isBreakfast ? breakfastMin : workoutMin;

        TimePickerDialog timePickerDialog = new TimePickerDialog(getContext(),
            (view, hourOfDay, minuteOfHour) -> {
                if (isBreakfast) {
                    breakfastHour = hourOfDay;
                    breakfastMin = minuteOfHour;
                    binding.btnBreakfastTime.setText(String.format(Locale.getDefault(), "%02d:%02d", breakfastHour, breakfastMin));
                    saveReminderTime("breakfast", breakfastHour, breakfastMin);
                } else {
                    workoutHour = hourOfDay;
                    workoutMin = minuteOfHour;
                    binding.btnWorkoutTime.setText(String.format(Locale.getDefault(), "%02d:%02d", workoutHour, workoutMin));
                    saveReminderTime("workout", workoutHour, workoutMin);
                }
            }, hour, minute, true);
        timePickerDialog.show();
    }

    private void saveReminderTime(String type, int h, int m) {
        String uid = FirebaseHelper.getInstance().getCurrentUid();
        if (uid == null) return;

        FirebaseHelper.getInstance().usersCollection().document(uid)
            .update("remind_" + type + "_h", h, "remind_" + type + "_m", m)
            .addOnSuccessListener(aVoid -> {
                if (isAdded()) {
                    ReminderScheduler.scheduleCustomReminder(requireContext(), h, m, 
                        type.equals("breakfast") ? 100 : 101,
                        type.equals("breakfast") ? "Time to log your breakfast!" : "Ready for your daily workout?");
                    Toast.makeText(getContext(), "Reminder updated", Toast.LENGTH_SHORT).show();
                }
            });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
