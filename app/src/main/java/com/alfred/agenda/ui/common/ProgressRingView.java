package com.alfred.agenda.ui.common;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.alfred.agenda.R;

/**
 * Anel de progresso (0 a 1): trilho grafite e arco dourado com pontas arredondadas.
 * Anima suavemente ao mudar, a menos que as animações estejam desligadas no sistema
 * (Configurações > Acessibilidade > Remover animações).
 */
public class ProgressRingView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private float progress = 0f;
    @Nullable
    private ValueAnimator animator;

    public ProgressRingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float stroke = 9 * getResources().getDisplayMetrics().density;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(stroke);
        track.setColor(ContextCompat.getColor(context, R.color.graphite_lighter));
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(stroke);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setColor(ContextCompat.getColor(context, R.color.highlight));
    }

    public void setRingColor(int color) {
        arc.setColor(color);
        invalidate();
    }

    public void setProgress(float value) {
        float target = Math.max(0f, Math.min(1f, value));
        if (animator != null) animator.cancel();
        if (!ValueAnimator.areAnimatorsEnabled() || !isLaidOut()) {
            progress = target;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(progress, target);
        animator.setDuration(500);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float half = track.getStrokeWidth() / 2f;
        bounds.set(half, half, getWidth() - half, getHeight() - half);
        canvas.drawArc(bounds, 0, 360, false, track);
        if (progress > 0f) canvas.drawArc(bounds, -90, 360 * progress, false, arc);
    }
}
