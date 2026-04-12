package com.smarthealth.posedetection;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.View;

import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;

import java.util.Arrays;
import java.util.List;

/**
 * Custom overlay view that draws skeleton, feedback, and rep count
 * on top of the CameraX preview.
 */
public class PoseOverlayView extends View {

    private Pose currentPose;
    private FormValidator.FormFeedback currentFeedback;
    private int repCount = 0;
    private String exerciseName = "";
    private boolean isTimerPaused = false;

    // Image dimensions for coordinate scaling
    private int imageWidth = 1;
    private int imageHeight = 1;
    private boolean isFrontCamera = true;

    // Paints
    private final Paint skeletonPaint = new Paint();
    private final Paint goodJointPaint = new Paint();
    private final Paint badJointPaint = new Paint();
    private final Paint feedbackPaint = new Paint();
    private final Paint repCountPaint = new Paint();
    private final Paint overlayBgPaint = new Paint();
    private final Paint labelPaint = new Paint();
    private final Paint pausedPaint = new Paint();

    // Skeleton connections (pairs of landmark types)
    private static final int[][] SKELETON_CONNECTIONS = {
            // Torso
            {PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER},
            {PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP},
            {PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP},
            {PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP},
            // Left arm
            {PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW},
            {PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST},
            // Right arm
            {PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW},
            {PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST},
            // Left leg
            {PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE},
            {PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE},
            // Right leg
            {PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE},
            {PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE},
    };

    // Key joints to draw larger
    private static final List<Integer> KEY_JOINTS = Arrays.asList(
            PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW,
            PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST,
            PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE,
            PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE
    );

    public PoseOverlayView(Context context) {
        super(context);
        init();
    }

    public PoseOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PoseOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Skeleton lines
        skeletonPaint.setColor(Color.parseColor("#00E5FF"));
        skeletonPaint.setStrokeWidth(6f);
        skeletonPaint.setStyle(Paint.Style.STROKE);
        skeletonPaint.setAntiAlias(true);

        // Good form joint dots
        goodJointPaint.setColor(Color.parseColor("#00E676"));
        goodJointPaint.setStyle(Paint.Style.FILL);
        goodJointPaint.setAntiAlias(true);

        // Bad form joint dots
        badJointPaint.setColor(Color.parseColor("#FF1744"));
        badJointPaint.setStyle(Paint.Style.FILL);
        badJointPaint.setAntiAlias(true);

        // Feedback text
        feedbackPaint.setColor(Color.WHITE);
        feedbackPaint.setTextSize(52f);
        feedbackPaint.setAntiAlias(true);
        feedbackPaint.setTextAlign(Paint.Align.CENTER);
        feedbackPaint.setShadowLayer(8f, 0, 2, Color.BLACK);
        feedbackPaint.setFakeBoldText(true);

        // Rep count
        repCountPaint.setColor(Color.WHITE);
        repCountPaint.setTextSize(120f);
        repCountPaint.setAntiAlias(true);
        repCountPaint.setTextAlign(Paint.Align.CENTER);
        repCountPaint.setShadowLayer(12f, 0, 4, Color.BLACK);
        repCountPaint.setFakeBoldText(true);

        // Semi-transparent overlay backgrounds
        overlayBgPaint.setColor(Color.parseColor("#66000000"));
        overlayBgPaint.setStyle(Paint.Style.FILL);

