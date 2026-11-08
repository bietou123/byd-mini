package com.zjy.bydmini;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.List;

/**
 * 保时捷 Panamera 971 侧视图。全部坐标以车长为单位：
 * 长5049 高1423 轴距2950 前悬910 轮径700 ——
 * 车顶 .282 / 腰线 .19 / 肩线 .174 / 轮顶 .139 / 裙边 .032。
 */
public class CarView extends View {

    private static final float TIRE_R = 0.0742f;
    private static final float AXLE_H = 0.0742f;
    private static final float ARCH_F = 0.0808f;
    private static final float ARCH_R = 0.0838f;
    private static final float FRONT_U = 0.180f;
    private static final float REAR_U = 0.764f;

    // 肩线控制点 {u, h}
    private static final float[][] CREASE_PTS = {
            {0.050f, 0.164f}, {0.140f, 0.168f}, {0.240f, 0.171f}, {0.352f, 0.174f},
            {0.480f, 0.177f}, {0.610f, 0.180f}, {0.740f, 0.182f},
            {0.828f, 0.183f}, {0.890f, 0.183f}, {0.954f, 0.183f}
    };

    // 肩线上方柔光带 {纵向偏移(车长比), 线宽(车长比)}
    private static final float[][] CREASE_GLOW = {
            {-0.0348f, 0.0370f}, {-0.0239f, 0.0261f}, {-0.0152f, 0.0163f}, {-0.0076f, 0.0087f}
    };
    private static final int[] CREASE_GLOW_A = {0x0D, 0x17, 0x1F, 0x29};
    // 肩线下方阴影带
    private static final float[][] CREASE_SHADE = {
            {0.0304f, 0.0348f}, {0.0196f, 0.0239f}
    };
    private static final int[] CREASE_SHADE_A = {0x38, 0x38};

    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sweepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tirePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint discPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint caliperPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint archPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint underglowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint reflectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);

    private static final PorterDuffXfermode DST_IN =
            new PorterDuffXfermode(PorterDuff.Mode.DST_IN);

    private final Path bodyPath = new Path();
    private final Path topEdgePath = new Path();
    private final Path glassPath = new Path();
    private final Path creasePath = new Path();
    private final Path scratchPath = new Path();
    private final Path headPath = new Path();
    private final Path tailPath = new Path();
    private final Path doorPath = new Path();
    private final Path doorPath2 = new Path();
    private final Path doorPath3 = new Path();
    private final Path mirrorPath = new Path();
    private final Path cavityPath = new Path();
    private final Path spokePath = new Path();
    private final List<Path> glowPaths = new ArrayList<>();
    private final List<Path> shadePaths = new ArrayList<>();

    private final RectF frontArch = new RectF();
    private final RectF rearArch = new RectF();
    private final RectF shadowRect = new RectF();
    private final RectF detailRect = new RectF();
    private final RectF tmp = new RectF();

    private final int cText;
    private final int cBg;
    private final int cDim;
    private final int cOk;
    private final boolean night;

    private float carW;
    private float left;
    private float groundY;

    private Bitmap carBmp;

    private boolean locked = true;
    private float bob = 0f;
    private float phase = 0f;
    private float lightPulse = 1f;
    private float popScale = 1f;

    private ValueAnimator animator;
    private ValueAnimator popAnim;

    public CarView(Context c, AttributeSet a) {
        super(c, a);

        cText = c.getColor(R.color.text);
        cBg = c.getColor(R.color.bg);
        cDim = c.getColor(R.color.text_dim);
        cOk = c.getColor(R.color.ok);
        night = isDark(cBg);

        caliperPaint.setColor(cOk);
        caliperPaint.setStyle(Paint.Style.STROKE);
        caliperPaint.setStrokeCap(Paint.Cap.ROUND);
        caliperPaint.setAntiAlias(true);

        spokePaint.setStyle(Paint.Style.STROKE);
        spokePaint.setStrokeCap(Paint.Cap.ROUND);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        lightPaint.setStyle(Paint.Style.STROKE);
        lightPaint.setStrokeCap(Paint.Cap.ROUND);

        archPaint.setColor(night ? 0xFF040405 : 0xFF070709);

        for (int i = 0; i < CREASE_GLOW.length; i++) glowPaths.add(new Path());
        for (int i = 0; i < CREASE_SHADE.length; i++) shadePaths.add(new Path());
    }

    private static int a(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000
                | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8)
                | (int) (ab + (bb - ab) * t);
    }

    private static boolean isDark(int c) {
        int l = (((c >> 16) & 0xFF) * 299 + ((c >> 8) & 0xFF) * 587 + (c & 0xFF) * 114) / 1000;
        return l < 128;
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    /** 以车长为单位的线宽，太细时退回 0.7dp 保证可见 */
    private float sw(float frac) {
        return Math.max(carW * frac, dp(0.7f));
    }

    private float X(float u) {
        return left + carW * u;
    }

    private float Y(float h) {
        return groundY - carW * h;
    }

    public void setCarBitmap(Bitmap b) {
        carBmp = b;
        invalidate();
    }

    public void setLocked(boolean locked) {
        boolean changed = this.locked != locked;
        this.locked = locked;
        if (changed) pop();
        invalidate();
    }

    private void pop() {
        if (popAnim != null) popAnim.cancel();
        popAnim = ValueAnimator.ofFloat(0f, 1f);
        popAnim.setDuration(560);
        popAnim.setInterpolator(new DecelerateInterpolator());
        popAnim.addUpdateListener(a -> {
            float p = (float) a.getAnimatedValue();
            popScale = 1f + (float) Math.sin(p * Math.PI) * 0.075f;
            invalidate();
        });
        popAnim.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (w == 0 || h == 0) return;
        carW = w * 0.99f;
        left = (w - carW) / 2f;
        groundY = h * 0.64f;
        buildShapes();
    }

    private void buildShapes() {
        frontArch.set(X(FRONT_U - ARCH_F), Y(AXLE_H + ARCH_F),
                X(FRONT_U + ARCH_F), Y(AXLE_H - ARCH_F));
        rearArch.set(X(REAR_U - ARCH_R), Y(AXLE_H + ARCH_R),
                X(REAR_U + ARCH_R), Y(AXLE_H - ARCH_R));

        // ── 车身轮廓 ──
        bodyPath.reset();
        bodyPath.moveTo(X(0.034f), Y(0.036f));
        bodyPath.cubicTo(X(0.014f), Y(0.037f), X(0.005f), Y(0.046f), X(0.002f), Y(0.064f));
        bodyPath.cubicTo(X(0.000f), Y(0.086f), X(0.002f), Y(0.112f), X(0.010f), Y(0.132f));
        bodyPath.cubicTo(X(0.018f), Y(0.148f), X(0.030f), Y(0.158f), X(0.048f), Y(0.163f));
        bodyPath.cubicTo(X(0.068f), Y(0.169f), X(0.086f), Y(0.173f), X(0.106f), Y(0.176f));
        bodyPath.cubicTo(X(0.136f), Y(0.180f), X(0.164f), Y(0.182f), X(0.192f), Y(0.183f));
        bodyPath.cubicTo(X(0.226f), Y(0.184f), X(0.258f), Y(0.186f), X(0.288f), Y(0.190f));
        bodyPath.cubicTo(X(0.314f), Y(0.193f), X(0.332f), Y(0.195f), X(0.348f), Y(0.197f));
        bodyPath.cubicTo(X(0.376f), Y(0.228f), X(0.410f), Y(0.256f), X(0.448f), Y(0.271f));
        bodyPath.cubicTo(X(0.474f), Y(0.279f), X(0.500f), Y(0.282f), X(0.532f), Y(0.283f));
        bodyPath.cubicTo(X(0.566f), Y(0.283f), X(0.606f), Y(0.276f), X(0.650f), Y(0.266f));
        bodyPath.cubicTo(X(0.700f), Y(0.254f), X(0.752f), Y(0.242f), X(0.800f), Y(0.231f));
        bodyPath.cubicTo(X(0.846f), Y(0.221f), X(0.884f), Y(0.212f), X(0.916f), Y(0.203f));
        bodyPath.cubicTo(X(0.944f), Y(0.196f), X(0.968f), Y(0.188f), X(0.984f), Y(0.178f));
        bodyPath.cubicTo(X(0.995f), Y(0.166f), X(0.999f), Y(0.146f), X(0.999f), Y(0.124f));
        bodyPath.cubicTo(X(0.999f), Y(0.102f), X(0.994f), Y(0.080f), X(0.986f), Y(0.063f));
        bodyPath.cubicTo(X(0.976f), Y(0.049f), X(0.962f), Y(0.039f), X(0.946f), Y(0.035f));
        bodyPath.cubicTo(X(0.930f), Y(0.032f), X(0.912f), Y(0.032f), X(0.896f), Y(0.033f));
        bodyPath.lineTo(X(REAR_U + ARCH_R), Y(0.033f));
        bodyPath.arcTo(rearArch, 0f, -180f, false);
        bodyPath.lineTo(X(FRONT_U + ARCH_F), Y(0.033f));
        bodyPath.arcTo(frontArch, 0f, -180f, false);
        bodyPath.lineTo(X(0.034f), Y(0.036f));
        bodyPath.close();

        topEdgePath.reset();
        topEdgePath.moveTo(X(0.002f), Y(0.064f));
        topEdgePath.cubicTo(X(0.000f), Y(0.086f), X(0.002f), Y(0.112f), X(0.010f), Y(0.132f));
        topEdgePath.cubicTo(X(0.018f), Y(0.148f), X(0.030f), Y(0.158f), X(0.048f), Y(0.163f));
        topEdgePath.cubicTo(X(0.068f), Y(0.169f), X(0.086f), Y(0.173f), X(0.106f), Y(0.176f));
        topEdgePath.cubicTo(X(0.136f), Y(0.180f), X(0.164f), Y(0.182f), X(0.192f), Y(0.183f));
        topEdgePath.cubicTo(X(0.226f), Y(0.184f), X(0.258f), Y(0.186f), X(0.288f), Y(0.190f));
        topEdgePath.cubicTo(X(0.314f), Y(0.193f), X(0.332f), Y(0.195f), X(0.348f), Y(0.197f));
        topEdgePath.cubicTo(X(0.376f), Y(0.228f), X(0.410f), Y(0.256f), X(0.448f), Y(0.271f));
        topEdgePath.cubicTo(X(0.474f), Y(0.279f), X(0.500f), Y(0.282f), X(0.532f), Y(0.283f));
        topEdgePath.cubicTo(X(0.566f), Y(0.283f), X(0.606f), Y(0.276f), X(0.650f), Y(0.266f));
        topEdgePath.cubicTo(X(0.700f), Y(0.254f), X(0.752f), Y(0.242f), X(0.800f), Y(0.231f));
        topEdgePath.cubicTo(X(0.846f), Y(0.221f), X(0.884f), Y(0.212f), X(0.916f), Y(0.203f));
        topEdgePath.cubicTo(X(0.944f), Y(0.196f), X(0.968f), Y(0.188f), X(0.984f), Y(0.178f));

        // ── 侧窗 ──
        glassPath.reset();
        glassPath.moveTo(X(0.350f), Y(0.198f));
        glassPath.cubicTo(X(0.380f), Y(0.230f), X(0.414f), Y(0.256f), X(0.452f), Y(0.269f));
        glassPath.cubicTo(X(0.480f), Y(0.275f), X(0.504f), Y(0.277f), X(0.534f), Y(0.277f));
        glassPath.cubicTo(X(0.570f), Y(0.276f), X(0.610f), Y(0.269f), X(0.654f), Y(0.259f));
        glassPath.cubicTo(X(0.704f), Y(0.247f), X(0.756f), Y(0.235f), X(0.804f), Y(0.223f));
        glassPath.cubicTo(X(0.832f), Y(0.217f), X(0.852f), Y(0.214f), X(0.866f), Y(0.210f));
        glassPath.cubicTo(X(0.842f), Y(0.198f), X(0.810f), Y(0.191f), X(0.770f), Y(0.189f));
        glassPath.cubicTo(X(0.640f), Y(0.188f), X(0.480f), Y(0.192f), X(0.350f), Y(0.198f));
        glassPath.close();

        buildCrease(creasePath, 0f);
        for (int i = 0; i < CREASE_GLOW.length; i++) {
            buildCrease(glowPaths.get(i), carW * CREASE_GLOW[i][0]);
        }
        for (int i = 0; i < CREASE_SHADE.length; i++) {
            buildCrease(shadePaths.get(i), carW * CREASE_SHADE[i][0]);
        }

        headPath.reset();
        headPath.moveTo(X(0.012f), Y(0.138f));
        headPath.cubicTo(X(0.038f), Y(0.156f), X(0.070f), Y(0.170f), X(0.100f), Y(0.170f));
        headPath.cubicTo(X(0.086f), Y(0.155f), X(0.050f), Y(0.136f), X(0.012f), Y(0.130f));
        headPath.close();

        tailPath.reset();
        tailPath.moveTo(X(0.850f), Y(0.1930f));
        tailPath.cubicTo(X(0.896f), Y(0.1884f), X(0.944f), Y(0.1836f), X(0.984f), Y(0.1782f));
        tailPath.lineTo(X(0.993f), Y(0.1728f));
        tailPath.cubicTo(X(0.948f), Y(0.1794f), X(0.898f), Y(0.1848f), X(0.850f), Y(0.1892f));
        tailPath.close();

        doorPath.reset();
        doorPath.moveTo(X(0.302f), Y(0.190f));
        doorPath.cubicTo(X(0.310f), Y(0.150f), X(0.306f), Y(0.090f), X(0.298f), Y(0.031f));

        doorPath2.reset();
        doorPath2.moveTo(X(0.538f), Y(0.192f));
        doorPath2.cubicTo(X(0.542f), Y(0.150f), X(0.538f), Y(0.090f), X(0.530f), Y(0.030f));

        doorPath3.reset();
        doorPath3.moveTo(X(0.754f), Y(0.194f));
        doorPath3.cubicTo(X(0.758f), Y(0.152f), X(0.752f), Y(0.092f), X(0.744f), Y(0.029f));

        mirrorPath.reset();
        mirrorPath.moveTo(X(0.330f), Y(0.198f));
        mirrorPath.cubicTo(X(0.342f), Y(0.210f), X(0.360f), Y(0.215f), X(0.374f), Y(0.213f));
        mirrorPath.cubicTo(X(0.383f), Y(0.211f), X(0.385f), Y(0.199f), X(0.379f), Y(0.193f));
        mirrorPath.cubicTo(X(0.362f), Y(0.189f), X(0.342f), Y(0.189f), X(0.330f), Y(0.191f));
        mirrorPath.close();
    }

    private void buildCrease(Path p, float dy) {
        p.reset();
        p.moveTo(X(CREASE_PTS[0][0]), Y(CREASE_PTS[0][1]) + dy);
        p.cubicTo(X(CREASE_PTS[1][0]), Y(CREASE_PTS[1][1]) + dy,
                X(CREASE_PTS[2][0]), Y(CREASE_PTS[2][1]) + dy,
                X(CREASE_PTS[3][0]), Y(CREASE_PTS[3][1]) + dy);
        p.cubicTo(X(CREASE_PTS[4][0]), Y(CREASE_PTS[4][1]) + dy,
                X(CREASE_PTS[5][0]), Y(CREASE_PTS[5][1]) + dy,
                X(CREASE_PTS[6][0]), Y(CREASE_PTS[6][1]) + dy);
        p.cubicTo(X(CREASE_PTS[7][0]), Y(CREASE_PTS[7][1]) + dy,
                X(CREASE_PTS[8][0]), Y(CREASE_PTS[8][1]) + dy,
                X(CREASE_PTS[9][0]), Y(CREASE_PTS[9][1]) + dy);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animator = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
        animator.setDuration(6400);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(an -> {
            float v = (float) an.getAnimatedValue();
            bob = (float) Math.sin(v) * dp(1.6f);
            phase = v / (float) (Math.PI * 2);
            lightPulse = 0.66f + 0.34f * (float) Math.sin(v * 2.0);
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        if (popAnim != null) popAnim.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        canvas.save();
        canvas.scale(popScale, popScale, w / 2f, h * 0.62f);

        if (carBmp != null) {
            drawBitmapCar(canvas, w, h);
        } else if (carW > 0f) {
            drawCar(canvas);
        }

        canvas.restore();
    }

    private void drawBitmapCar(Canvas canvas, int w, int h) {
        float maxW = w * 1.00f;
        float maxH = h * 0.90f;
        float s = Math.min(maxW / carBmp.getWidth(), maxH / carBmp.getHeight());
        float dw = carBmp.getWidth() * s;
        float dh = carBmp.getHeight() * s;
        float dx = (w - dw) / 2f;
        float dy = h * 0.40f - dh / 2f + bob;

        shadowRect.set(w * 0.12f, dy + dh * 0.88f, w * 0.88f, dy + dh * 1.06f);
        shadowPaint.setShader(new RadialGradient(shadowRect.centerX(), shadowRect.centerY(),
                w * 0.42f, 0x78000000, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawOval(shadowRect, shadowPaint);
        shadowPaint.setShader(null);

        tmp.set(dx, dy, dx + dw, dy + dh);
        canvas.drawBitmap(carBmp, null, tmp, bitmapPaint);
    }

    private void drawCar(Canvas cv) {
        cv.save();
        cv.translate(0f, bob);

        drawReflection(cv);
        drawGroundShadow(cv);

        drawWheel(cv, X(FRONT_U), Y(AXLE_H), frontArch);
        drawWheel(cv, X(REAR_U), Y(AXLE_H), rearArch);

        drawBody(cv);
        drawGlass(cv);
        drawDetails(cv);
        drawLights(cv);

        cv.restore();
    }

    private void drawReflection(Canvas cv) {
        float reflH = carW * 0.13f;
        int layer = cv.saveLayer(0f, groundY - 2f, getWidth(), groundY + reflH + 2f, null);
        cv.save();
        cv.clipRect(0f, groundY, getWidth(), groundY + reflH);
        cv.scale(1f, -1f, 0f, groundY);
        reflectPaint.setShader(null);
        reflectPaint.setColor(mix(cText, 0xFFFFFFFF, 0.26f));
        reflectPaint.setAlpha(night ? 66 : 77);
        cv.drawPath(bodyPath, reflectPaint);
        cv.restore();

        fadePaint.setShader(new LinearGradient(0f, groundY, 0f, groundY + reflH,
                0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        fadePaint.setXfermode(DST_IN);
        cv.drawRect(0f, groundY, getWidth(), groundY + reflH, fadePaint);
        fadePaint.setXfermode(null);
        fadePaint.setShader(null);

        cv.restoreToCount(layer);
    }

    private void drawGroundShadow(Canvas cv) {
        shadowRect.set(X(-0.05f), Y(0.024f), X(1.05f), Y(-0.072f));
        shadowPaint.setShader(new RadialGradient(X(0.5f), Y(0.002f),
                carW * 0.545f, night ? 0x8A000000 : 0x7A000000, 0x00000000,
                Shader.TileMode.CLAMP));
        cv.drawOval(shadowRect, shadowPaint);

        shadowPaint.setShader(null);
        shadowPaint.setColor(night ? 0x70000000 : 0x57000000);
        shadowRect.set(X(FRONT_U - 0.080f), Y(-0.004f), X(FRONT_U + 0.080f), Y(-0.028f));
        cv.drawOval(shadowRect, shadowPaint);
        shadowRect.set(X(REAR_U - 0.085f), Y(-0.004f), X(REAR_U + 0.085f), Y(-0.030f));
        cv.drawOval(shadowRect, shadowPaint);

        if (!locked) {
            underglowPaint.setColor(a(cOk, (int) (34 * lightPulse)));
            shadowRect.set(X(0.12f), Y(0.040f), X(0.90f), Y(-0.055f));
            cv.drawOval(shadowRect, underglowPaint);
        }
    }

    private void drawWheel(Canvas cv, float cx, float cy, RectF arch) {
        float r = carW * TIRE_R;
        float ar = arch.width() / 2f;

        // 轮拱内腔：整圆填暗，避免浅色背景/反光从胎侧漏出；
        // 靠外一圈才压深，孔洞边缘不会在轮胎外露出一圈硬黑环
        int save = cv.save();
        cv.clipRect(0f, 0f, getWidth(), groundY);
        archPaint.setColor(night ? 0xFF070708 : 0xFF0C0D10);
        cv.drawCircle(cx, cy, ar * 0.995f, archPaint);
        shadowPaint.setShader(new RadialGradient(cx, cy, ar * 0.995f,
                new int[]{0x00000000, 0xE0000000},
                new float[]{0.62f, 1f}, Shader.TileMode.CLAMP));
        cv.drawCircle(cx, cy, ar * 0.995f, shadowPaint);
        shadowPaint.setShader(null);
        cv.restoreToCount(save);

        // 轮胎
        tirePaint.setShader(new RadialGradient(cx, cy - r * 0.55f, r * 1.55f,
                new int[]{0xFF232427, 0xFF111214, 0xFF050506},
                new float[]{0f, 0.72f, 1f}, Shader.TileMode.CLAMP));
        cv.drawCircle(cx, cy, r, tirePaint);
        tirePaint.setShader(null);

        tirePaint.setStyle(Paint.Style.STROKE);
        tirePaint.setStrokeWidth(r * 0.10f);
        tirePaint.setStrokeCap(Paint.Cap.ROUND);
        tirePaint.setColor(0x1AFFFFFF);
        tmp.set(cx - r * 0.855f, cy - r * 0.855f, cx + r * 0.855f, cy + r * 0.855f);
        cv.drawArc(tmp, 158f, 74f, false, tirePaint);
        tirePaint.setStyle(Paint.Style.FILL);

        // 刹车盘 + 卡钳（在辐条后面，透过辐条能看到）
        discPaint.setColor(0xFF26282C);
        cv.drawCircle(cx, cy, r * 0.500f, discPaint);
        discPaint.setStyle(Paint.Style.STROKE);
        discPaint.setStrokeWidth(r * 0.012f);
        discPaint.setColor(0x1AFFFFFF);
        cv.drawCircle(cx, cy, r * 0.455f, discPaint);
        discPaint.setStyle(Paint.Style.FILL);

        caliperPaint.setStrokeWidth(r * 0.105f);
        tmp.set(cx - r * 0.405f, cy - r * 0.405f, cx + r * 0.405f, cy + r * 0.405f);
        cv.drawArc(tmp, 116f, 56f, false, caliperPaint);

        // 轮辋外圈（枪灰，不要亮银）
        rimPaint.setShader(new LinearGradient(cx, cy - r * 0.80f, cx, cy + r * 0.80f,
                new int[]{0xFF3A3D44, 0xFF1A1C20, 0xFF0D0E11},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        cv.drawCircle(cx, cy, r * 0.790f, rimPaint);
        rimPaint.setShader(null);

        linePaint.setStrokeWidth(r * 0.014f);
        linePaint.setColor(0x80DCE2EC);
        tmp.set(cx - r * 0.780f, cy - r * 0.780f, cx + r * 0.780f, cy + r * 0.780f);
        cv.drawArc(tmp, 192f, 156f, false, linePaint);

        // 5 组双辐
        cv.save();
        cv.translate(cx, cy);
        cv.rotate(8f);
        for (int i = 0; i < 5; i++) {
            for (int k = 0; k < 2; k++) {
                cv.save();
                cv.rotate(i * 72f + (k == 0 ? -4.6f : 4.6f));

                spokePaint.setStyle(Paint.Style.FILL);
                spokePaint.setColor(0xFF202227);
                spokePath.reset();
                spokePath.moveTo(r * 0.150f, -r * 0.052f);
                spokePath.lineTo(r * 0.735f, -r * 0.040f);
                spokePath.lineTo(r * 0.735f, r * 0.012f);
                spokePath.lineTo(r * 0.150f, r * 0.030f);
                spokePath.close();
                cv.drawPath(spokePath, spokePaint);

                spokePaint.setStyle(Paint.Style.STROKE);
                spokePaint.setStrokeWidth(r * 0.009f);
                spokePaint.setColor(0x6BCED6E2);
                spokePath.reset();
                spokePath.moveTo(r * 0.160f, -r * 0.050f);
                spokePath.lineTo(r * 0.730f, -r * 0.038f);
                cv.drawPath(spokePath, spokePaint);
                cv.restore();
            }
        }
        cv.restore();

        // 中心盖
        rimPaint.setColor(0xFF17181C);
        cv.drawCircle(cx, cy, r * 0.165f, rimPaint);
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(r * 0.010f);
        rimPaint.setColor(0x73D6DCE6);
        cv.drawCircle(cx, cy, r * 0.135f, rimPaint);
        rimPaint.setStyle(Paint.Style.FILL);
    }

    private void drawBody(Canvas cv) {
        int[] cols = night
                ? new int[]{0xFF4A4C52, 0xFF8E9198, 0xFFC8CBD2, 0xFF9A9DA4, 0xFF5A5C62, 0xFF1A1B1F}
                : new int[]{0xFF393A3F, 0xFF54565C, 0xFF76787E, 0xFF424349, 0xFF24252A, 0xFF08080A};

        bodyPaint.setShader(new LinearGradient(0f, Y(0.292f), 0f, Y(0.020f), cols,
                new float[]{0f, 0.16f, 0.34f, 0.56f, 0.80f, 1f}, Shader.TileMode.CLAMP));
        cv.drawPath(bodyPath, bodyPaint);
        bodyPaint.setShader(null);

        cv.save();
        cv.clipPath(bodyPath);

        // 车顶环境光
        shadePaint.setShader(new RadialGradient(X(0.34f), Y(0.203f), carW * 0.60f,
                0x33FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawPaint(shadePaint);

        // 前后翼子板体积
        shadePaint.setShader(new RadialGradient(X(FRONT_U), Y(0.152f), carW * 0.100f,
                0x3DFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawPaint(shadePaint);
        shadePaint.setShader(new RadialGradient(X(REAR_U), Y(0.160f), carW * 0.120f,
                0x47FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawPaint(shadePaint);
        shadePaint.setShader(null);

        // 肩线上方柔光 / 下方阴影
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < CREASE_GLOW.length; i++) {
            linePaint.setStrokeWidth(Math.max(carW * CREASE_GLOW[i][1], dp(1f)));
            linePaint.setColor(CREASE_GLOW_A[i] << 24 | 0x00FFFFFF);
            cv.drawPath(glowPaths.get(i), linePaint);
        }
        for (int i = 0; i < CREASE_SHADE.length; i++) {
            linePaint.setStrokeWidth(Math.max(carW * CREASE_SHADE[i][1], dp(1f)));
            linePaint.setColor(CREASE_SHADE_A[i] << 24);
            cv.drawPath(shadePaths.get(i), linePaint);
        }

        // 门板下段地面反光 + 裙边压暗
        shadePaint.setShader(new LinearGradient(0f, Y(0.120f), 0f, Y(0.030f),
                new int[]{0x00FFFFFF, 0x21FFFFFF, 0x12FFFFFF,
                        night ? 0x57000000 : 0x4D000000,
                        night ? 0x9E000000 : 0x85000000},
                new float[]{0f, 0.30f, 0.62f, 0.86f, 1f}, Shader.TileMode.CLAMP));
        cv.drawPaint(shadePaint);
        shadePaint.setShader(null);

        // 贴地窄反光
        shadePaint.setShader(new LinearGradient(0f, Y(0.062f), 0f, Y(0.040f),
                new int[]{0x00FFFFFF, 0x38FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        cv.drawRect(X(0.255f), Y(0.062f), X(0.735f), Y(0.040f), shadePaint);
        shadePaint.setShader(null);

        // 轮眉内缘明暗
        for (int i = 0; i < 2; i++) {
            float cx = i == 0 ? X(FRONT_U) : X(REAR_U);
            float rr = i == 0 ? ARCH_F : ARCH_R;
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeWidth(sw(0.0039f));
            linePaint.setColor(0x57FFFFFF);
            tmp.set(cx - carW * rr * 1.016f, Y(AXLE_H) - carW * rr * 1.016f,
                    cx + carW * rr * 1.016f, Y(AXLE_H) + carW * rr * 1.016f);
            cv.drawArc(tmp, 183.6f, 172.8f, false, linePaint);

            linePaint.setStrokeWidth(sw(0.0060f));
            linePaint.setColor(0x8C000000);
            tmp.set(cx - carW * rr * 0.980f, Y(AXLE_H) - carW * rr * 0.980f,
                    cx + carW * rr * 0.980f, Y(AXLE_H) + carW * rr * 0.980f);
            cv.drawArc(tmp, 183.6f, 172.8f, false, linePaint);
        }

        // 掠光
        float swid = carW * 0.30f;
        float sx = X(-0.50f + phase * 1.90f);
        sweepPaint.setShader(new LinearGradient(sx, 0f, sx + swid, 0f,
                new int[]{0x00FFFFFF, a(cOk, 46), 0x00FFFFFF},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        cv.drawPaint(sweepPaint);
        sweepPaint.setShader(null);
        linePaint.setStyle(Paint.Style.STROKE);

        // 车顶轮廓光
        linePaint.setStrokeWidth(sw(0.0141f));
        linePaint.setShader(new LinearGradient(X(0f), 0f, X(1f), 0f,
                new int[]{0x00FFFFFF, 0x85FFFFFF, 0x42FFFFFF, 0x38FFFFFF, 0x4DFFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.06f, 0.46f, 0.80f, 0.93f, 1f}, Shader.TileMode.CLAMP));
        cv.drawPath(topEdgePath, linePaint);

        linePaint.setStrokeWidth(sw(0.0035f));
        linePaint.setShader(new LinearGradient(X(0f), 0f, X(1f), 0f,
                new int[]{0x00FFFFFF, 0xF0FFFFFF, 0x80FFFFFF, 0x52FFFFFF, 0x8FFFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.06f, 0.46f, 0.80f, 0.93f, 1f}, Shader.TileMode.CLAMP));
        cv.drawPath(topEdgePath, linePaint);
        linePaint.setShader(null);

        cv.restore();

        // 肩线本体
        linePaint.setStrokeWidth(sw(0.0028f));
        linePaint.setShader(new LinearGradient(X(0.04f), 0f, X(0.96f), 0f,
                new int[]{0x00FFFFFF, 0xE6FFFFFF, 0x99FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.10f, 0.72f, 1f}, Shader.TileMode.CLAMP));
        cv.drawPath(creasePath, linePaint);
        linePaint.setShader(null);

        linePaint.setStrokeWidth(sw(0.0033f));
        linePaint.setColor(night ? 0x1AFFFFFF : 0x29FFFFFF);
        cv.save();
        cv.translate(0f, sw(0.0120f));
        cv.drawPath(creasePath, linePaint);
        cv.restore();
    }

    private void drawGlass(Canvas cv) {
        glassPaint.setShader(new LinearGradient(0f, Y(0.282f), 0f, Y(0.186f),
                new int[]{0xFF9FB0C2, 0xFF2C353F, 0xFF0B0F14, 0xFF05080B},
                new float[]{0f, 0.28f, 0.70f, 1f}, Shader.TileMode.CLAMP));
        cv.drawPath(glassPath, glassPaint);
        glassPaint.setShader(null);

        cv.save();
        cv.clipPath(glassPath);

        float gx = X(0.40f + phase * 0.16f);
        glassPaint.setShader(new LinearGradient(gx, Y(0.282f), gx + carW * 0.20f, Y(0.186f),
                new int[]{0x00FFFFFF, 0x70FFFFFF, 0x1FFFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.40f, 0.54f, 1f}, Shader.TileMode.CLAMP));
        cv.drawPaint(glassPaint);

        glassPaint.setShader(new LinearGradient(0f, Y(0.282f), 0f, Y(0.244f),
                0x52D6EAFF, 0x00D6EAFF, Shader.TileMode.CLAMP));
        cv.drawPaint(glassPaint);
        glassPaint.setShader(null);
        cv.restore();

        // 窗框：外圈深色收边，内侧两道冷银镀铬
        linePaint.setStrokeWidth(sw(0.0037f));
        linePaint.setColor(0xFF1C2026);
        cv.drawPath(glassPath, linePaint);
        linePaint.setStrokeWidth(sw(0.0021f));
        linePaint.setColor(0xFF8C97A6);
        cv.drawPath(glassPath, linePaint);
        linePaint.setStrokeWidth(sw(0.0013f));
        linePaint.setColor(0xEBE8F0FA);
        cv.drawPath(glassPath, linePaint);

        // B 柱（亮黑）
        linePaint.setStrokeWidth(sw(0.0120f));
        linePaint.setColor(0xFF14171C);
        cv.drawLine(X(0.538f), Y(0.276f), X(0.534f), Y(0.192f), linePaint);
        linePaint.setStrokeWidth(sw(0.0015f));
        linePaint.setColor(0x29FFFFFF);
        cv.drawLine(X(0.536f), Y(0.276f), X(0.532f), Y(0.192f), linePaint);
    }

    private void drawDetails(Canvas cv) {
        // 门缝
        linePaint.setStrokeWidth(sw(0.0026f));
        linePaint.setColor(night ? 0x70000000 : 0x57000000);
        cv.drawPath(doorPath, linePaint);
        cv.drawPath(doorPath2, linePaint);
        cv.drawPath(doorPath3, linePaint);

        linePaint.setStrokeWidth(sw(0.0014f));
        linePaint.setColor(0x38FFFFFF);
        cv.save();
        cv.translate(sw(0.0024f), 0f);
        cv.drawPath(doorPath, linePaint);
        cv.drawPath(doorPath2, linePaint);
        cv.drawPath(doorPath3, linePaint);
        cv.restore();

        // 门把手
        bodyPaint.setShader(null);
        bodyPaint.setColor(0xFFC9CED6);
        for (int i = 0; i < 2; i++) {
            float hu = i == 0 ? 0.446f : 0.660f;
            float hh = i == 0 ? 0.184f : 0.188f;
            detailRect.set(X(hu), Y(hh), X(hu) + carW * 0.040f, Y(hh) + carW * 0.0080f);
            cv.drawRoundRect(detailRect, dp(2f), dp(2f), bodyPaint);
        }
        linePaint.setStrokeWidth(sw(0.0020f));
        linePaint.setColor(0x57000000);
        for (int i = 0; i < 2; i++) {
            float hu = i == 0 ? 0.446f : 0.660f;
            float hh = i == 0 ? 0.184f : 0.188f;
            detailRect.set(X(hu), Y(hh) + carW * 0.0028f,
                    X(hu) + carW * 0.040f, Y(hh) + carW * 0.0108f);
            cv.drawRoundRect(detailRect, dp(2f), dp(2f), linePaint);
        }

        // 后视镜
        Paint mg = shadePaint;
        mg.setShader(new LinearGradient(X(0.33f), Y(0.216f), X(0.39f), Y(0.188f),
                mix(cText, 0xFFFFFFFF, night ? 0.34f : 0.24f),
                mix(cText, 0xFF000000, 0.55f), Shader.TileMode.CLAMP));
        cv.drawPath(mirrorPath, mg);
        mg.setShader(null);
        linePaint.setStrokeWidth(sw(0.0015f));
        linePaint.setColor(0x73FFFFFF);
        cv.drawPath(mirrorPath, linePaint);

        bodyPaint.setColor(0x4D000000);
        detailRect.set(X(0.352f) - carW * 0.016f, Y(0.181f) - carW * 0.0058f,
                X(0.352f) + carW * 0.016f, Y(0.181f) + carW * 0.0058f);
        cv.drawOval(detailRect, bodyPaint);

        // 前翼子板侧鳃
        bodyPaint.setColor(0xFF0A0B0D);
        detailRect.set(X(0.234f), Y(0.092f),
                X(0.234f) + carW * 0.013f, Y(0.092f) + carW * 0.046f);
        cv.drawRoundRect(detailRect, dp(2.5f), dp(2.5f), bodyPaint);
        linePaint.setStrokeWidth(sw(0.0017f));
        linePaint.setColor(0x33FFFFFF);
        cv.drawRoundRect(detailRect, dp(2.5f), dp(2.5f), linePaint);

        // 黑色裙边
        linePaint.setStrokeWidth(sw(0.0076f));
        linePaint.setColor(0xFF101114);
        cv.drawLine(X(0.262f), Y(0.033f), X(0.690f), Y(0.030f), linePaint);
        linePaint.setStrokeWidth(sw(0.0022f));
        linePaint.setColor(0x38FFFFFF);
        cv.drawLine(X(0.264f), Y(0.041f), X(0.688f), Y(0.038f), linePaint);

        // 前脸
        linePaint.setStrokeWidth(sw(0.0060f));
        linePaint.setColor(0xFF15161A);
        cv.drawLine(X(0.004f), Y(0.038f), X(0.080f), Y(0.038f), linePaint);

        bodyPaint.setColor(0xFF0A0B0D);
        detailRect.set(X(0.006f), Y(0.062f),
                X(0.006f) + carW * 0.052f, Y(0.062f) + carW * 0.040f);
        cv.drawRoundRect(detailRect, dp(4.5f), dp(4.5f), bodyPaint);
        detailRect.set(X(0.066f), Y(0.070f),
                X(0.066f) + carW * 0.030f, Y(0.070f) + carW * 0.032f);
        cv.drawRoundRect(detailRect, dp(4f), dp(4f), bodyPaint);

        bodyPaint.setColor(0xFF282A30);
        detailRect.set(X(0.010f), Y(0.058f),
                X(0.010f) + carW * 0.044f, Y(0.058f) + carW * 0.008f);
        cv.drawRoundRect(detailRect, dp(2f), dp(2f), bodyPaint);
    }

    private void drawLights(Canvas cv) {
        int glowA = locked ? 90 : (int) (255 * lightPulse);

        // 大灯
        bodyPaint.setShader(null);
        bodyPaint.setColor(night ? 0x38C8FFE8 : 0x99E8F2FF);
        cv.drawPath(headPath, bodyPaint);
        linePaint.setStrokeWidth(sw(0.0028f));
        linePaint.setColor(a(cOk, 150));
        cv.drawPath(headPath, linePaint);

        bodyPaint.setColor(0xF7FFFFFF);
        for (int i = 0; i < 4; i++) {
            float u = 0.026f + i * 0.019f;
            float hh = 0.141f + i * 0.0068f;
            float px = X(u), py = Y(hh);
            cv.save();
            cv.rotate(-19.5f, px, py);
            detailRect.set(px - carW * 0.0064f, py - carW * 0.0044f,
                    px + carW * 0.0064f, py + carW * 0.0044f);
            cv.drawOval(detailRect, bodyPaint);
            cv.restore();
        }

        if (!locked) {
            shadowPaint.setShader(new RadialGradient(X(0.060f), Y(0.150f), carW * 0.14f,
                    a(cOk, (int) (70 * lightPulse)), 0x00000000, Shader.TileMode.CLAMP));
            cv.drawCircle(X(0.060f), Y(0.150f), carW * 0.14f, shadowPaint);
            shadowPaint.setShader(null);
        }

        // 尾部贯穿灯带
        int tailA = locked ? 130 : (int) (255 * lightPulse);
        linePaint.setStrokeWidth(carW * 0.0074f);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setColor(a(cOk, (int) (tailA * 0.28f)));
        cv.drawPath(tailPath, linePaint);
        bodyPaint.setShader(new LinearGradient(X(0.850f), Y(0.192f), X(0.993f), Y(0.176f),
                new int[]{0xFFB8323C, 0xFFE24A4A, 0xFFFF7B72},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        cv.drawPath(tailPath, bodyPaint);
        bodyPaint.setShader(null);
        linePaint.setStrokeWidth(carW * 0.0016f);
        linePaint.setColor(0x99FFFFFF);
        cv.drawPath(tailPath, linePaint);

        if (!locked) {
            shadowPaint.setShader(new RadialGradient(X(0.920f), Y(0.186f), carW * 0.12f,
                    a(cOk, (int) (60 * lightPulse)), 0x00000000, Shader.TileMode.CLAMP));
            cv.drawCircle(X(0.920f), Y(0.186f), carW * 0.12f, shadowPaint);
            shadowPaint.setShader(null);
        }

        // 车头灯带发光
        linePaint.setStrokeWidth(sw(0.0028f));
        linePaint.setColor(a(cOk, glowA));
        cv.drawPath(headPath, linePaint);

        // 鸭尾
        linePaint.setStrokeWidth(sw(0.0043f));
        linePaint.setColor(mix(cText, 0xFF000000, 0.35f));
        cv.drawLine(X(0.876f), Y(0.206f), X(0.982f), Y(0.180f), linePaint);
        linePaint.setStrokeWidth(sw(0.0015f));
        linePaint.setColor(0x4DFFFFFF);
        cv.drawLine(X(0.876f), Y(0.211f), X(0.982f), Y(0.185f), linePaint);

        // 四出排气：左右各两个
        float[] exhaustU = {0.876f, 0.898f, 0.936f, 0.958f};
        for (float eu : exhaustU) {
            bodyPaint.setColor(0xFF111215);
            detailRect.set(X(eu), Y(0.052f),
                    X(eu) + carW * 0.017f, Y(0.052f) + carW * 0.013f);
            cv.drawRoundRect(detailRect, dp(2f), dp(2f), bodyPaint);
            linePaint.setStrokeWidth(sw(0.0018f));
            linePaint.setColor(0xFF8E929B);
            cv.drawRoundRect(detailRect, dp(2f), dp(2f), linePaint);
        }

        // 扩散器
        linePaint.setStrokeWidth(sw(0.0054f));
        linePaint.setColor(0xFF15161A);
        cv.drawLine(X(0.858f), Y(0.036f), X(0.994f), Y(0.034f), linePaint);
    }
}
