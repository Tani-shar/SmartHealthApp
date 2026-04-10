package com.smarthealth.calories.scan;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.smarthealth.databinding.ActivityFoodScannerBinding;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.*;

public class FoodScannerActivity extends AppCompatActivity {

    public static final String EXTRA_FOOD_NAME = "food_name";
    public static final String EXTRA_CALORIES   = "calories";
    public static final String EXTRA_PROTEIN    = "protein";
    public static final String EXTRA_CARBS      = "carbs";
    public static final String EXTRA_FAT        = "fat";

    private static final int CAMERA_PERMISSION_CODE = 101;
    private static final String TAG = "FoodScanner";

    private ActivityFoodScannerBinding binding;
    private ExecutorService cameraExecutor;
    private BarcodeScanner barcodeScanner;
    private boolean isProcessing = false;
    private OkHttpClient httpClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFoodScannerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Scan Food Barcode");
        }

        httpClient = new OkHttpClient();
        cameraExecutor = Executors.newSingleThreadExecutor();

        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);

        binding.btnManualEntry.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                analysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    if (!isProcessing) {
                        processBarcode(imageProxy);
                    } else {
                        imageProxy.close();
                    }
                });

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                provider.unbindAll();
                provider.bindToLifecycle(this, cameraSelector, preview, analysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera init error", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @androidx.annotation.OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
    private void processBarcode(ImageProxy imageProxy) {
        if (imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        InputImage image = InputImage.fromMediaImage(
                imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());

        barcodeScanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    if (!barcodes.isEmpty() && !isProcessing) {
                        String barcode = barcodes.get(0).getRawValue();
                        if (barcode != null) {
                            isProcessing = true;
                            runOnUiThread(() -> {
                                binding.tvScanStatus.setText("Barcode found: " + barcode + "\nLooking up nutrition...");
                                binding.progressScanning.setVisibility(View.VISIBLE);
                            });
                            lookupBarcode(barcode);
                        }
                    }
                    imageProxy.close();
                })
                .addOnFailureListener(e -> imageProxy.close());
    }

    private void lookupBarcode(String barcode) {
        String url = "https://world.openfoodfacts.org/api/v0/product/" + barcode + ".json";
        Request request = new Request.Builder().url(url).build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                finishWithError("Network error. Try manual entry.");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    String body = response.body() != null ? response.body().string() : "";
                    JSONObject json = new JSONObject(body);
                    if (json.optInt("status", 0) == 1) {
                        JSONObject product = json.getJSONObject("product");
                        JSONObject nutriments = product.optJSONObject("nutriments");

                        String name = product.optString("product_name", "Unknown Product");
                        int calories = 0;
                        double protein = 0, carbs = 0, fat = 0;

                        if (nutriments != null) {
                            calories = (int) nutriments.optDouble("energy-kcal_100g", 0);
                            protein  = nutriments.optDouble("proteins_100g", 0);
                            carbs    = nutriments.optDouble("carbohydrates_100g", 0);
                            fat      = nutriments.optDouble("fat_100g", 0);
                        }

                        final String foodName = name;
                        final int cal = calories;
                        final double pro = protein, car = carbs, fa = fat;

                        runOnUiThread(() -> {
                            Intent result = new Intent();
                            result.putExtra(EXTRA_FOOD_NAME, foodName);
                            result.putExtra(EXTRA_CALORIES,  cal);
                            result.putExtra(EXTRA_PROTEIN,   pro);
                            result.putExtra(EXTRA_CARBS,     car);
                            result.putExtra(EXTRA_FAT,       fa);
                            setResult(RESULT_OK, result);
                            finish();
                        });
                    } else {
                        finishWithError("Barcode not recognized.");
                    }
                } catch (Exception e) {
                    finishWithError("Error parsing barcode data.");
                }
            }
        });
    }

    private void finishWithError(String msg) {
        isProcessing = false;
        runOnUiThread(() -> {
            binding.progressScanning.setVisibility(View.GONE);
            binding.tvScanStatus.setText("Point camera at a food barcode");
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            finish();
        }
    }

    @Override
    public boolean onSupportNavigateUp() { finish(); return true; }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
