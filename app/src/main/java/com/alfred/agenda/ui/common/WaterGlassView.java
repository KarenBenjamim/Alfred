package com.alfred.agenda.ui.common;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.alfred.agenda.R;

/**
 * Copo de água que vai enchendo conforme o consumo do dia (0 = vazio, 1 = meta atingida).
 * Contorno sempre cinza claro.
 * A animação respeita a opção "remover animações" do sistema.
 */
public class WaterGlassView extends View {

    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint water = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint surface = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path glass = new Path();
    private final RectF bounds = new RectF();
    private float fraction;
    /** Tamanho para o qual o contorno foi montado (-1 = ainda não montado). */
    private int builtWidth = -1;
    private int builtHeight = -1;
    @Nullable
    private ValueAnimator animator;

    public WaterGlassView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(2.5f * density);
        outline.setStrokeJoin(Paint.Join.ROUND);
        int blue = ContextCompat.getColor(context, R.color.info);
        water.setColor(ColorUtils.setAlphaComponent(blue, 0xB3));
        surface.setStyle(Paint.Style.STROKE);
        surface.setStrokeWidth(2f * density);
        surface.setColor(blue);
        updateOutlineColor();
    }

    /** Define o nível (0 a 1), com animação curta. */
    public void setFraction(float value) {
        float target = Math.max(0f, Math.min(1f, value));
        if (animator != null) animator.cancel();
        if (!ValueAnimator.areAnimatorsEnabled() || !isLaidOut()) {
            fraction = target;
            updateOutlineColor();
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(fraction, target);
        animator.setDuration(450);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> invalidate());
        animator.start();
        fraction = target;
        updateOutlineColor();
    }

    private void updateOutlineColor() {
        // Contorno sempre cinza claro (cheio ou vazio)
        outline.setColor(ContextCompat.getColor(getContext(), R.color.accent));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        buildGlass(w, h);
    }

    /** Monta o contorno do copo para o tamanho atual. */
    private void buildGlass(int w, int h) {
        builtWidth = w;
        builtHeight = h;
        float inset = outline.getStrokeWidth();
        bounds.set(getPaddingLeft() + inset, getPaddingTop() + inset,
                w - getPaddingRight() - inset, h - getPaddingBottom() - inset);
        // Copo: boca mais larga que o fundo, fundo levemente arredondado
        float taper = bounds.width() * 0.14f;
        float r = Math.min(bounds.width() * 0.12f, bounds.height() * 0.1f);
        glass.reset();
        glass.moveTo(bounds.left, bounds.top);
        glass.lineTo(bounds.right, bounds.top);
        glass.lineTo(bounds.right - taper, bounds.bottom - r);
        glass.quadTo(bounds.right - taper, bounds.bottom, bounds.right - taper - r, bounds.bottom);
        glass.lineTo(bounds.left + taper + r, bounds.bottom);
        glass.quadTo(bounds.left + taper, bounds.bottom, bounds.left + taper, bounds.bottom - r);
        glass.close();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Garante o contorno mesmo se o desenho vier antes de onSizeChanged (ou o tamanho mudar)
        if (getWidth() != builtWidth || getHeight() != builtHeight) buildGlass(getWidth(), getHeight());
        float shown = animator != null && animator.isRunning() ? (float) animator.getAnimatedValue() : fraction;
        if (shown > 0f) {
            float level = bounds.bottom - bounds.height() * shown;
            canvas.save();
            canvas.clipPath(glass);
            canvas.drawRect(bounds.left - 1, level, bounds.right + 1, bounds.bottom + 1, water);
            canvas.drawLine(bounds.left, level, bounds.right, level, surface);
            canvas.restore();
        }
        canvas.drawPath(glass, outline);
    }
}
