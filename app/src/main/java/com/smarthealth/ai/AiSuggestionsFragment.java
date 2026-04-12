package com.smarthealth.ai;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.smarthealth.databinding.FragmentAiSuggestionsBinding;
import com.smarthealth.injury.InjuryModeManager;
import com.smarthealth.utils.FirebaseHelper;
import com.smarthealth.utils.GeminiHelper;

/**
 * AI-powered meal suggestions using Gemini API.
 * Migrated from Groq/Llama to Gemini for consistency with the rest of the app.
 */
public class AiSuggestionsFragment extends Fragment {

    private FragmentAiSuggestionsBinding binding;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String userName = "";
    private double bmi = 0;
    private String bmiCategory = "";
    private String fitnessGoal = "";
    private int calorieTarget = 2000;
    private int age = 0;
    private String gender = "";
    private String activityLevel = "";
    private String workoutLocation = "home";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAiSuggestionsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        loadUserProfile();

        binding.btnGetSuggestions.setOnClickListener(v -> generateSuggestions("meals"));
        binding.btnGetSnacks.setOnClickListener(v -> generateSuggestions("snacks"));
        binding.btnGetMealPlan.setOnClickListener(v -> generateSuggestions("weekly_plan"));
    }

    private void loadUserProfile() {
        String uid = FirebaseHelper.getInstance().getCurrentUid();
        if (uid == null) return;

        FirebaseHelper.getInstance().usersCollection().document(uid).get()
            .addOnSuccessListener(doc -> {
                if (!isAdded() || doc == null) return;
                userName      = doc.getString("displayName") != null ? doc.getString("displayName") : "";
                bmiCategory   = doc.getString("bmiCategory") != null ? doc.getString("bmiCategory") : "";
                fitnessGoal   = doc.getString("fitnessGoal") != null ? doc.getString("fitnessGoal") : "";
                gender        = doc.getString("gender") != null ? doc.getString("gender") : "";
                activityLevel = doc.getString("activityLevel") != null ? doc.getString("activityLevel") : "";
                Double b  = doc.getDouble("bmiCurrent");
                if (b != null) bmi = b;
                Long cal  = doc.getLong("dailyCalorieTarget");
                if (cal != null) calorieTarget = cal.intValue();
                Long a    = doc.getLong("age");
                if (a != null) age = a.intValue();
                String loc = doc.getString("workoutLocation");
                if (loc != null) workoutLocation = loc;

                binding.tvProfileSummary.setText(
                    "Profile: " + (bmi > 0 ? String.format("BMI %.1f (%s)", bmi, bmiCategory) : "BMI not set") +
                    " | Goal: " + formatGoal(fitnessGoal) +
                    " | Target: " + calorieTarget + " kcal/day");
            });
    }

    private String formatGoal(String goal) {
        if (goal == null) return "Not set";
        switch (goal) {
            case "lose_weight":  return "Lose Weight";
            case "maintain":     return "Maintain";
            case "build_muscle": return "Build Muscle";
            default: return goal;
        }
    }

    /**
     * Generates AI suggestions using Gemini (migrated from Groq/Llama).
     */
    private void generateSuggestions(String type) {
        setLoading(true);
        binding.tvSuggestions.setText("");

        String prompt = buildPrompt(type);

        // Add injury context if applicable
        String injuryContext = InjuryModeManager.getInstance().getInjuryPromptAddition();
        if (!injuryContext.isEmpty()) {
            prompt += injuryContext;
        }

        GeminiHelper.getInstance().generateText(prompt, new GeminiHelper.GeminiCallback() {
            @Override
            public void onSuccess(String response) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    setLoading(false);
                    binding.tvSuggestions.setText(response);
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    setLoading(false);
                    showError("AI Error: " + error);
                });
            }
        });
    }

    private String buildPrompt(String type) {
        String profile = String.format(
            "User profile:\n" +
            "- Name: %s\n" +
            "- Age: %d\n" +
            "- Gender: %s\n" +
            "- BMI: %.1f (%s)\n" +
            "- Fitness goal: %s\n" +
            "- Daily calorie target: %d kcal\n" +
            "- Activity level: %s\n\n",
            userName, age, gender, bmi, bmiCategory,
            formatGoal(fitnessGoal), calorieTarget, activityLevel);

        String locationContext = "gym".equalsIgnoreCase(workoutLocation)
                ? "The user trains at a GYM with full equipment."
                : "The user works out at HOME with minimal equipment.";

        switch (type) {
            case "meals":
                return profile + locationContext + "\n\n" +
                    "Based on this health profile, suggest 5 healthy meal ideas for today. " +
                    "Include Indian food options where appropriate. " +
                    "For each meal include: meal name, approximate calories, key ingredients, " +
                    "and why it suits this person's goal. Keep it practical and easy to prepare. " +
                    "Format each meal clearly with a number and emoji.";
            case "snacks":
                return profile +
                    "Suggest 6 healthy snack options that fit this person's calorie target and fitness goal. " +
                    "Include at least 2 Indian snack options. " +
                    "For each snack include: name, calories, and a brief reason why it's good for them. " +
                    "Format with numbers and emojis.";
            case "weekly_plan":
                return profile +
                    "Create a 7-day healthy meal plan (breakfast, lunch, dinner) for this person. " +
                    "Keep total daily calories near their target of " + calorieTarget + " kcal. " +
                    "Align meals with their goal of " + formatGoal(fitnessGoal) + ". " +
                    "Include a mix of Indian and international options. " +
                    "Be concise — one line per meal. Format as a clear day-by-day plan.";
            default:
                return profile + "Give 5 healthy meal suggestions.";
        }
    }

    private void setLoading(boolean loading) {
        if (!isAdded()) return;
        binding.progressAi.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnGetSuggestions.setEnabled(!loading);
        binding.btnGetSnacks.setEnabled(!loading);
        binding.btnGetMealPlan.setEnabled(!loading);
        if (loading) binding.tvSuggestions.setText("Generating personalised suggestions...");
    }

    private void showError(String msg) {
        if (!isAdded()) return;
        binding.tvSuggestions.setText("⚠️ " + msg);
        Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
