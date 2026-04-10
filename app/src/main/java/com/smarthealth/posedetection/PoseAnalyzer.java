package com.smarthealth.posedetection;

import android.graphics.PointF;

import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;

/**
 * Core pose analysis engine for real-time exercise tracking.
 * Uses angle calculation and state machines for rep counting.
 */
public class PoseAnalyzer {

    public enum ExerciseType {
        BICEP_CURL,
        SQUAT,
        PUSH_UP
    }

    public enum ExerciseState {
        UP,
        DOWN,
        TRANSITION
    }

    // State tracking
    private ExerciseState currentState = ExerciseState.UP;
    private int repCount = 0;
    private ExerciseType currentExercise = ExerciseType.SQUAT;

    // Thresholds for each exercise
    // Bicep Curl thresholds (elbow angle)
    private static final double CURL_UP_ANGLE = 150.0;
    private static final double CURL_DOWN_ANGLE = 50.0;

    // Squat thresholds (knee angle)
    private static final double SQUAT_UP_ANGLE = 160.0;
    private static final double SQUAT_DOWN_ANGLE = 90.0;

    // Push-up thresholds (elbow angle)
    private static final double PUSHUP_UP_ANGLE = 160.0;
    private static final double PUSHUP_DOWN_ANGLE = 90.0;

    // Frame skipping
    private int frameCounter = 0;
    private static final int FRAME_SKIP_RATE = 3;

    public PoseAnalyzer() {}

    public void setExercise(ExerciseType type) {
        this.currentExercise = type;
        reset();
    }

    public void reset() {
        currentState = ExerciseState.UP;
        repCount = 0;
        frameCounter = 0;
    }

    /**
     * Process a frame. Returns true if this frame was actually analyzed
     * (frame skipping means not every frame is processed).
     */
    public boolean shouldProcessFrame() {
        frameCounter++;
        return (frameCounter % FRAME_SKIP_RATE == 0);
    }

    /**
     * Analyze a pose and update rep count.
     * @return the current rep count after analysis
     */
    public int analyzePose(Pose pose) {
        if (pose == null) return repCount;

        switch (currentExercise) {
            case BICEP_CURL:
                analyzeBicepCurl(pose);
                break;
            case SQUAT:
                analyzeSquat(pose);
                break;
            case PUSH_UP:
                analyzePushUp(pose);
                break;
        }

        return repCount;
    }

    private void analyzeBicepCurl(Pose pose) {
        // Use right arm primarily, fall back to left
        PoseLandmark shoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);
        PoseLandmark elbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW);
        PoseLandmark wrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST);

        if (shoulder == null || elbow == null || wrist == null) {
            // Try left side
            shoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
            elbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW);
            wrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST);
        }

        if (shoulder == null || elbow == null || wrist == null) return;

        double angle = calculateAngle(
                shoulder.getPosition(),
                elbow.getPosition(),
                wrist.getPosition()
        );

        updateState(angle, CURL_UP_ANGLE, CURL_DOWN_ANGLE);
    }

    private void analyzeSquat(Pose pose) {
        // Use right leg primarily
        PoseLandmark hip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP);
        PoseLandmark knee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE);
        PoseLandmark ankle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE);

        if (hip == null || knee == null || ankle == null) {
            // Try left side
            hip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP);
            knee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE);
            ankle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE);
        }

        if (hip == null || knee == null || ankle == null) return;

        double angle = calculateAngle(
                hip.getPosition(),
                knee.getPosition(),
                ankle.getPosition()
        );

        updateState(angle, SQUAT_UP_ANGLE, SQUAT_DOWN_ANGLE);
    }

    private void analyzePushUp(Pose pose) {
        // Use right arm
        PoseLandmark shoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);
        PoseLandmark elbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW);
        PoseLandmark wrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST);

        if (shoulder == null || elbow == null || wrist == null) {
            shoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
            elbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW);
            wrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST);
        }

        if (shoulder == null || elbow == null || wrist == null) return;

        double angle = calculateAngle(
                shoulder.getPosition(),
                elbow.getPosition(),
                wrist.getPosition()
        );

        updateState(angle, PUSHUP_UP_ANGLE, PUSHUP_DOWN_ANGLE);
    }

    /**
     * State machine: UP → DOWN → UP = 1 rep
     */
    private void updateState(double angle, double upThreshold, double downThreshold) {
        switch (currentState) {
            case UP:
                if (angle < downThreshold) {
                    currentState = ExerciseState.DOWN;
                } else if (angle < upThreshold) {
                    currentState = ExerciseState.TRANSITION;
                }
                break;

            case TRANSITION:
                if (angle < downThreshold) {
                    currentState = ExerciseState.DOWN;
                } else if (angle > upThreshold) {
                    currentState = ExerciseState.UP;
                }
                break;

            case DOWN:
                if (angle > upThreshold) {
                    currentState = ExerciseState.UP;
                    repCount++;
                } else if (angle > downThreshold) {
                    currentState = ExerciseState.TRANSITION;
                }
                break;
        }
    }

    /**
     * Calculate angle at point B formed by vectors BA and BC.
     * Uses vector dot product: angle = arccos((BA · BC) / (|BA| * |BC|))
     */
    public static double calculateAngle(PointF a, PointF b, PointF c) {
        double baX = a.x - b.x;
        double baY = a.y - b.y;
        double bcX = c.x - b.x;
        double bcY = c.y - b.y;

        double dotProduct = baX * bcX + baY * bcY;
        double magnitudeBA = Math.sqrt(baX * baX + baY * baY);
        double magnitudeBC = Math.sqrt(bcX * bcX + bcY * bcY);

        if (magnitudeBA == 0 || magnitudeBC == 0) return 0;

        double cosAngle = dotProduct / (magnitudeBA * magnitudeBC);
        // Clamp to [-1, 1] to handle floating point errors
        cosAngle = Math.max(-1.0, Math.min(1.0, cosAngle));

        return Math.toDegrees(Math.acos(cosAngle));
    }

    // Getters
    public int getRepCount() { return repCount; }
    public ExerciseState getCurrentState() { return currentState; }
    public ExerciseType getCurrentExercise() { return currentExercise; }
}
