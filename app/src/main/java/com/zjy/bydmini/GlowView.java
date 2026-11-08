package com.zjy.bydmini;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class GlowView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float t = 0f;
    private ValueAnimator animator;

    private final int cGrid;
    private final int cGlow;

    public GlowView(Context c, AttributeSet a) {
        super(c, a);
        cGrid = c.getColor(R.color.grid);
        cGlow = c.getColor(R.color.glow);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(Math.max(1f, getResources().getDisplayMetrics().density * 0.7f));
        linePaint.setColor(cGrid);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(34000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        float a = (float) (t * Math.PI * 2);

        float gx = w * (0.50f + 0.10f * (float) Math.sin(a));
        float gy = -h * 0.16f + h * 0.05f * (float) Math.cos(a * 0.8f);
        paint.setShader(new RadialGradient(gx, gy, w * 1.15f,
                cGlow, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);

        float s = getResources().getDisplayMetrics().density * 46f;
        for (float x = 0; x <= w; x += s) {
            canvas.drawLine(x, 0, x, h, linePaint);
        }
        for (float y = 0; y <= h; y += s) {
            canvas.drawLine(0, y, w, y, linePaint);
        }
    }
}