        // Label text
        labelPaint.setColor(Color.parseColor("#B0FFFFFF"));
        labelPaint.setTextSize(32f);
        labelPaint.setAntiAlias(true);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        // Paused indicator
        pausedPaint.setColor(Color.parseColor("#CCFF6D00"));
        pausedPaint.setTextSize(48f);
        pausedPaint.setAntiAlias(true);
        pausedPaint.setTextAlign(Paint.Align.CENTER);
        pausedPaint.setFakeBoldText(true);
    }

    public void updatePose(Pose pose, FormValidator.FormFeedback feedback,
                           int repCount, int imgWidth, int imgHeight) {
        this.currentPose = pose;
        this.currentFeedback = feedback;
        this.repCount = repCount;
        this.imageWidth = imgWidth;
        this.imageHeight = imgHeight;
        postInvalidate();
    }

    public void setExerciseName(String name) {
        this.exerciseName = name;
    }

    public void setFrontCamera(boolean frontCamera) {
        this.isFrontCamera = frontCamera;
    }

    public void setTimerPaused(boolean paused) {
        this.isTimerPaused = paused;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        boolean formIncorrect = currentFeedback != null && !currentFeedback.isCorrect;

        // Draw skeleton
        if (currentPose != null) {
            drawSkeleton(canvas, formIncorrect);
            drawJoints(canvas, formIncorrect);
        }

        // Draw feedback overlay at top
        drawFeedbackOverlay(canvas);

        // Draw rep count center-bottom
        drawRepCount(canvas);

        // Draw paused indicator if timer is paused
        if (isTimerPaused) {
            drawPausedIndicator(canvas);
        }
    }

    private void drawSkeleton(Canvas canvas, boolean formIncorrect) {
        Paint linePaint = new Paint(skeletonPaint);
        if (formIncorrect) {
            linePaint.setColor(Color.parseColor("#FF5252"));
        }

        for (int[] connection : SKELETON_CONNECTIONS) {
            PoseLandmark start = currentPose.getPoseLandmark(connection[0]);
            PoseLandmark end = currentPose.getPoseLandmark(connection[1]);

            if (start != null && end != null) {
                PointF startPoint = scalePoint(start.getPosition());
                PointF endPoint = scalePoint(end.getPosition());
                canvas.drawLine(startPoint.x, startPoint.y, endPoint.x, endPoint.y, linePaint);
            }
        }
    }

    private void drawJoints(Canvas canvas, boolean formIncorrect) {
        Paint jointPaint = formIncorrect ? badJointPaint : goodJointPaint;

        for (int jointType : KEY_JOINTS) {
            PoseLandmark landmark = currentPose.getPoseLandmark(jointType);
            if (landmark != null) {
                PointF point = scalePoint(landmark.getPosition());
                canvas.drawCircle(point.x, point.y, 10f, jointPaint);
            }
        }
    }

    private void drawFeedbackOverlay(Canvas canvas) {
        if (currentFeedback == null) return;

        float width = getWidth();

        // Background strip at top
        int bgColor;
        switch (currentFeedback.severity) {
            case 2:
                bgColor = Color.parseColor("#CCD32F2F");
                break;
            case 1:
                bgColor = Color.parseColor("#CCFF8F00");
                break;
            default:
                bgColor = Color.parseColor("#CC2E7D32");
                break;
        }
        overlayBgPaint.setColor(bgColor);
        canvas.drawRect(0, 0, width, 140, overlayBgPaint);

        // Feedback text
        canvas.drawText(currentFeedback.message, width / 2f, 95, feedbackPaint);
    }

    private void drawRepCount(Canvas canvas) {
        float width = getWidth();
        float height = getHeight();

        // Background circle
        Paint circleBg = new Paint();
        circleBg.setColor(Color.parseColor("#88000000"));
        circleBg.setStyle(Paint.Style.FILL);
        circleBg.setAntiAlias(true);
        canvas.drawCircle(width / 2f, height - 280, 90, circleBg);

        // Rep number
        canvas.drawText(String.valueOf(repCount), width / 2f, height - 250, repCountPaint);

        // Label
        canvas.drawText("REPS", width / 2f, height - 175, labelPaint);

        // Exercise name
        if (exerciseName != null && !exerciseName.isEmpty()) {
            canvas.drawText(exerciseName.toUpperCase(), width / 2f, height - 130, labelPaint);
        }
    }

    private void drawPausedIndicator(Canvas canvas) {
        float width = getWidth();

        // Draw pulsing "PAUSED — Fix Form" text
        canvas.drawRect(0, 140, width, 210, overlayBgPaint);
        canvas.drawText("⏸ PAUSED — Fix your form to resume", width / 2f, 190, pausedPaint);
    }

    /**
     * Scale pose landmark coordinates from image space to view space.
     * Handles mirror flip for front camera.
     */
    private PointF scalePoint(PointF point) {
        float scaleX = (float) getWidth() / imageWidth;
        float scaleY = (float) getHeight() / imageHeight;

        float x = point.x * scaleX;
        float y = point.y * scaleY;

        // Mirror for front camera
        if (isFrontCamera) {
            x = getWidth() - x;
        }

        return new PointF(x, y);
    }
}
