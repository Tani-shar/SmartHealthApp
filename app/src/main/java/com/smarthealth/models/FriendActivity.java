package com.smarthealth.models;

public class FriendActivity {
    private String uid;
    private String displayName;
    private String activityType; // "meal_logged"|"bmi_updated"|"steps_goal"|"workout_done"
    private String description;
    private double bmi;
    private String bmiCategory;
    private int steps;
    private int calories;
    private String photoUrl;
    private long timestamp;

    public FriendActivity() {}

    public String getUid() { return uid; }
    public void setUid(String u) { this.uid = u; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String d) { this.displayName = d; }
    public String getActivityType() { return activityType; }
    public void setActivityType(String a) { this.activityType = a; }
    public String getDescription() { return description; }
    public void setDescription(String d) { this.description = d; }
    public double getBmi() { return bmi; }
    public void setBmi(double b) { this.bmi = b; }
    public String getBmiCategory() { return bmiCategory; }
    public void setBmiCategory(String b) { this.bmiCategory = b; }
    public int getSteps() { return steps; }
    public void setSteps(int s) { this.steps = s; }
    public int getCalories() { return calories; }
    public void setCalories(int c) { this.calories = c; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String p) { this.photoUrl = p; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long t) { this.timestamp = t; }
}
