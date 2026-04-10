package com.smarthealth.injury;

import com.smarthealth.models.WorkoutExercise;
import com.smarthealth.utils.FirebaseHelper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Manages injury-aware exercise modifications.
 * Filters exercises, adjusts form validation rules, and provides
 * injury-aware prompts for Gemini AI suggestions.
 */
public class InjuryModeManager {

    private static InjuryModeManager instance;
    private String currentInjury = "none";

    private InjuryModeManager() {}

    public static synchronized InjuryModeManager getInstance() {
        if (instance == null) {
            instance = new InjuryModeManager();
        }
        return instance;
    }

    public void setInjury(String injury) {
        this.currentInjury = injury != null ? injury : "none";
    }

    public String getCurrentInjury() {
        return currentInjury;
    }

    public boolean hasInjury() {
        return !"none".equals(currentInjury);
    }

    /**
     * Load injury setting from Firestore.
     */
    public void loadFromFirestore() {
        String uid = FirebaseHelper.getInstance().getCurrentUid();
        if (uid == null) return;

        FirebaseHelper.getInstance().usersCollection().document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        String injury = doc.getString("injuryArea");
                        if (injury != null) {
                            currentInjury = injury;
                        }
                    }
                });
    }

    /**
     * Save injury setting to Firestore.
     */
    public void saveToFirestore(String injury) {
        this.currentInjury = injury != null ? injury : "none";
        String uid = FirebaseHelper.getInstance().getCurrentUid();
        if (uid == null) return;

        FirebaseHelper.getInstance().usersCollection().document(uid)
                .update("injuryArea", currentInjury);
    }

    /**
     * Filter exercises based on injury.
     * Removes unsafe exercises and adds safe alternatives.
     */
    public List<WorkoutExercise> filterExercises(List<WorkoutExercise> exercises) {
        if ("none".equals(currentInjury)) return exercises;

        List<WorkoutExercise> filtered = new ArrayList<>(exercises);

        switch (currentInjury) {
            case "knee":
                removeByName(filtered, "Squat", "Jump", "Running", "Lunge");
                addIfNotExists(filtered, new WorkoutExercise(
                        "Seated Leg Extensions", "Strengthen quads without knee stress",
                        "3", "12", "—", "strength"));
                addIfNotExists(filtered, new WorkoutExercise(
                        "Wall Sits (Partial)", "Isometric hold at safe angle",
                        "3", "—", "20 sec", "strength"));
                addIfNotExists(filtered, new WorkoutExercise(
                        "Glute Bridges", "Hip and glute strengthening",
                        "3", "15", "—", "strength"));
                break;

            case "shoulder":
                removeByName(filtered, "Push-Up", "Bench Press", "Overhead", "Pull-Up");
                addIfNotExists(filtered, new WorkoutExercise(
                        "Wall Push-ups", "Low-stress chest work",
                        "3", "12", "—", "strength"));
                addIfNotExists(filtered, new WorkoutExercise(
                        "Resistance Band External Rotation", "Rotator cuff rehab",
                        "3", "15", "—", "strength"));
                addIfNotExists(filtered, new WorkoutExercise(
                        "Isometric Shoulder Holds", "Gentle strengthening",
                        "3", "—", "15 sec", "strength"));
                break;

            case "back":
                removeByName(filtered, "Deadlift", "Row");
                addIfNotExists(filtered, new WorkoutExercise(
                        "Cat-Cow Stretch", "Spine mobility and relief",
                        "—", "—", "2 min", "flexibility"));
                addIfNotExists(filtered, new WorkoutExercise(
                        "Bird-Dog", "Core stability without back strain",
                        "3", "10 each side", "—", "strength"));
                addIfNotExists(filtered, new WorkoutExercise(
                        "Plank (Modified)", "Core stability with neutral spine",
                        "3", "—", "20 sec", "strength"));
                break;
        }

        return filtered;
    }

    /**
     * Get injury-specific prompt additions for Gemini AI suggestions.
     */
    public String getInjuryPromptAddition() {
        switch (currentInjury) {
            case "knee":
                return "\n\nIMPORTANT: The user has a KNEE injury. " +
                       "Avoid recommending: squats, lunges, jumping exercises, running. " +
                       "Favor: swimming, cycling, seated exercises, upper body work.";
            case "shoulder":
                return "\n\nIMPORTANT: The user has a SHOULDER injury. " +
                       "Avoid recommending: push-ups, overhead presses, pull-ups, bench press. " +
                       "Favor: lower body exercises, isometric shoulder rehab, wall push-ups.";
            case "back":
                return "\n\nIMPORTANT: The user has a BACK injury. " +
                       "Avoid recommending: deadlifts, heavy rows, exercises with spinal loading. " +
                       "Favor: core stability exercises, swimming, walking, stretching.";
            default:
                return "";
        }
    }

    private void removeByName(List<WorkoutExercise> list, String... keywords) {
        Iterator<WorkoutExercise> it = list.iterator();
        while (it.hasNext()) {
            WorkoutExercise ex = it.next();
            String name = ex.getName().toLowerCase();
            for (String keyword : keywords) {
                if (name.contains(keyword.toLowerCase())) {
                    it.remove();
                    break;
                }
            }
        }
    }

    private void addIfNotExists(List<WorkoutExercise> list, WorkoutExercise exercise) {
        for (WorkoutExercise ex : list) {
            if (ex.getName().equals(exercise.getName())) return;
        }
        list.add(exercise);
    }
}
