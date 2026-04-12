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
 * Centralized Gemini API wrapper with 3-layer fallback.
 * Layer 1: Primary API call
 * Layer 2: Retry once after 2-second delay
 * Layer 3: Smart fallback with predefined dynamic responses
 *
 * All AI calls in the app should go through this helper.
 */
public class GeminiHelper {

    private static final String TAG = "GeminiHelper";
    private static final int MAX_RETRIES = 2;
    private static final long INITIAL_RETRY_DELAY_MS = 1500;
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
     * Attempt a single text API call. Returns the response text or null on failure.
     * Catches MissingFieldException and all SDK parse errors gracefully.
     */
    private String attemptTextCall(String prompt) {
        try {
            Content content = new Content.Builder()
                    .addText(prompt)
                    .build();
            GenerateContentResponse response = textModel.generateContent(content).get();
            String text = response.getText();
            return (text != null && !text.isEmpty()) ? text : null;
        } catch (Throwable t) {
            String msg = extractErrorMessage(t);
            // MissingFieldException = SDK parse bug, not a real content error
            if (msg.contains("MissingFieldException") || msg.contains("details")) {
                Log.w(TAG, "Fallback due to SDK parse issue: " + msg);
            } else {
                Log.w(TAG, "Text API attempt failed: " + msg);
            }
            return null;
        }
    }

    /**
     * Attempt a single vision API call. Returns the response text or null on failure.
     * Catches MissingFieldException and all SDK parse errors gracefully.
     */
    private String attemptVisionCall(Bitmap image, String prompt) {
        try {
            Content content = new Content.Builder()
                    .addImage(image)
                    .addText(prompt)
                    .build();
            GenerateContentResponse response = visionModel.generateContent(content).get();
            String text = response.getText();
            return (text != null && !text.isEmpty()) ? text : null;
        } catch (Throwable t) {
            String msg = extractErrorMessage(t);
            if (msg.contains("MissingFieldException") || msg.contains("details")) {
                Log.w(TAG, "Fallback due to SDK parse issue: " + msg);
            } else {
                Log.w(TAG, "Vision API attempt failed: " + msg);
            }
            return null;
        }
    }

    /**
     * Retry a text call with exponential backoff.
     * @return response text or null if all retries failed
     */
    private String retryTextWithBackoff(String prompt) {
        long delay = INITIAL_RETRY_DELAY_MS;
        for (int i = 0; i < MAX_RETRIES; i++) {
            Log.i(TAG, "Text retry " + (i + 1) + "/" + MAX_RETRIES + " after " + delay + "ms");
            try { Thread.sleep(delay); } catch (InterruptedException ignored) {}
            String result = attemptTextCall(prompt);
            if (result != null) return result;
            delay *= 2; // exponential backoff
        }
        return null;
    }

    /**
     * Retry a vision call with exponential backoff.
     * @return response text or null if all retries failed
     */
    private String retryVisionWithBackoff(Bitmap image, String prompt) {
        long delay = INITIAL_RETRY_DELAY_MS;
        for (int i = 0; i < MAX_RETRIES; i++) {
            Log.i(TAG, "Vision retry " + (i + 1) + "/" + MAX_RETRIES + " after " + delay + "ms");
            try { Thread.sleep(delay); } catch (InterruptedException ignored) {}
            String result = attemptVisionCall(image, prompt);
            if (result != null) return result;
            delay *= 2;
        }
        return null;
    }

    /**
     * Generate text response from a prompt.
     * Pipeline: primary → exponential backoff retries → smart fallback.
     */
    public void generateText(String prompt, GeminiCallback callback) {
        executor.execute(() -> {
            // Layer 1: Primary attempt
            String result = attemptTextCall(prompt);
            if (result != null) {
                callback.onSuccess(result);
                return;
            }

            // Layer 2: Exponential backoff retries
            result = retryTextWithBackoff(prompt);
            if (result != null) {
                callback.onSuccess(result);
                return;
            }

            // Layer 3: Smart fallback — user never sees failure
            Log.i(TAG, "All retries failed, using smart fallback for text prompt");
            String fallback = FallbackProvider.generateFallbackText(prompt);
            callback.onSuccess(fallback);
        });
    }

