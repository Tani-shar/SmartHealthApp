package com.smarthealth.posedetection;

import java.util.List;

/**
 * Data class for video analysis output.
 */
public class VideoAnalysisResult {

    private String exercise;
    private int totalReps;
    private int completedReps;
    private int incompleteReps;
    private List<String> mistakes;
    private int formScore; // 0-100

    public VideoAnalysisResult() {}

    public VideoAnalysisResult(String exercise, int totalReps, int completedReps,
                               int incompleteReps, List<String> mistakes, int formScore) {
        this.exercise = exercise;
        this.totalReps = totalReps;
        this.completedReps = completedReps;
        this.incompleteReps = incompleteReps;
        this.mistakes = mistakes;
        this.formScore = formScore;
    }

    public String getExercise() { return exercise; }
    public void setExercise(String e) { this.exercise = e; }
    public int getTotalReps() { return totalReps; }
    public void setTotalReps(int t) { this.totalReps = t; }
    public int getCompletedReps() { return completedReps; }
    public void setCompletedReps(int c) { this.completedReps = c; }
    public int getIncompleteReps() { return incompleteReps; }
    public void setIncompleteReps(int i) { this.incompleteReps = i; }
    public List<String> getMistakes() { return mistakes; }
    public void setMistakes(List<String> m) { this.mistakes = m; }
    public int getFormScore() { return formScore; }
    public void setFormScore(int s) { this.formScore = s; }

    public String toSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Exercise: ").append(exercise).append("\n");
        sb.append("Total Reps: ").append(totalReps).append("\n");
        sb.append("Completed: ").append(completedReps).append("\n");
        sb.append("Incomplete: ").append(incompleteReps).append("\n");
        sb.append("Form Score: ").append(formScore).append("/100\n");
        if (mistakes != null && !mistakes.isEmpty()) {
            sb.append("Mistakes:\n");
            for (String m : mistakes) {
                sb.append("  • ").append(m).append("\n");
            }
        }
        return sb.toString();
    }
}
