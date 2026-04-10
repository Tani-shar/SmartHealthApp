package com.smarthealth.models;

public class BmiLog {
    private String id;
    private double bmi;
    private double weightKg;
    private String category;
    private long timestamp;

    public BmiLog() {}

    public BmiLog(double bmi, double weightKg, String category) {
        this.bmi = bmi;
        this.weightKg = weightKg;
        this.category = category;
        this.timestamp = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public double getBmi() { return bmi; }
    public void setBmi(double bmi) { this.bmi = bmi; }
    public double getWeightKg() { return weightKg; }
    public void setWeightKg(double w) { this.weightKg = w; }
    public String getCategory() { return category; }
    public void setCategory(String c) { this.category = c; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long t) { this.timestamp = t; }
}