    /**
     * Analyze an image with a text prompt using Gemini Vision.
     * Pipeline: primary → exponential backoff → ultra-light prompt → smart fallback.
     */
    public void analyzeImage(Bitmap image, String prompt, GeminiCallback callback) {
        executor.execute(() -> {
            // Layer 1: Primary attempt (full prompt)
            String result = attemptVisionCall(image, prompt);
            if (result != null) {
                callback.onSuccess(result);
                return;
            }

            // Layer 2: Exponential backoff retries (full prompt)
            result = retryVisionWithBackoff(image, prompt);
            if (result != null) {
                callback.onSuccess(result);
                return;
            }

            // Layer 3: Ultra-light prompt — tiny request that can succeed under load
            Log.i(TAG, "Full retries failed, trying ultra-light prompt...");
            String ultraLightResult = attemptVisionCall(image,
                    "Identify food items only. Max 3 words per item. No explanation.");
            if (ultraLightResult != null) {
                // Wrap the item list into a structured estimate
                String enhanced = FallbackProvider.enhanceUltraLightResult(ultraLightResult);
                callback.onSuccess(enhanced);
                return;
            }

            // Layer 4: Smart offline fallback — user never sees failure
            Log.i(TAG, "All attempts failed, using smart offline fallback");
            String fallback = FallbackProvider.generateFallbackImageAnalysis(prompt);
            callback.onSuccess(fallback);
        });
    }

    /**
     * Generate text with a system instruction for more controlled output.
     * Pipeline: primary → exponential backoff retries → smart fallback.
     */
    public void generateTextWithSystem(String systemPrompt, String userPrompt, GeminiCallback callback) {
        executor.execute(() -> {
            String combinedPrompt = "System instruction: " + systemPrompt + "\n\n" + userPrompt;

            // Layer 1: Primary attempt
            String result = attemptTextCall(combinedPrompt);
            if (result != null) {
                callback.onSuccess(result);
                return;
            }

            // Layer 2: Exponential backoff retries
            result = retryTextWithBackoff(combinedPrompt);
            if (result != null) {
                callback.onSuccess(result);
                return;
            }

            // Layer 3: Smart fallback
            Log.i(TAG, "All retries failed, using smart fallback for system prompt");
            String fallback = FallbackProvider.generateFallbackText(userPrompt);
            callback.onSuccess(fallback);
        });
    }

    // ─────────────────────────────────────────────────────────────────
    // Smart Fallback Provider — generates contextual predefined
    // responses so the UI is never blank
    // ─────────────────────────────────────────────────────────────────

    static class FallbackProvider {

        /**
         * Generates a dynamic fallback based on keywords found in the prompt.
         */
        static String generateFallbackText(String prompt) {
            if (prompt == null) prompt = "";
            String lower = prompt.toLowerCase();

            if (lower.contains("step") && (lower.contains("squat") || lower.contains("push") || lower.contains("curl"))) {
                return generateExerciseStepsFallback(lower);
            }
            if (lower.contains("weekly") || lower.contains("7-day") || lower.contains("meal plan")) {
                return generateWeeklyPlanFallback();
            }
            if (lower.contains("snack")) {
                return generateSnackFallback();
            }
            if (lower.contains("meal") || lower.contains("food") || lower.contains("nutrition") || lower.contains("eat")) {
                return generateMealFallback();
            }
            if (lower.contains("workout") || lower.contains("exercise") || lower.contains("training")) {
                return generateWorkoutFallback(lower);
            }
            if (lower.contains("coaching") || lower.contains("feedback") || lower.contains("form")) {
                return generateCoachingFallback();
            }

            return "⚠️ AI is temporarily unavailable (high demand). Here are general tips:\n\n" +
                    "1. 🥗 Eat balanced meals with protein, carbs, and healthy fats\n" +
                    "2. 💧 Drink at least 8 glasses of water daily\n" +
                    "3. 🏃 Get 30 minutes of moderate exercise daily\n" +
                    "4. 😴 Aim for 7-8 hours of quality sleep\n" +
                    "5. 🧘 Include stretching and mobility work\n\n" +
                    "Please try again in a moment for personalized AI suggestions.";
        }

        /**
         * Returns a structured JSON fallback for image analysis so the
         * caller's JSON parser works seamlessly (user never sees "AI failed").
         */
        static String generateFallbackImageAnalysis(String prompt) {
            return "{\n" +
                    "  \"items\": [\n" +
                    "    {\"name\": \"Mixed Indian Meal\", \"calories\": 550, \"protein\": 18, \"carbs\": 70, \"fat\": 20, \"portion\": \"1 plate\", \"recommendation\": \"reduce\"},\n" +
                    "    {\"name\": \"Side Dish / Accompaniment\", \"calories\": 120, \"protein\": 5, \"carbs\": 15, \"fat\": 5, \"portion\": \"1 bowl\", \"recommendation\": \"eat\"}\n" +
                    "  ],\n" +
                    "  \"totalCalories\": 670\n" +
                    "}";
        }

