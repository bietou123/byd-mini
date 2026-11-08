package com.zjy.bydmini;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

public class GaugeView extends View {

    private static final float START = 135f;
    private static final float SWEEP = 270f;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF oval = new RectF();
    private SweepGradient sweep;

    private int battery = 82;
    private int range = 412;
    private float progress = 0f;
    private float targetProgress = 0.82f;
    private ValueAnimator animator;

    public GaugeView(Context c, AttributeSet a) {
        super(c, a);
        float stroke = dp(11);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(stroke);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(0xFF1A1D22);

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(stroke);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(0xFFB8FFE6);

        glowPaint.setStyle(Paint.Style.FILL);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        float stroke = dp(11);
        float pad = stroke + dp(14);
        float size = Math.min(w, h) - pad * 2f;
        float l = (w - size) / 2f;
        float t = (h - size) / 2f;
        oval.set(l, t, l + size, t + size);

        sweep = new SweepGradient(
                oval.centerX(), oval.centerY(),
                new int[]{0xFF7DEBC0, 0xFF00E5A0, 0xFF00B37D},
                new float[]{0f, 0.55f, 1f});
        Matrix m = new Matrix();
        m.postRotate(START, oval.centerX(), oval.centerY());
        sweep.setLocalMatrix(m);
        arcPaint.setShader(sweep);

        animateTo(targetProgress);
    }

    public void setBattery(int battery, int range) {
        this.battery = battery;
        this.range = range;
        animateTo(battery / 100f);
        invalidate();
    }

    public void setRange(int range) {
        this.range = range;
        invalidate();
    }

    private void animateTo(float p) {
        p = Math.max(0f, Math.min(1f, p));
        targetProgress = p;
        if (animator != null) animator.cancel();
        if (getWidth() == 0) {
            progress = p;
            return;
        }
        animator = ValueAnimator.ofFloat(progress, p);
        animator.setDuration(800);
        animator.setInterpolator(new DecelerateInterpolator(1.6f));
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (oval.isEmpty()) return;

        canvas.drawArc(oval, START, SWEEP, false, trackPaint);

        if (progress > 0.002f) {
            canvas.drawArc(oval, START, SWEEP * progress, false, arcPaint);

            float angle = (float) Math.toRadians(START + SWEEP * progress);
            float r = oval.width() / 2f;
            float cx = oval.centerX() + r * (float) Math.cos(angle);
            float cy = oval.centerY() + r * (float) Math.sin(angle);

            glowPaint.setShader(new RadialGradient(cx, cy, dp(22),
                    0x7700E5A0, 0x0000E5A0, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, dp(22), glowPaint);

            canvas.drawCircle(cx, cy, dp(5.5f), dotPaint);
        }
    }
}
