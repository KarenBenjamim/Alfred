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
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.alfred.agenda.R;
import com.alfred.agenda.util.WaterGoal;

/**
 * Copo de água que vai enchendo conforme o consumo do dia (vazio até a meta, cheio na meta).
 * Contorno sempre cinza claro.
 *
 * Se o usuário registrar mais água e passar da meta, o copo transborda: a água sobe acima da
 * borda, escorre pelos lados e forma uma pocinha embaixo que some em seguida.
 * As animações respeitam a opção "remover animações" do sistema.
 */
public class WaterGlassView extends View {

    /** Espaço em volta do copo (dp) para a água que sobe, escorre e empoça. */
    private static final float ROOM_SIDE_DP = 4f;
    private static final float ROOM_TOP_DP = 6f;
    private static final float ROOM_BOTTOM_DP = 4f;
    private static final long OVERFLOW_MS = 1700;

    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint water = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint surface = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stream = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint drop = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path glass = new Path();
    private final Path dome = new Path();
    private final RectF bounds = new RectF();
    private final RectF puddle = new RectF();
    private final float density;
    private float fraction;
    /** Último total registrado (-1 = a tela acabou de abrir: o primeiro valor não transborda). */
    private int lastMl = -1;
    /** Tamanho para o qual o contorno foi montado (-1 = ainda não montado). */
    private int builtWidth = -1;
    private int builtHeight = -1;
    /** Inclinação das paredes e raio do fundo, guardados ao montar o contorno. */
    private float taper;
    private float corner;
    @Nullable
    private ValueAnimator animator;
    @Nullable
    private ValueAnimator overflow;

