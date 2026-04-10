package com.smarthealth.calories;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.smarthealth.utils.GeminiHelper;

/**
 * Checks daily calorie intake vs target and shows alerts
 * with AI-generated food suggestions.
 */
public class CalorieAlertHelper {

    /**
     * Check if user is approaching, exceeding, or under their calorie target.
     * Shows appropriate alert with AI suggestions.
     */
    public static void checkAndAlert(Context context, int consumed, int target, String fitnessGoal) {
        if (target <= 0) return;

        double ratio = (double) consumed / target;
        Handler mainHandler = new Handler(Looper.getMainLooper());

        if (ratio > 1.0) {
            // Over target
            int overBy = consumed - target;
            showAlertWithAiSuggestion(context, mainHandler,
                    "⚠️ Calorie Target Exceeded",
                    "You've consumed " + consumed + " kcal, which is " + overBy + " kcal over your daily target of " + target + " kcal.",
                    "The user has exceeded their daily calorie target by " + overBy + " kcal. " +
                    "Their fitness goal is: " + fitnessGoal + ". " +
                    "Suggest 3 ways to recover:\n" +
                    "1. Light exercise to burn extra calories (with specific activity and duration)\n" +
                    "2. Adjustments for the next meal\n" +
                    "3. An encouraging motivational tip\n" +
                    "Keep response brief and supportive.");

        } else if (ratio > 0.9) {
            // Approaching target (90-100%)
            int remaining = target - consumed;
            showAlertWithAiSuggestion(context, mainHandler,
                    "📊 Approaching Calorie Target",
                    "You've consumed " + consumed + " kcal. Only " + remaining + " kcal remaining for today.",
                    "The user has " + remaining + " kcal remaining for today. " +
                    "Their fitness goal is: " + fitnessGoal + ". " +
                    "Suggest 3 low-calorie snack options that fit within " + remaining + " kcal. " +
                    "Include Indian snack options. Be specific with calories per snack.");

        } else if (ratio < 0.5 && java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) >= 16) {
            // Under 50% by 4 PM
            int remaining = target - consumed;
            showAlertWithAiSuggestion(context, mainHandler,
                    "🍽 You're Under-Eating",
                    "You've only consumed " + consumed + " kcal. Try to eat " + remaining + " more kcal today.",
                    "The user has only consumed " + consumed + " kcal out of " + target + " kcal by evening. " +
                    "Their fitness goal is: " + fitnessGoal + ". " +
                    "Suggest 3 calorie-dense healthy food options to help them reach their target. " +
                    "Include Indian meal options. Mention approximate calories.");
        }
    }

    private static void showAlertWithAiSuggestion(Context context, Handler mainHandler,
                                                    String title, String baseMessage, String aiPrompt) {
        // Show immediate alert
        mainHandler.post(() -> {
            new MaterialAlertDialogBuilder(context)
                    .setTitle(title)
                    .setMessage(baseMessage + "\n\n🤖 Getting AI suggestions...")
                    .setPositiveButton("Got it", null)
                    .show();
        });

        // Get AI suggestions in background
        GeminiHelper.getInstance().generateText(aiPrompt, new GeminiHelper.GeminiCallback() {
            @Override
            public void onSuccess(String response) {
                mainHandler.post(() -> {
                    try {
                        new MaterialAlertDialogBuilder(context)
                                .setTitle("🤖 AI Suggestion")
                                .setMessage(response)
                                .setPositiveButton("Thanks!", null)
                                .show();
                    } catch (Exception ignored) {
                        // Activity may have been destroyed
                    }
                });
            }

            @Override
            public void onError(String error) {
                // Silently fail if AI suggestion doesn't work
            }
        });
    }
}
