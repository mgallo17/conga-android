package com.mgallo17.conga;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom view that draws the robot's cleaning path as a trail of dots.
 * Positions are received from StatusResponse and normalised to the view bounds.
 */
public class RobotPathView extends View {

    private static final int   TRAIL_COLOR  = 0xFF2196F3;
    private static final int   ROBOT_COLOR  = 0xFFE53935;
    private static final float DOT_RADIUS   = 4f;
    private static final float ROBOT_RADIUS = 10f;
    private static final int   MAX_POINTS   = 2000;

    private final Paint trailPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint robotPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<float[]> points = new ArrayList<>();
    private boolean tracking = false;

    // Track min/max for normalisation
    private float minX = 0, maxX = 10, minY = 0, maxY = 10;

    public RobotPathView(Context context) { super(context); init(); }
    public RobotPathView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public RobotPathView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle); init();
    }

    private void init() {
        trailPaint.setColor(TRAIL_COLOR);
        trailPaint.setStyle(Paint.Style.FILL);
        trailPaint.setAlpha(180);

        robotPaint.setColor(ROBOT_COLOR);
        robotPaint.setStyle(Paint.Style.FILL);

        gridPaint.setColor(Color.LTGRAY);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f);
    }

    public void startTracking() {
        points.clear();
        tracking = true;
        invalidate();
    }

    public void addPoint(float x, float y) {
        if (!tracking) return;
        if (points.size() >= MAX_POINTS) points.remove(0);
        points.add(new float[]{x, y});

        // Update bounds
        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
        if (y < minY) minY = y;
        if (y > maxY) maxY = y;

        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();

        // Background
        canvas.drawColor(0xFFF5F5F5);

        // Grid
        int gridLines = 10;
        for (int i = 1; i < gridLines; i++) {
            float x = w * i / (float) gridLines;
            float y = h * i / (float) gridLines;
            canvas.drawLine(x, 0, x, h, gridPaint);
            canvas.drawLine(0, y, w, y, gridPaint);
        }

        if (points.isEmpty()) return;

        float rangeX = Math.max(maxX - minX, 1f);
        float rangeY = Math.max(maxY - minY, 1f);
        float padding = 20f;

        // Draw trail
        for (int i = 0; i < points.size() - 1; i++) {
            float[] p = points.get(i);
            float px = padding + (p[0] - minX) / rangeX * (w - 2 * padding);
            float py = padding + (p[1] - minY) / rangeY * (h - 2 * padding);
            // Fade older points
            int alpha = 80 + (int) (150 * i / (float) points.size());
            trailPaint.setAlpha(alpha);
            canvas.drawCircle(px, py, DOT_RADIUS, trailPaint);
        }

        // Draw robot at last position
        float[] last = points.get(points.size() - 1);
        float rx = padding + (last[0] - minX) / rangeX * (w - 2 * padding);
        float ry = padding + (last[1] - minY) / rangeY * (h - 2 * padding);
        canvas.drawCircle(rx, ry, ROBOT_RADIUS, robotPaint);
    }
}
