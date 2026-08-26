package com.localphoto360.app.capture;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class CaptureHudView extends View {
    private final Paint goldStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint whiteStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private PhotosphereSession session;
    private PhotosphereSession.CaptureTarget target;
    private float yawDeg;
    private float pitchDeg;
    private boolean aligned;
    private boolean capturing;

    public CaptureHudView(Context context) {
        this(context, null);
    }

    public CaptureHudView(Context context, AttributeSet attrs) {
        super(context, attrs);
        goldStroke.setStyle(Paint.Style.STROKE);
        goldStroke.setStrokeWidth(dp(3));
        goldStroke.setColor(Color.parseColor("#F4C95D"));
        goldStroke.setStrokeCap(Paint.Cap.ROUND);
        whiteStroke.setStyle(Paint.Style.STROKE);
        whiteStroke.setStrokeWidth(dp(3));
        whiteStroke.setColor(Color.WHITE);
        fill.setStyle(Paint.Style.FILL);
    }

    public void update(
            PhotosphereSession session,
            PhotosphereSession.CaptureTarget target,
            float yawDeg,
            float pitchDeg,
            boolean aligned,
            boolean capturing
    ) {
        this.session = session;
        this.target = target;
        this.yawDeg = yawDeg;
        this.pitchDeg = pitchDeg;
        this.aligned = aligned;
        this.capturing = capturing;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        canvas.drawCircle(cx, cy, dp(22), whiteStroke);
        canvas.drawLine(cx - dp(36), cy, cx - dp(18), cy, goldStroke);
        canvas.drawLine(cx + dp(18), cy, cx + dp(36), cy, goldStroke);
        canvas.drawLine(cx, cy - dp(36), cx, cy - dp(18), goldStroke);
        canvas.drawLine(cx, cy + dp(18), cx, cy + dp(36), goldStroke);

        if (session != null && target != null) {
            float yawOff = session.headingOffsetDeg(yawDeg, target.yawDeg);
            float pitchOff = session.pitchOffsetDeg(pitchDeg, target.pitchDeg);
            float mx = cx + (yawOff / 90f) * getWidth() * 0.42f;
            float my = cy - (pitchOff / 60f) * getHeight() * 0.32f;
            float radius = aligned ? dp(28) : dp(18);
            goldStroke.setStrokeWidth(dp(4));
            canvas.drawCircle(mx, my, radius, goldStroke);
            if (aligned || capturing) {
                fill.setColor(0x59F4C95D);
                canvas.drawCircle(mx, my, radius * 0.55f, fill);
            }
        }

        if (session == null) return;
        float stripTop = getHeight() * 0.18f;
        for (PhotosphereSession.CaptureTarget item : session.targets) {
            float x = (item.yawDeg / 360f) * getWidth();
            float y = stripTop + ((40f - item.pitchDeg) / 80f) * dp(48);
            fill.setColor(item.captured ? Color.parseColor("#F4C95D") : 0x59FFFFFF);
            canvas.drawCircle(x, y, dp(6), fill);
        }
        float compassX = (yawDeg / 360f) * getWidth();
        whiteStroke.setStrokeWidth(dp(2));
        canvas.drawLine(compassX, stripTop - dp(8), compassX, stripTop + dp(56), whiteStroke);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
