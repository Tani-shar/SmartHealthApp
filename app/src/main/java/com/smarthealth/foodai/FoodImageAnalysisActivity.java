package com.smarthealth.foodai;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.smarthealth.databinding.ActivityFoodImageAnalysisBinding;
import com.smarthealth.utils.FirebaseHelper;
import com.smarthealth.utils.GeminiHelper;

import java.io.File;
import java.io.InputStream;

/**
 * Captures or picks a food image and sends to Gemini Vision
 * for nutrition analysis with Indian food expertise.
 */
public class FoodImageAnalysisActivity extends AppCompatActivity {

    private ActivityFoodImageAnalysisBinding binding;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Bitmap capturedBitmap = null;
    private Uri cameraImageUri = null;
    private String fitnessGoal = "maintain";
    private int calorieTarget = 2000;

    private static final int CAMERA_PERMISSION_CODE = 300;

    // Camera capture launcher
    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success && cameraImageUri != null) {
                    loadImage(cameraImageUri);
                }
            });

    // Gallery picker launcher
    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    loadImage(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFoodImageAnalysisBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        loadUserProfile();
        setupUI();
    }

    private void loadUserProfile() {
        String uid = FirebaseHelper.getInstance().getCurrentUid();
        if (uid == null) return;

        FirebaseHelper.getInstance().usersCollection().document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        String goal = doc.getString("fitnessGoal");
                        if (goal != null) fitnessGoal = goal;
                        Long cal = doc.getLong("dailyCalorieTarget");
                        if (cal != null) calorieTarget = cal.intValue();
                    }
                });
    }

    private void setupUI() {
        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnCapture.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                launchCamera();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
            }
        });

        binding.btnGallery.setOnClickListener(v -> {
            galleryLauncher.launch("image/*");
        });

        binding.btnAnalyze.setOnClickListener(v -> analyzeFood());
    }

    private void launchCamera() {
        try {
            File imageFile = new File(getCacheDir(), "food_capture_" + System.currentTimeMillis() + ".jpg");
            cameraImageUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", imageFile);
            cameraLauncher.launch(cameraImageUri);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open camera", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadImage(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            capturedBitmap = BitmapFactory.decodeStream(inputStream);
            if (inputStream != null) inputStream.close();

            if (capturedBitmap != null) {
                // Scale down for API efficiency
                if (capturedBitmap.getWidth() > 1024) {
                    float scale = 1024f / capturedBitmap.getWidth();
                    capturedBitmap = Bitmap.createScaledBitmap(capturedBitmap,
                            1024, (int) (capturedBitmap.getHeight() * scale), true);
                }

                binding.cardImagePreview.setVisibility(View.VISIBLE);
                binding.ivFoodImage.setImageBitmap(capturedBitmap);
                binding.btnAnalyze.setEnabled(true);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
        }
    }

    private void analyzeFood() {
        if (capturedBitmap == null) {
            Toast.makeText(this, "Please capture or select an image first", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.progressAnalysis.setVisibility(View.VISIBLE);
        binding.btnAnalyze.setEnabled(false);
        binding.cardResults.setVisibility(View.GONE);
        binding.cardRecommendation.setVisibility(View.GONE);

        String goalDisplay = formatGoal(fitnessGoal);

        String prompt = "You are an expert nutritionist specializing in Indian cuisine. " +
                "Analyze this food image and identify ALL food items visible.\n\n" +
                "For each food item, provide:\n" +
                "- Name (in English, and Hindi name if it's an Indian dish)\n" +
                "- Approximate calories per serving\n" +
                "- Macros: Protein (g), Carbs (g), Fat (g)\n" +
                "- Serving size estimate\n\n" +
                "Then provide a TOTAL estimated calories for the entire meal.\n\n" +
                "Finally, based on the user's fitness goal of '" + goalDisplay + "' " +
                "and daily calorie target of " + calorieTarget + " kcal:\n" +
                "- For each item, recommend: ✅ Eat / ❌ Avoid / ⚠️ Reduce portion\n" +
                "- Provide a brief reason for each recommendation\n" +
                "- Suggest any healthier alternatives if applicable\n\n" +
                "Format the response clearly with sections and emojis.";

        GeminiHelper.getInstance().analyzeImage(capturedBitmap, prompt, new GeminiHelper.GeminiCallback() {
            @Override
            public void onSuccess(String response) {
                mainHandler.post(() -> {
                    binding.progressAnalysis.setVisibility(View.GONE);
                    binding.btnAnalyze.setEnabled(true);

                    // Split response into food analysis and recommendation
                    binding.cardResults.setVisibility(View.VISIBLE);
                    binding.tvFoodResults.setText(response);

                    binding.cardRecommendation.setVisibility(View.VISIBLE);
                    binding.tvRecommendation.setText(
                            "Goal: " + formatGoal(fitnessGoal) +
                            " | Target: " + calorieTarget + " kcal/day\n\n" +
                            "Tap items above to add them to your meal diary.");
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    binding.progressAnalysis.setVisibility(View.GONE);
                    binding.btnAnalyze.setEnabled(true);
                    Toast.makeText(FoodImageAnalysisActivity.this,
                            "Analysis failed: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private String formatGoal(String goal) {
        if (goal == null) return "Maintain Weight";
        switch (goal) {
            case "lose_weight": return "Lose Weight";
            case "maintain": return "Maintain Weight";
            case "build_muscle": return "Build Muscle";
            default: return goal;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        }
    }
}
