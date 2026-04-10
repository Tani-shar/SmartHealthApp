package com.smarthealth.calories;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.*;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.DialogFragment;
import com.bumptech.glide.Glide;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.smarthealth.databinding.DialogAddMealBinding;
import com.smarthealth.models.MealLog;
import com.smarthealth.utils.FirebaseHelper;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

public class AddMealDialogFragment extends DialogFragment {

    public interface OnMealAddedListener { void onMealAdded(MealLog meal); }

    private static final String ARG_NAME    = "name";
    private static final String ARG_CAL     = "cal";
    private static final String ARG_PROTEIN = "protein";
    private static final String ARG_CARBS   = "carbs";
    private static final String ARG_FAT     = "fat";

    private DialogAddMealBinding binding;
    private OnMealAddedListener listener;
    private Uri photoUri;
    private String uploadedPhotoUrl = null;
    private boolean isUploading = false;

    // Camera launcher
    private final ActivityResultLauncher<Uri> cameraLauncher =
        registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
            if (success && photoUri != null) {
                showPhotoPreview(photoUri);
                uploadPhotoToFirebase(photoUri);
            }
        });

    // Gallery launcher
    private final ActivityResultLauncher<String> galleryLauncher =
        registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                photoUri = uri;
                showPhotoPreview(uri);
                uploadPhotoToFirebase(uri);
            }
        });

    public static AddMealDialogFragment newInstance(
            String name, int cal, double protein, double carbs, double fat,
            OnMealAddedListener listener) {
        AddMealDialogFragment f = new AddMealDialogFragment();
        f.listener = listener;
        Bundle args = new Bundle();
        if (name != null) args.putString(ARG_NAME, name);
        args.putInt(ARG_CAL, cal);
        args.putDouble(ARG_PROTEIN, protein);
        args.putDouble(ARG_CARBS, carbs);
        args.putDouble(ARG_FAT, fat);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = DialogAddMealBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ArrayAdapter<String> mealTypeAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item,
                new String[]{"Breakfast", "Lunch", "Dinner", "Snack"});
        mealTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerMealType.setAdapter(mealTypeAdapter);

        // Pre-fill from scan
        Bundle args = getArguments();
        if (args != null) {
            String name = args.getString(ARG_NAME, "");
            int    cal  = args.getInt(ARG_CAL, 0);
            double pro  = args.getDouble(ARG_PROTEIN, 0);
            double carb = args.getDouble(ARG_CARBS, 0);
            double fat  = args.getDouble(ARG_FAT, 0);
            if (name != null && !name.isEmpty()) binding.etFoodName.setText(name);
            if (cal > 0)  binding.etCalories.setText(String.valueOf(cal));
            if (pro > 0)  binding.etProtein.setText(String.format(Locale.getDefault(), "%.1f", pro));
            if (carb > 0) binding.etCarbs.setText(String.format(Locale.getDefault(), "%.1f", carb));
            if (fat > 0)  binding.etFat.setText(String.format(Locale.getDefault(), "%.1f", fat));
            if (cal > 0) {
                binding.tvScanNote.setVisibility(View.VISIBLE);
                binding.tvScanNote.setText("Nutrition auto-filled from barcode (per 100g). Adjust for your serving size.");
            }
        }

        // Photo buttons
        binding.btnTakePhoto.setOnClickListener(v -> takePhoto());
        binding.btnChoosePhoto.setOnClickListener(v -> galleryLauncher.launch("image/*"));
        binding.btnAdd.setOnClickListener(v -> addMeal());
        binding.btnCancel.setOnClickListener(v -> dismiss());
    }

    private void takePhoto() {
        try {
            File photoFile = File.createTempFile("meal_", ".jpg",
                requireContext().getCacheDir());
            photoUri = FileProvider.getUriForFile(requireContext(),
                requireContext().getPackageName() + ".fileprovider", photoFile);
            cameraLauncher.launch(photoUri);
        } catch (Exception e) {
            Toast.makeText(getContext(), "Camera error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showPhotoPreview(Uri uri) {
        binding.ivMealPhoto.setVisibility(View.VISIBLE);
        Glide.with(this).load(uri).centerCrop().into(binding.ivMealPhoto);
        binding.tvPhotoStatus.setVisibility(View.VISIBLE);
        binding.tvPhotoStatus.setText("Uploading photo...");
    }

    private void uploadPhotoToFirebase(Uri uri) {
        isUploading = true;
        String uid = FirebaseHelper.getInstance().getCurrentUid();
        if (uid == null) { isUploading = false; return; }

        String fileName = "meals/" + uid + "/" + System.currentTimeMillis() + ".jpg";
        StorageReference ref = FirebaseStorage.getInstance().getReference(fileName);

        ref.putFile(uri)
            .addOnSuccessListener(taskSnapshot ->
                ref.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                    uploadedPhotoUrl = downloadUri.toString();
                    isUploading = false;
                    if (isAdded()) {
                        binding.tvPhotoStatus.setText("✓ Photo uploaded");
                    }
                }))
            .addOnFailureListener(e -> {
                isUploading = false;
                if (isAdded()) {
                    binding.tvPhotoStatus.setText("Photo upload failed — meal will be saved without photo");
                }
            });
    }

    private void addMeal() {
        if (isUploading) {
            Toast.makeText(getContext(), "Please wait, photo is uploading...", Toast.LENGTH_SHORT).show();
            return;
        }

        String food = binding.etFoodName.getText().toString().trim();
        String calS = binding.etCalories.getText().toString().trim();
        String proS = binding.etProtein.getText().toString().trim();
        String carS = binding.etCarbs.getText().toString().trim();
        String fatS = binding.etFat.getText().toString().trim();

        if (food.isEmpty() || calS.isEmpty()) {
            Toast.makeText(getContext(), "Food name and calories are required",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String[] mealTypes = {"breakfast", "lunch", "dinner", "snack"};
        String mealType = mealTypes[binding.spinnerMealType.getSelectedItemPosition()];
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        MealLog meal = new MealLog(
            food,
            Integer.parseInt(calS),
            proS.isEmpty() ? 0 : Double.parseDouble(proS),
            carS.isEmpty() ? 0 : Double.parseDouble(carS),
            fatS.isEmpty() ? 0 : Double.parseDouble(fatS),
            mealType,
            today
        );

        if (uploadedPhotoUrl != null) {
            meal.setPhotoUrl(uploadedPhotoUrl);
        }

        if (listener != null) listener.onMealAdded(meal);
        dismiss();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
