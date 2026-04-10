package com.smarthealth.utils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import com.smarthealth.models.WorkoutExercise;

public class BmiUtils {

    public static double calculateBmi(double weightKg, double heightCm) {
        if (heightCm <= 0 || weightKg <= 0) return 0;
        double heightM = heightCm / 100.0;
        return weightKg / (heightM * heightM);
    }

    public static String getCategory(double bmi) {
        if (bmi < 18.5)       return "Underweight";
        else if (bmi < 25.0)  return "Normal Weight";
        else if (bmi < 30.0)  return "Overweight";
        else if (bmi < 35.0)  return "Obese Class I";
        else if (bmi < 40.0)  return "Obese Class II";
        else                  return "Obese Class III";
    }

    public static String getDailyHealthTip(String category) {
        String[][] tips = {
            // Underweight
            {
                "Focus on nutrient-dense foods and strength training to build healthy muscle mass.",
                "Try adding healthy fats like avocado and nuts to your meals.",
                "Consistency is key. Aim for 3 main meals and 2-3 snacks daily.",
                "Smoothies are a great way to pack in calories and nutrients.",
                "Prioritize protein to help repair and build your muscles after workouts."
            },
            // Normal Weight
            {
                "Great work! Maintain your healthy weight with balanced nutrition and regular exercise.",
                "Stay hydrated! Aim for at least 2 liters of water every day.",
                "Mix up your routine with different types of physical activity.",
                "Ensure you're getting enough quality sleep for optimal recovery.",
                "Practice mindful eating to better understand your body's hunger cues."
            },
            // Overweight
            {
                "Incorporate cardio exercise and reduce processed food intake to move toward a healthy weight.",
                "Walking 30 minutes a day can significantly improve your metabolic health.",
                "Try replacing sugary drinks with water or herbal tea.",
                "Focus on fiber-rich foods like vegetables and whole grains to stay full longer.",
                "Small, sustainable changes are more effective than restrictive diets."
            },
            // Obese
            {
                "Start with low-impact activities like walking. Consult a nutritionist for a calorie plan.",
                "Consistency over intensity. Short daily walks are better than one long weekly session.",
                "Portion control is a powerful tool for managing your daily calorie intake.",
                "Don't skip breakfast; it helps regulate your appetite for the rest of the day.",
                "Celebrate small victories, like taking the stairs instead of the elevator."
            }
        };

        int categoryIndex = 0;
        if (category.equals("Normal Weight")) categoryIndex = 1;
        else if (category.equals("Overweight")) categoryIndex = 2;
        else if (category.contains("Obese")) categoryIndex = 3;

        // Use day of the year to pick a unique tip
        int dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
        String[] categoryTips = tips[categoryIndex];
        return categoryTips[dayOfYear % categoryTips.length];
    }

    // Keeping the old method for backward compatibility if needed, but pointing to the new one
    public static String getHealthTip(String category) {
        return getDailyHealthTip(category);
    }

    public static String getCategoryColor(String category) {
        switch (category) {
            case "Underweight":   return "#FFF176";
            case "Normal Weight": return "#A5D6A7";
            case "Overweight":    return "#FFCC80";
            case "Obese Class I": return "#EF9A9A";
            case "Obese Class II":
            case "Obese Class III": return "#EF5350";
            default:              return "#BDBDBD";
        }
    }

    public static int calculateDailyCalories(double weightKg, double heightCm,
                                              int age, String gender, String activityLevel) {
        double bmr;
        if ("male".equalsIgnoreCase(gender)) {
            bmr = (10 * weightKg) + (6.25 * heightCm) - (5 * age) + 5;
        } else {
            bmr = (10 * weightKg) + (6.25 * heightCm) - (5 * age) - 161;
        }

        double multiplier;
        switch (activityLevel) {
            case "light":      multiplier = 1.375; break;
            case "moderate":   multiplier = 1.55;  break;
            case "active":     multiplier = 1.725; break;
            case "very_active":multiplier = 1.9;   break;
            default:           multiplier = 1.2;   break; // sedentary
        }
        return (int) Math.round(bmr * multiplier);
    }

    public static List<WorkoutExercise> getWorkoutRecommendations(String category) {
        List<WorkoutExercise> list = new ArrayList<>();
        switch (category) {
            case "Underweight":
                list.add(new WorkoutExercise("Push-Ups", "Build chest and tricep strength", "3", "12-15", "—", "strength"));
                list.add(new WorkoutExercise("Dumbbell Squats", "Compound leg and core builder", "3", "12", "—", "strength"));
                list.add(new WorkoutExercise("Pull-Ups / Assisted", "Back and bicep development", "3", "8-10", "—", "strength"));
                list.add(new WorkoutExercise("Plank", "Core stability and posture", "3", "—", "30 sec", "strength"));
                list.add(new WorkoutExercise("Dumbbell Rows", "Upper back muscle building", "3", "10", "—", "strength"));
                break;

            case "Normal Weight":
                list.add(new WorkoutExercise("Running", "Improve cardiovascular health", "—", "—", "30 min", "cardio"));
                list.add(new WorkoutExercise("Bench Press", "Upper body strength maintenance", "3", "10", "—", "strength"));
                list.add(new WorkoutExercise("Deadlift", "Full body compound movement", "3", "8", "—", "strength"));
                list.add(new WorkoutExercise("Yoga Flow", "Flexibility and mindfulness", "—", "—", "20 min", "flexibility"));
                list.add(new WorkoutExercise("Jump Rope", "Cardio and coordination", "3", "—", "3 min", "cardio"));
                break;

            case "Overweight":
                list.add(new WorkoutExercise("Brisk Walking", "Low-impact fat-burning cardio", "—", "—", "45 min", "cardio"));
                list.add(new WorkoutExercise("Cycling", "Joint-friendly cardio", "—", "—", "30 min", "cardio"));
                list.add(new WorkoutExercise("Bodyweight Squats", "Leg strength without weights", "3", "15", "—", "strength"));
                list.add(new WorkoutExercise("Swimming", "Full body low-impact workout", "—", "—", "30 min", "cardio"));
                list.add(new WorkoutExercise("Stretching", "Improve flexibility and recovery", "—", "—", "15 min", "flexibility"));
                break;

            default: // Obese Classes
                list.add(new WorkoutExercise("Walking", "Start at a comfortable pace", "—", "—", "20-30 min", "cardio"));
                list.add(new WorkoutExercise("Chair Exercises", "Safe seated strength moves", "2", "10", "—", "strength"));
                list.add(new WorkoutExercise("Water Aerobics", "Low-impact full body movement", "—", "—", "30 min", "cardio"));
                list.add(new WorkoutExercise("Gentle Stretching", "Improve mobility and blood flow", "—", "—", "15 min", "flexibility"));
                list.add(new WorkoutExercise("Breathing Exercises", "Reduce stress, improve lung capacity", "—", "—", "10 min", "flexibility"));
                break;
        }
        return list;
    }
}
