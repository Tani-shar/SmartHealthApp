package com.smarthealth.models;

public class User {
    private String uid;
    private String displayName;
    private String email;
    private int age;
    private String gender;        // "male" | "female" | "other"
    private double heightCm;
    private double weightKg;
    private double bmiCurrent;
    private String bmiCategory;
    private String activityLevel; // "sedentary"|"light"|"moderate"|"active"|"very_active"
    private String fitnessGoal;   // "lose_weight"|"maintain"|"build_muscle"
    private int dailyCalorieTarget;
    private boolean darkModeEnabled;
    private boolean isAdmin;
    private long createdAt;
    private String experienceLevel; // "beginner"|"intermediate"|"advanced"
    private String injuryArea;      // "none"|"knee"|"shoulder"|"back"
    private String aiWorkoutPlan;   // JSON string from Gemini
    private String aiMacroPlan;     // JSON string from Gemini

    public User() {} // Required for Firestore deserialization

    public User(String uid, String displayName, String email) {
        this.uid = uid;
        this.displayName = displayName;
        this.email = email;
        this.createdAt = System.currentTimeMillis();
        this.experienceLevel = "beginner";
        this.injuryArea = "none";
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String n) { this.displayName = n; }
    public String getEmail() { return email; }
    public void setEmail(String e) { this.email = e; }
    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }
    public String getGender() { return gender; }
    public void setGender(String g) { this.gender = g; }
    public double getHeightCm() { return heightCm; }
    public void setHeightCm(double h) { this.heightCm = h; }
    public double getWeightKg() { return weightKg; }
    public void setWeightKg(double w) { this.weightKg = w; }
    public double getBmiCurrent() { return bmiCurrent; }
    public void setBmiCurrent(double b) { this.bmiCurrent = b; }
    public String getBmiCategory() { return bmiCategory; }
    public void setBmiCategory(String c) { this.bmiCategory = c; }
    public String getActivityLevel() { return activityLevel; }
    public void setActivityLevel(String a) { this.activityLevel = a; }
    public String getFitnessGoal() { return fitnessGoal; }
    public void setFitnessGoal(String f) { this.fitnessGoal = f; }
    public int getDailyCalorieTarget() { return dailyCalorieTarget; }
    public void setDailyCalorieTarget(int c) { this.dailyCalorieTarget = c; }
    public boolean isDarkModeEnabled() { return darkModeEnabled; }
    public void setDarkModeEnabled(boolean d) { this.darkModeEnabled = d; }
    public boolean isAdmin() { return isAdmin; }
    public void setAdmin(boolean admin) { this.isAdmin = admin; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long t) { this.createdAt = t; }
    public String getExperienceLevel() { return experienceLevel; }
    public void setExperienceLevel(String e) { this.experienceLevel = e; }
    public String getInjuryArea() { return injuryArea; }
    public void setInjuryArea(String i) { this.injuryArea = i; }
    public String getAiWorkoutPlan() { return aiWorkoutPlan; }
    public void setAiWorkoutPlan(String p) { this.aiWorkoutPlan = p; }
    public String getAiMacroPlan() { return aiMacroPlan; }
    public void setAiMacroPlan(String m) { this.aiMacroPlan = m; }
}
