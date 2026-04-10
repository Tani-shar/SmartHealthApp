package com.smarthealth.social;

import com.smarthealth.models.FriendActivity;
import com.smarthealth.utils.FirebaseHelper;

public class ActivityFeedHelper {

    public static void postMealLogged(String uid, String userName,
                                       String foodName, int calories, String photoUrl) {
        FriendActivity act = new FriendActivity();
        act.setUid(uid);
        act.setDisplayName(userName);
        act.setActivityType("meal_logged");
        act.setDescription(userName + " logged " + foodName + " (" + calories + " kcal)");
        act.setCalories(calories);
        act.setPhotoUrl(photoUrl);
        act.setTimestamp(System.currentTimeMillis());
        post(uid, act);
    }

    public static void postBmiUpdated(String uid, String userName,
                                       double bmi, String category) {
        FriendActivity act = new FriendActivity();
        act.setUid(uid);
        act.setDisplayName(userName);
        act.setActivityType("bmi_updated");
        act.setDescription(String.format(userName + " updated their BMI: %.1f (%s)", bmi, category));
        act.setBmi(bmi);
        act.setBmiCategory(category);
        act.setTimestamp(System.currentTimeMillis());
        post(uid, act);
    }

    public static void postStepsGoalReached(String uid, String userName, int steps) {
        FriendActivity act = new FriendActivity();
        act.setUid(uid);
        act.setDisplayName(userName);
        act.setActivityType("steps_goal");
        act.setDescription(userName + " reached their step goal with " + steps + " steps! 🎉");
        act.setSteps(steps);
        act.setTimestamp(System.currentTimeMillis());
        post(uid, act);
    }

    public static void postWorkoutDone(String uid, String userName, String workoutType) {
        FriendActivity act = new FriendActivity();
        act.setUid(uid);
        act.setDisplayName(userName);
        act.setActivityType("workout_done");
        act.setDescription(userName + " completed a " + workoutType + " workout! 💪");
        act.setTimestamp(System.currentTimeMillis());
        post(uid, act);
    }

    private static void post(String uid, FriendActivity activity) {
        FirebaseHelper.getInstance().activityFeedCollection(uid)
            .add(activity);
    }
}
