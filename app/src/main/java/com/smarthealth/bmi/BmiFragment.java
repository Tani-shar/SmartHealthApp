package com.smarthealth.bmi;

import android.os.Bundle;
import android.view.*;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.smarthealth.databinding.FragmentBmiBinding;
import com.smarthealth.models.BmiLog;
import com.smarthealth.utils.BmiUtils;
import com.smarthealth.utils.FirebaseHelper;

public class BmiFragment extends Fragment {

    private FragmentBmiBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentBmiBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnCalculate.setOnClickListener(v -> calculateBmi());
        binding.btnSave.setOnClickListener(v -> saveBmiLog());
        binding.btnSave.setEnabled(false);
    }

    private void calculateBmi() {
        String wStr = binding.etWeight.getText().toString().trim();
        String hStr = binding.etHeight.getText().toString().trim();

        if (wStr.isEmpty() || hStr.isEmpty()) {
            Toast.makeText(getContext(), "Please enter weight and height", Toast.LENGTH_SHORT).show();
            return;
        }

        double weight = Double.parseDouble(wStr);
        double height = Double.parseDouble(hStr);
        double bmi    = BmiUtils.calculateBmi(weight, height);
        String cat    = BmiUtils.getCategory(bmi);

        binding.tvBmiResult.setText(String.format("BMI: %.2f", bmi));
        binding.tvBmiCategory.setText("Category: " + cat);
        binding.tvHealthTip.setText(BmiUtils.getHealthTip(cat));
        binding.btnSave.setEnabled(true);

        // Store in tag for save action
        binding.btnSave.setTag(new double[]{bmi, weight});
        binding.tvBmiCategory.setTag(cat);
    }

    private void saveBmiLog() {
        String uid = FirebaseHelper.getInstance().getCurrentUid();
        if (uid == null) return;

        double[] vals = (double[]) binding.btnSave.getTag();
        String   cat  = (String) binding.tvBmiCategory.getTag();

        BmiLog log = new BmiLog(vals[0], vals[1], cat);

        FirebaseHelper.getInstance().bmiLogsCollection(uid)
            .add(log)
            .addOnSuccessListener(ref -> {
                // Also update user's current BMI in their profile
                FirebaseHelper.getInstance().usersCollection().document(uid)
                    .update("bmiCurrent", vals[0], "bmiCategory", cat,
                            "weightKg", vals[1]);
                Toast.makeText(getContext(), "BMI saved successfully!", Toast.LENGTH_SHORT).show();
                binding.btnSave.setEnabled(false);
            })
            .addOnFailureListener(e ->
                Toast.makeText(getContext(), "Error saving: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