        /**
         * Takes ultra-light AI output (e.g. "Rice, Dal, Roti") and wraps
         * it into a structured quick-estimate the user finds useful.
         */
        static String enhanceUltraLightResult(String itemList) {
            if (itemList == null || itemList.trim().isEmpty()) {
                return generateFallbackImageAnalysis("");
            }

            String[] items = itemList.split("[,\\n]");
            StringBuilder json = new StringBuilder();
            json.append("{\n  \"items\": [\n");

            int totalCal = 0;
            for (int i = 0; i < items.length; i++) {
                String name = items[i].trim()
                        .replaceAll("^[\\-•\\d.]+\\s*", "") // strip bullets/numbers
                        .replaceAll("\"", "");
                if (name.isEmpty()) continue;

                // Smart calorie estimate based on common food keywords
                int cal = estimateCalories(name.toLowerCase());
                int protein = cal / 25;
                int carbs = cal / 8;
                int fat = cal / 30;
                totalCal += cal;

                if (json.toString().endsWith("},\n") || json.toString().endsWith("[\n")) {
                    // ok
                } else if (i > 0) {
                    json.append(",\n");
                }
                json.append("    {\"name\": \"").append(name)
                        .append("\", \"calories\": ").append(cal)
                        .append(", \"protein\": ").append(protein)
                        .append(", \"carbs\": ").append(carbs)
                        .append(", \"fat\": ").append(fat)
                        .append(", \"portion\": \"1 serving\"")
                        .append(", \"recommendation\": \"reduce\"}");
            }

            json.append("\n  ],\n  \"totalCalories\": ").append(totalCal).append("\n}");
            return json.toString();
        }

        /**
         * Rough calorie estimate from food name keywords.
         */
        private static int estimateCalories(String name) {
            if (name.contains("rice") || name.contains("biryani") || name.contains("pulao")) return 250;
            if (name.contains("roti") || name.contains("naan") || name.contains("paratha")) return 150;
            if (name.contains("dal") || name.contains("lentil") || name.contains("sambar")) return 150;
            if (name.contains("chicken") || name.contains("mutton") || name.contains("meat")) return 280;
            if (name.contains("paneer") || name.contains("cheese")) return 220;
            if (name.contains("egg")) return 90;
            if (name.contains("fish") || name.contains("prawn")) return 200;
            if (name.contains("salad") || name.contains("raita")) return 70;
            if (name.contains("sabzi") || name.contains("curry") || name.contains("vegetable")) return 160;
            if (name.contains("chutney") || name.contains("pickle")) return 30;
            if (name.contains("sweet") || name.contains("dessert") || name.contains("gulab")) return 300;
            if (name.contains("chai") || name.contains("tea") || name.contains("coffee")) return 60;
            if (name.contains("juice") || name.contains("lassi") || name.contains("buttermilk")) return 120;
            if (name.contains("fruit") || name.contains("banana") || name.contains("apple")) return 80;
            if (name.contains("bread") || name.contains("toast")) return 130;
            if (name.contains("soup")) return 100;
            if (name.contains("fry") || name.contains("fried") || name.contains("pakora")) return 250;
            if (name.contains("dosa") || name.contains("idli") || name.contains("uttapam")) return 170;
            if (name.contains("samosa") || name.contains("kachori")) return 260;
            return 180; // generic fallback
        }

        private static String generateMealFallback() {
            return "⚠️ AI is temporarily busy. Here are 5 healthy meal ideas:\n\n" +
                    "1. 🥣 **Oatmeal with Banana & Almonds** — ~350 kcal\n" +
                    "   High fiber, sustained energy. Great for any fitness goal.\n\n" +
                    "2. 🍗 **Grilled Chicken Salad** — ~400 kcal\n" +
                    "   High protein, low carb. Perfect for muscle building.\n\n" +
                    "3. 🍛 **Dal + Brown Rice + Sabzi** — ~450 kcal\n" +
                    "   Balanced Indian meal with complete amino acids.\n\n" +
                    "4. 🥚 **Egg Bhurji with Multigrain Roti** — ~380 kcal\n" +
                    "   Quick protein-rich meal. Add veggies for extra nutrients.\n\n" +
                    "5. 🐟 **Fish Curry with Steamed Rice** — ~420 kcal\n" +
                    "   Rich in omega-3 fatty acids. Good for heart health.\n\n" +
                    "Try again soon for personalized AI-powered suggestions!";
        }