    public WaterGlassView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(2.5f * density);
        outline.setStrokeJoin(Paint.Join.ROUND);
        outline.setColor(ContextCompat.getColor(context, R.color.accent));
        int blue = ContextCompat.getColor(context, R.color.info);
        water.setColor(ColorUtils.setAlphaComponent(blue, 0xB3));
        surface.setStyle(Paint.Style.STROKE);
        surface.setStrokeWidth(2f * density);
        surface.setColor(blue);
        stream.setStyle(Paint.Style.STROKE);
        stream.setStrokeWidth(2.4f * density);
        stream.setStrokeCap(Paint.Cap.ROUND);
        stream.setColor(ColorUtils.setAlphaComponent(blue, 0xD9));
        drop.setColor(blue);
    }

    /**
     * Mostra o total do dia em relação à meta. Se o total aumentou e passou da meta,
     * o copo transborda.
     */
    public void setWater(int ml, int goalMl) {
        boolean spill = WaterGoal.overflows(lastMl, ml, goalMl);
        boolean wasFull = fraction >= 1f;
        lastMl = ml;
        setFraction(WaterGoal.fraction(ml, goalMl));
        if (spill) overflow(wasFull ? 0 : 300);
    }

    /** Define o nível (0 a 1), com animação curta. */
    public void setFraction(float value) {
        float target = Math.max(0f, Math.min(1f, value));
        if (animator != null) animator.cancel();
        if (!canAnimate()) {
            fraction = target;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(fraction, target);
        animator.setDuration(450);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> invalidate());
        animator.start();
        fraction = target;
    }

    /** Toca o transbordamento (depois de o copo terminar de encher, se ainda estava subindo). */
    private void overflow(long delayMs) {
        if (!canAnimate()) return;
        if (overflow != null) overflow.cancel();
        overflow = ValueAnimator.ofFloat(0f, 1f);
        overflow.setStartDelay(delayMs);
        overflow.setDuration(OVERFLOW_MS);
        overflow.setInterpolator(new LinearInterpolator()); // cada fase tem sua curva em onDraw
        overflow.addUpdateListener(a -> invalidate());
        overflow.start();
    }

    private boolean canAnimate() {
        return ValueAnimator.areAnimatorsEnabled() && isLaidOut();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        if (overflow != null) overflow.cancel();
        super.onDetachedFromWindow();
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
        float side = ROOM_SIDE_DP * density;
        bounds.set(getPaddingLeft() + inset + side, getPaddingTop() + inset + ROOM_TOP_DP * density,
                w - getPaddingRight() - inset - side, h - getPaddingBottom() - inset - ROOM_BOTTOM_DP * density);
        // Copo: boca mais larga que o fundo, fundo levemente arredondado
        taper = bounds.width() * 0.14f;
        corner = Math.min(bounds.width() * 0.12f, bounds.height() * 0.1f);
        glass.reset();
        glass.moveTo(bounds.left, bounds.top);
        glass.lineTo(bounds.right, bounds.top);
        glass.lineTo(bounds.right - taper, bounds.bottom - corner);
        glass.quadTo(bounds.right - taper, bounds.bottom, bounds.right - taper - corner, bounds.bottom);
        glass.lineTo(bounds.left + taper + corner, bounds.bottom);
        glass.quadTo(bounds.left + taper, bounds.bottom, bounds.left + taper, bounds.bottom - corner);
        glass.close();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Garante o contorno mesmo se o desenho vier antes de onSizeChanged (ou o tamanho mudar)
        if (getWidth() != builtWidth || getHeight() != builtHeight) buildGlass(getWidth(), getHeight());
        float shown = animator != null && animator.isRunning() ? (float) animator.getAnimatedValue() : fraction;
        boolean spilling = overflow != null && overflow.isRunning();
        float t = spilling ? (float) overflow.getAnimatedValue() : 0f;

        if (spilling) drawPuddle(canvas, t);
        if (shown > 0f) {
            float level = bounds.bottom - bounds.height() * shown;
            canvas.save();
            canvas.clipPath(glass);
            canvas.drawRect(bounds.left - 1, level, bounds.right + 1, bounds.bottom + 1, water);
            canvas.drawLine(bounds.left, level, bounds.right, level, surface);
            canvas.restore();
        }
        if (spilling) {
            drawDome(canvas, t);
            drawStream(canvas, t, true);
            drawStream(canvas, t, false);
            drawSplash(canvas, t);
        }
        canvas.drawPath(glass, outline);
    }

    // ---------------- Fases do transbordamento (t vai de 0 a 1) ----------------

    /** A água sobe acima da borda como uma cúpula e depois assenta. */
    private void drawDome(Canvas canvas, float t) {
        float rise = t < 0.12f ? smooth(t / 0.12f) : 1f - smooth((t - 0.12f) / 0.5f);
        if (rise <= 0f) return;
        float height = ROOM_TOP_DP * 0.8f * density * rise;
        float half = outline.getStrokeWidth() / 2f;
        dome.reset();
        dome.moveTo(bounds.left - half, bounds.top + half);
        dome.quadTo(bounds.centerX(), bounds.top - 2f * height, bounds.right + half, bounds.top + half);
        dome.close();
        canvas.drawPath(dome, water);
        canvas.drawPath(dome, surface);
    }

    /** Um filete escorre pela parede de fora, da borda até o fundo. */
    private void drawStream(Canvas canvas, float t, boolean left) {
        float head = smooth((t - 0.05f) / 0.45f);
        float tail = smooth((t - 0.35f) / 0.45f);
        if (head <= tail) return;
        // Parede: da borda (a) até o começo da curva do fundo (b), deslocada para fora
        float ax = left ? bounds.left : bounds.right;
        float bx = left ? bounds.left + taper : bounds.right - taper;
        float ay = bounds.top;
        float by = bounds.bottom - corner;
        float dx = bx - ax;
        float dy = by - ay;
        float len = (float) Math.hypot(dx, dy);
        float off = outline.getStrokeWidth() / 2f + stream.getStrokeWidth() / 2f;
        float nx = (left ? -dy : dy) / len * off;
        float ny = (left ? dx : -dx) / len * off;
        canvas.drawLine(ax + dx * tail + nx, ay + dy * tail + ny, ax + dx * head + nx, ay + dy * head + ny, stream);
    }

    /** Gotinhas pulam da borda no começo. */
    private void drawSplash(Canvas canvas, float t) {
        float p = t / 0.4f;
        if (p >= 1f) return;
        float r = 1.3f * density;
        drop.setAlpha(Math.round(255 * (1f - p)));
        for (int i = 0; i < 2; i++) {
            float reach = (ROOM_SIDE_DP - 1.5f + i) * density * p;
            float y = bounds.top - (5f - 2f * i) * density * (float) Math.sin(Math.PI * p) + 6f * density * p * p;
            canvas.drawCircle(bounds.left - reach, y, r, drop);
            canvas.drawCircle(bounds.right + reach, y, r, drop);
        }
    }

    /** Pocinha embaixo do copo: espalha quando os filetes chegam e depois seca. */
    private void drawPuddle(Canvas canvas, float t) {
        float grow = smooth((t - 0.4f) / 0.3f);
        if (grow <= 0f) return;
        float fade = 1f - smooth((t - 0.7f) / 0.3f);
        float minHalf = bounds.width() / 2f - taper;
        float maxHalf = bounds.width() / 2f + ROOM_SIDE_DP * density;
        float half = minHalf + (maxHalf - minHalf) * grow;
        float thick = ROOM_BOTTOM_DP * density * 0.9f;
        float bottom = bounds.bottom + outline.getStrokeWidth() / 2f + ROOM_BOTTOM_DP * density;
        puddle.set(bounds.centerX() - half, bottom - thick, bounds.centerX() + half, bottom);
        int alpha = water.getAlpha();
        water.setAlpha(Math.round(alpha * fade));
        canvas.drawOval(puddle, water);
        water.setAlpha(alpha);
    }

    /** Curva suave entre 0 e 1 (valores fora da faixa são limitados). */
    private static float smooth(float x) {
        float c = Math.max(0f, Math.min(1f, x));
        return c * c * (3f - 2f * c);
    }
}
