package com.smarthealth.models;

public class MealLog {
    private String id;
    private String foodName;
    private int calories;
    private double protein;   // grams
    private double carbs;     // grams
    private double fat;       // grams
    private String mealType;  // "breakfast"|"lunch"|"dinner"|"snack"
    private String date;      // "yyyy-MM-dd"
    private long timestamp;
    private String photoUrl;  // Firebase Storage URL for meal photo

    public MealLog() {}

    public MealLog(String foodName, int calories, double protein,
                   double carbs, double fat, String mealType, String date) {
        this.foodName = foodName;
        this.calories = calories;
        this.protein = protein;
        this.carbs = carbs;
        this.fat = fat;
        this.mealType = mealType;
        this.date = date;
        this.timestamp = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFoodName() { return foodName; }
    public void setFoodName(String f) { this.foodName = f; }
    public int getCalories() { return calories; }
    public void setCalories(int c) { this.calories = c; }
    public double getProtein() { return protein; }
    public void setProtein(double p) { this.protein = p; }
    public double getCarbs() { return carbs; }
    public void setCarbs(double c) { this.carbs = c; }
    public double getFat() { return fat; }
    public void setFat(double f) { this.fat = f; }
    public String getMealType() { return mealType; }
    public void setMealType(String m) { this.mealType = m; }
    public String getDate() { return date; }
    public void setDate(String d) { this.date = d; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long t) { this.timestamp = t; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String p) { this.photoUrl = p; }
}