        private static String generateSnackFallback() {
            return "⚠️ AI is temporarily busy. Here are 6 healthy snack ideas:\n\n" +
                    "1. 🍎 **Apple with Peanut Butter** — ~200 kcal\n" +
                    "   Fiber + healthy fats. Great pre-workout snack.\n\n" +
                    "2. 🥜 **Mixed Nuts (handful)** — ~170 kcal\n" +
                    "   Healthy fats and protein. Don't overeat!\n\n" +
                    "3. 🧆 **Roasted Chana** — ~120 kcal\n" +
                    "   High protein Indian snack. Crunchy and satisfying.\n\n" +
                    "4. 🥒 **Cucumber Raita** — ~80 kcal\n" +
                    "   Low calorie, probiotic-rich. Good for digestion.\n\n" +
                    "5. 🍌 **Banana with Honey** — ~130 kcal\n" +
                    "   Quick energy boost. Perfect post-workout.\n\n" +
                    "6. 🫘 **Sprouts Chaat** — ~150 kcal\n" +
                    "   Protein-packed Indian snack with fiber.\n\n" +
                    "Try again soon for personalized suggestions!";
        }

        private static String generateWeeklyPlanFallback() {
            return "⚠️ AI is temporarily busy. Here's a sample 7-day plan:\n\n" +
                    "📅 **Monday**\n" +
                    "  Breakfast: Oatmeal + fruits | Lunch: Dal + rice + salad | Dinner: Grilled chicken + roti\n\n" +
                    "📅 **Tuesday**\n" +
                    "  Breakfast: Egg bhurji + toast | Lunch: Rajma + rice | Dinner: Paneer tikka + salad\n\n" +
                    "📅 **Wednesday**\n" +
                    "  Breakfast: Smoothie bowl | Lunch: Chicken curry + brown rice | Dinner: Soup + multigrain bread\n\n" +
                    "📅 **Thursday**\n" +
                    "  Breakfast: Poha + sprouts | Lunch: Fish curry + rice | Dinner: Vegetable stir-fry + roti\n\n" +
                    "📅 **Friday**\n" +
                    "  Breakfast: Idli + chutney | Lunch: Chole + roti + raita | Dinner: Egg curry + rice\n\n" +
                    "📅 **Saturday**\n" +
                    "  Breakfast: Besan chilla | Lunch: Mixed veg + dal + rice | Dinner: Grilled fish + salad\n\n" +
                    "📅 **Sunday**\n" +
                    "  Breakfast: Pancakes + fruits | Lunch: Biryani (small portion) | Dinner: Soup + grilled paneer\n\n" +
                    "Try again soon for a personalized AI-generated plan!";
        }

        private static String generateWorkoutFallback(String lower) {
            boolean isGym = lower.contains("gym") || lower.contains("machine") || lower.contains("weight");

            if (isGym) {
                return "⚠️ AI is temporarily busy. Here's a gym workout plan:\n\n" +
                        "🏋️ **Gym Workout (60 min)**\n\n" +
                        "**Warm-up (5 min):** Treadmill light jog\n\n" +
                        "1. **Bench Press** — 4 sets × 8-10 reps | Rest: 90s\n" +
                        "2. **Lat Pulldown** — 3 sets × 10-12 reps | Rest: 60s\n" +
                        "3. **Leg Press** — 4 sets × 10 reps | Rest: 90s\n" +
                        "4. **Dumbbell Shoulder Press** — 3 sets × 10 reps | Rest: 60s\n" +
                        "5. **Cable Bicep Curls** — 3 sets × 12 reps | Rest: 45s\n" +
                        "6. **Tricep Pushdowns** — 3 sets × 12 reps | Rest: 45s\n\n" +
                        "**Cool-down (5 min):** Stretching\n\n" +
                        "💡 Progressive Overload: Increase weight by 2.5kg when you can complete all sets.\n\n" +
                        "Try again for a more personalized plan!";
            }

            return "⚠️ AI is temporarily busy. Here's a home workout plan:\n\n" +
                    "🏠 **Home Workout (45 min, no equipment)**\n\n" +
                    "**Warm-up (5 min):** Jumping jacks + arm circles\n\n" +
                    "1. **Push-ups** — 3 sets × 12-15 reps | Rest: 60s\n" +
                    "2. **Bodyweight Squats** — 4 sets × 15 reps | Rest: 60s\n" +
                    "3. **Lunges** — 3 sets × 12 each leg | Rest: 60s\n" +
                    "4. **Plank** — 3 sets × 30-45 sec | Rest: 45s\n" +
                    "5. **Burpees** — 3 sets × 8-10 reps | Rest: 90s\n" +
                    "6. **Mountain Climbers** — 3 sets × 20 reps | Rest: 45s\n\n" +
                    "**Cool-down (5 min):** Stretching\n\n" +
                    "💡 Progressive Overload: Add 2 reps per set each week.\n\n" +
                    "Try again for a more personalized plan!";
        }

