package com.smarthealth.posedetection;

import android.graphics.PointF;

import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;

/**
 * Rule-based form validation for exercises.
 * Returns feedback messages and correctness status.
 */
public class FormValidator {

    public static class FormFeedback {
        public final boolean isCorrect;
        public final String message;
        public final int severity; // 0 = good, 1 = warning, 2 = error

        public FormFeedback(boolean isCorrect, String message, int severity) {
            this.isCorrect = isCorrect;
            this.message = message;
            this.severity = severity;
        }
    }

    private String injuryArea = "none";

    public FormValidator() {}

    public void setInjuryArea(String injuryArea) {
        this.injuryArea = injuryArea != null ? injuryArea : "none";
    }

    /**
     * Validate form for the given exercise and pose.
     */
    public FormFeedback validateForm(Pose pose, PoseAnalyzer.ExerciseType exerciseType) {
        if (pose == null) {
            return new FormFeedback(true, "Position yourself in frame", 0);
        }

        switch (exerciseType) {
            case SQUAT:
                return validateSquat(pose);
            case PUSH_UP:
                return validatePushUp(pose);
            case BICEP_CURL:
                return validateBicepCurl(pose);
            default:
                return new FormFeedback(true, "Good form!", 0);
        }
    }

    private FormFeedback validateSquat(Pose pose) {
        PoseLandmark rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP);
        PoseLandmark rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE);
        PoseLandmark rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE);
        PoseLandmark rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);

        // Fall back to left side
        PoseLandmark hip = rightHip != null ? rightHip : pose.getPoseLandmark(PoseLandmark.LEFT_HIP);
        PoseLandmark knee = rightKnee != null ? rightKnee : pose.getPoseLandmark(PoseLandmark.LEFT_KNEE);
        PoseLandmark ankle = rightAnkle != null ? rightAnkle : pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE);
        PoseLandmark shoulder = rightShoulder != null ? rightShoulder : pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);

        if (hip == null || knee == null || ankle == null || shoulder == null) {
            return new FormFeedback(true, "Move fully into frame", 0);
        }

        // Check knee angle
        double kneeAngle = PoseAnalyzer.calculateAngle(
                hip.getPosition(), knee.getPosition(), ankle.getPosition());

        // Check back tilt (shoulder-hip angle relative to vertical)
        double backTilt = calculateBackTilt(shoulder.getPosition(), hip.getPosition());

        // Injury-aware validation
        if ("knee".equals(injuryArea)) {
            // Stricter limits for knee injury
            if (kneeAngle < 100) {
                return new FormFeedback(false, "Don't go too deep — protect your knees", 2);
            }
        }

        // Back tilt check
        if (backTilt > 30) {
            return new FormFeedback(false, "Keep back straight", 2);
        }

        // Knee caving check
        PoseLandmark leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE);
        PoseLandmark leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE);
        PoseLandmark rightKneeL = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE);
        PoseLandmark rightAnkleL = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE);

        if (leftKnee != null && leftAnkle != null && rightKneeL != null && rightAnkleL != null) {
            float leftKneeX = leftKnee.getPosition().x;
            float leftAnkleX = leftAnkle.getPosition().x;
            float rightKneeX = rightKneeL.getPosition().x;
            float rightAnkleX = rightAnkleL.getPosition().x;

            // If knees are caving inward (closer together than ankles)
            float kneeDist = Math.abs(rightKneeX - leftKneeX);
            float ankleDist = Math.abs(rightAnkleX - leftAnkleX);
            if (kneeDist < ankleDist * 0.7f && kneeAngle < 130) {
                return new FormFeedback(false, "Push knees out", 1);
            }
        }

        // Depth check (only when in downward motion)
        if (kneeAngle > 120 && kneeAngle < 150) {
            return new FormFeedback(true, "Go lower", 1);
        }

        return new FormFeedback(true, "Good form! 💪", 0);
    }

    private FormFeedback validatePushUp(Pose pose) {
        PoseLandmark shoulder = getPreferredLandmark(pose, PoseLandmark.RIGHT_SHOULDER, PoseLandmark.LEFT_SHOULDER);
        PoseLandmark elbow = getPreferredLandmark(pose, PoseLandmark.RIGHT_ELBOW, PoseLandmark.LEFT_ELBOW);
        PoseLandmark wrist = getPreferredLandmark(pose, PoseLandmark.RIGHT_WRIST, PoseLandmark.LEFT_WRIST);
        PoseLandmark hip = getPreferredLandmark(pose, PoseLandmark.RIGHT_HIP, PoseLandmark.LEFT_HIP);
        PoseLandmark ankle = getPreferredLandmark(pose, PoseLandmark.RIGHT_ANKLE, PoseLandmark.LEFT_ANKLE);

        if (shoulder == null || elbow == null || wrist == null || hip == null || ankle == null) {
            return new FormFeedback(true, "Move fully into frame", 0);
        }

        // Shoulder injury check
        if ("shoulder".equals(injuryArea)) {
            double elbowAngle = PoseAnalyzer.calculateAngle(
                    shoulder.getPosition(), elbow.getPosition(), wrist.getPosition());
            if (elbowAngle < 80) {
                return new FormFeedback(false, "Don't go too deep — protect your shoulder", 2);
            }
        }

        // Check hip sagging: hip should be roughly on the line between shoulder and ankle
        double hipSag = calculateHipSag(shoulder.getPosition(), hip.getPosition(), ankle.getPosition());
        if (hipSag > 25) {
            return new FormFeedback(false, "Hip sagging — raise your core", 2);
        }
        if (hipSag < -20) {
            return new FormFeedback(false, "Hips too high — align your body", 1);
        }

        // Check elbow flare
        PoseLandmark leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
        PoseLandmark rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);
        PoseLandmark leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW);
        PoseLandmark rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW);

        if (leftShoulder != null && rightShoulder != null && leftElbow != null && rightElbow != null) {
            float shoulderWidth = Math.abs(rightShoulder.getPosition().x - leftShoulder.getPosition().x);
            float elbowWidth = Math.abs(rightElbow.getPosition().x - leftElbow.getPosition().x);

            if (elbowWidth > shoulderWidth * 1.6f) {
                return new FormFeedback(false, "Keep elbows closer to body", 1);
            }
        }

        return new FormFeedback(true, "Good form! 💪", 0);
    }

    private FormFeedback validateBicepCurl(Pose pose) {
        PoseLandmark shoulder = getPreferredLandmark(pose, PoseLandmark.RIGHT_SHOULDER, PoseLandmark.LEFT_SHOULDER);
        PoseLandmark elbow = getPreferredLandmark(pose, PoseLandmark.RIGHT_ELBOW, PoseLandmark.LEFT_ELBOW);
        PoseLandmark hip = getPreferredLandmark(pose, PoseLandmark.RIGHT_HIP, PoseLandmark.LEFT_HIP);

        if (shoulder == null || elbow == null || hip == null) {
            return new FormFeedback(true, "Move fully into frame", 0);
        }

        // Check elbow pinned to side: elbow should be close to hip on x-axis
        float elbowX = elbow.getPosition().x;
        float hipX = hip.getPosition().x;
        float shoulderX = shoulder.getPosition().x;

        float armLength = Math.abs(shoulderX - elbowX);
        float elbowDrift = Math.abs(elbowX - hipX);

        // If elbow drifts more than 40% of upper arm length from hip
        if (elbowDrift > armLength * 0.6f) {
            return new FormFeedback(false, "Keep elbows pinned to sides", 2);
        }

        // Check shoulder raising (shoulder elevation during curl)
        PoseLandmark leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
        PoseLandmark rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);

        if (leftShoulder != null && rightShoulder != null) {
            float shoulderDiff = Math.abs(leftShoulder.getPosition().y - rightShoulder.getPosition().y);
            float avgShoulderToHip = Math.abs(shoulder.getPosition().y - hip.getPosition().y);

            if (shoulderDiff > avgShoulderToHip * 0.15f) {
                return new FormFeedback(false, "Don't use shoulder momentum", 1);
            }
        }

        return new FormFeedback(true, "Good form! 💪", 0);
    }

    /**
     * Calculate back tilt from vertical in degrees.
     * 0° = perfectly upright, 90° = horizontal.
     */
    private double calculateBackTilt(PointF shoulder, PointF hip) {
        double dx = Math.abs(shoulder.x - hip.x);
        double dy = Math.abs(shoulder.y - hip.y);
        if (dy == 0) return 90;
        return Math.toDegrees(Math.atan(dx / dy));
    }

    /**
     * Calculate hip sag angle relative to the shoulder-ankle line.
     * Positive = sagging, Negative = piking up.
     */
    private double calculateHipSag(PointF shoulder, PointF hip, PointF ankle) {
        double angle = PoseAnalyzer.calculateAngle(shoulder, hip, ankle);
        // In a perfect plank, shoulder-hip-ankle should be ~180°
        return 180 - angle;
    }

    private PoseLandmark getPreferredLandmark(Pose pose, int rightType, int leftType) {
        PoseLandmark right = pose.getPoseLandmark(rightType);
        return right != null ? right : pose.getPoseLandmark(leftType);
    }
}
