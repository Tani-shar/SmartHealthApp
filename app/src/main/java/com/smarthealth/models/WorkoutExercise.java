package com.smarthealth.models;

public class WorkoutExercise {
    private String name;
    private String description;
    private String sets;
    private String reps;
    private String duration;  // e.g. "30 min"
    private String category;  // "cardio"|"strength"|"flexibility"
    private String imageRes;  // drawable resource name

    public WorkoutExercise() {}

    public WorkoutExercise(String name, String description, String sets,
                           String reps, String duration, String category) {
        this.name = name;
        this.description = description;
        this.sets = sets;
        this.reps = reps;
        this.duration = duration;
        this.category = category;
    }

    public String getName() { return name; }
    public void setName(String n) { this.name = n; }
    public String getDescription() { return description; }
    public void setDescription(String d) { this.description = d; }
    public String getSets() { return sets; }
    public void setSets(String s) { this.sets = s; }
    public String getReps() { return reps; }
    public void setReps(String r) { this.reps = r; }
    public String getDuration() { return duration; }
    public void setDuration(String d) { this.duration = d; }
    public String getCategory() { return category; }
    public void setCategory(String c) { this.category = c; }
    public String getImageRes() { return imageRes; }
    public void setImageRes(String i) { this.imageRes = i; }
}