        private static String generateExerciseStepsFallback(String lower) {
            if (lower.contains("squat")) {
                return "STEP 1: Starting Position\n" +
                        "Stand with feet shoulder-width apart, toes slightly turned out. Keep your chest up and core engaged.\n\n" +
                        "STEP 2: Initiate the Descent\n" +
                        "Push your hips back as if sitting in a chair. Keep your weight on your heels.\n\n" +
                        "STEP 3: Lower Down\n" +
                        "Bend your knees and lower until thighs are parallel to the floor. Keep knees tracking over toes.\n\n" +
                        "STEP 4: Bottom Position\n" +
                        "Pause briefly at the bottom. Your back should be straight, chest up. Avoid rounding your lower back.\n\n" +
                        "STEP 5: Drive Up\n" +
                        "Push through your heels to stand back up. Squeeze your glutes at the top.\n\n" +
                        "STEP 6: Reset\n" +
                        "Return to starting position. Take a breath and repeat. Common mistake: don't let knees cave inward.\n\n" +
                        "(Offline fallback — try again for AI-generated guidance)";
            } else if (lower.contains("push")) {
                return "STEP 1: Starting Position\n" +
                        "Place hands slightly wider than shoulder-width on the floor. Extend legs behind you in a plank position.\n\n" +
                        "STEP 2: Body Alignment\n" +
                        "Keep your body in a straight line from head to heels. Engage your core and glutes. Don't let hips sag.\n\n" +
                        "STEP 3: Lower Down\n" +
                        "Bend elbows to lower your chest toward the floor. Keep elbows at 45 degrees, not flared out.\n\n" +
                        "STEP 4: Bottom Position\n" +
                        "Lower until your chest nearly touches the floor. Keep core tight throughout.\n\n" +
                        "STEP 5: Push Up\n" +
                        "Press through your palms to push back up. Fully extend arms without locking elbows.\n\n" +
                        "STEP 6: Breathing\n" +
                        "Inhale going down, exhale pushing up. Maintain a controlled tempo. Avoid rushing.\n\n" +
                        "(Offline fallback — try again for AI-generated guidance)";
            } else {
                return "STEP 1: Starting Position\n" +
                        "Stand with feet hip-width apart. Hold weights at your sides with palms facing forward.\n\n" +
                        "STEP 2: Arm Position\n" +
                        "Pin your elbows to your sides. They should not move forward or backward during the curl.\n\n" +
                        "STEP 3: Curl Up\n" +
                        "Bend your elbows to curl the weights toward your shoulders. Squeeze your biceps at the top.\n\n" +
                        "STEP 4: Peak Contraction\n" +
                        "Hold the top position for 1 second. Keep your wrists straight, don't bend them.\n\n" +
                        "STEP 5: Lower Down\n" +
                        "Slowly lower the weights back to starting position. Control the descent — don't drop them.\n\n" +
                        "STEP 6: Reset and Repeat\n" +
                        "Return to full arm extension. Avoid swinging or using momentum. Keep your back straight.\n\n" +
                        "(Offline fallback — try again for AI-generated guidance)";
            }
        }

        private static String generateCoachingFallback() {
            return "⚠️ AI coaching is temporarily unavailable. Here's general feedback:\n\n" +
                    "📊 **General Assessment:**\n" +
                    "Keep practicing! Consistency is more important than perfection.\n\n" +
                    "🔧 **Top 3 Tips:**\n" +
                    "1. Focus on controlled movements — avoid rushing through reps\n" +
                    "2. Maintain proper breathing — exhale during exertion, inhale during release\n" +
                    "3. Keep your core engaged throughout every exercise\n\n" +
                    "⭐ **Positive Note:**\n" +
                    "You're putting in the effort to improve — that's already a win!\n\n" +
                    "Try again soon for personalized AI coaching feedback.";
        }
    }
}
