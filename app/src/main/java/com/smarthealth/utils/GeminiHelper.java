package com.smarthealth.utils;

import android.graphics.Bitmap;
import android.util.Log;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.smarthealth.BuildConfig;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Centralized Gemini API wrapper.
 * All AI calls in the app should go through this helper.
 */
public class GeminiHelper {

    private static final String TAG = "GeminiHelper";
    private static GeminiHelper instance;

    private final GenerativeModelFutures textModel;
    private final GenerativeModelFutures visionModel;
    private final ExecutorService executor;

    public interface GeminiCallback {
        void onSuccess(String response);
        void onError(String error);
    }

    private GeminiHelper() {
        String apiKey = BuildConfig.GEMINI_API_KEY;

        // Text-only model
        GenerativeModel textGenModel = new GenerativeModel(
                "gemini-2.5-flash",
                apiKey
        );
        textModel = GenerativeModelFutures.from(textGenModel);

        // Vision model (same model supports both text and image)
        GenerativeModel visionGenModel = new GenerativeModel(
                "gemini-2.5-flash",
                apiKey
        );
        visionModel = GenerativeModelFutures.from(visionGenModel);

        executor = Executors.newFixedThreadPool(3);
    }

    public static synchronized GeminiHelper getInstance() {
        if (instance == null) {
            instance = new GeminiHelper();
        }
        return instance;
    }

    /**
     * Extract the real error message from potentially nested exceptions.
     * The Gemini SDK wraps errors in ExecutionException, and sometimes
     * throws Kotlin MissingFieldException on malformed error responses.
     */
    private String extractErrorMessage(Throwable t) {
        Throwable cause = t;
        // Unwrap ExecutionException / other wrappers to get root cause
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String msg = cause.getMessage();
        if (msg == null || msg.isEmpty()) {
            msg = cause.getClass().getSimpleName();
        }
        return msg;
    }

    /**
     * Generate text response from a prompt.
     */
    public void generateText(String prompt, GeminiCallback callback) {
        executor.execute(() -> {
            try {
                Content content = new Content.Builder()
                        .addText(prompt)
                        .build();

                GenerateContentResponse response = textModel.generateContent(content).get();
                String text = response.getText();

                if (text != null && !text.isEmpty()) {
                    callback.onSuccess(text);
                } else {
                    callback.onError("Empty response from Gemini");
                }
            } catch (Throwable t) {
                Log.e(TAG, "Text generation error", t);
                String errorMsg = extractErrorMessage(t);
                // Check for common Kotlin serialization bug in the SDK
                if (errorMsg.contains("MissingFieldException") || errorMsg.contains("details")) {
                    callback.onError("Gemini SDK error — please try again");
                } else {
                    callback.onError("AI error: " + errorMsg);
                }
            }
        });
    }

    /**
     * Analyze an image with a text prompt using Gemini Vision.
     */
    public void analyzeImage(Bitmap image, String prompt, GeminiCallback callback) {
        executor.execute(() -> {
            try {
                Content content = new Content.Builder()
                        .addImage(image)
                        .addText(prompt)
                        .build();

                GenerateContentResponse response = visionModel.generateContent(content).get();
                String text = response.getText();

                if (text != null && !text.isEmpty()) {
                    callback.onSuccess(text);
                } else {
                    callback.onError("Empty response from Gemini Vision");
                }
            } catch (Throwable t) {
                Log.e(TAG, "Image analysis error", t);
                String errorMsg = extractErrorMessage(t);
                if (errorMsg.contains("MissingFieldException") || errorMsg.contains("details")) {
                    callback.onError("Gemini SDK error — please try again");
                } else {
                    callback.onError("AI Vision error: " + errorMsg);
                }
            }
        });
    }

    /**
     * Generate text with a system instruction for more controlled output.
     */
    public void generateTextWithSystem(String systemPrompt, String userPrompt, GeminiCallback callback) {
        executor.execute(() -> {
            try {
                String combinedPrompt = "System instruction: " + systemPrompt + "\n\n" + userPrompt;

                Content content = new Content.Builder()
                        .addText(combinedPrompt)
                        .build();

                GenerateContentResponse response = textModel.generateContent(content).get();
                String text = response.getText();

                if (text != null && !text.isEmpty()) {
                    callback.onSuccess(text);
                } else {
                    callback.onError("Empty response from Gemini");
                }
            } catch (Throwable t) {
                Log.e(TAG, "Text generation error", t);
                String errorMsg = extractErrorMessage(t);
                if (errorMsg.contains("MissingFieldException") || errorMsg.contains("details")) {
                    callback.onError("Gemini SDK error — please try again");
                } else {
                    callback.onError("AI error: " + errorMsg);
                }
            }
        });
    }
}
