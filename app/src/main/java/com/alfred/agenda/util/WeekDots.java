package com.alfred.agenda.util;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.alfred.agenda.R;

import java.util.function.LongConsumer;
import java.util.function.LongPredicate;

/**
 * Desenha uma linha com os 7 dias da semana (Seg..Dom), cada um com uma bolinha:
 *  - amarela e cheia: concluído naquele dia;
 *  - contorno cinza: não concluído;
 *  - contorno apagado: dia que ainda não chegou;
 *  - o dia de hoje tem o rótulo destacado e um contorno mais forte.
 *
 * Usado na aba Hábitos (uma linha por hábito) e na aba Hoje (todos os hábitos do dia).
 */
public final class WeekDots {

    private static final int DOT_DP = 20;

    private WeekDots() {
    }

    /**
     * @param container  LinearLayout horizontal onde a linha será desenhada (é limpo antes)
     * @param weekStart  segunda-feira da semana, em epochDay
     * @param today      dia de hoje, em epochDay
     * @param isDone     diz se um dia (epochDay) está concluído
     * @param onDayClick chamado ao tocar numa bolinha de hoje ou de um dia passado (null = sem toque)
     */
    public static void render(LinearLayout container, long weekStart, long today,
                              LongPredicate isDone, @Nullable LongConsumer onDayClick) {
        Context ctx = container.getContext();
        container.removeAllViews();
        String[] labels = ctx.getResources().getStringArray(R.array.weekday_short);
        int dotSize = dp(ctx, DOT_DP);

        for (int i = 0; i < 7; i++) {
            long day = weekStart + i;
            boolean done = isDone.test(day);
            boolean isToday = day == today;
            boolean future = day > today;

            LinearLayout cell = new LinearLayout(ctx);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(0, dp(ctx, 4), 0, dp(ctx, 4));
            container.addView(cell, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView label = new TextView(ctx);
            label.setText(labels[i]);
            label.setTextSize(11);
            label.setGravity(Gravity.CENTER);
            label.setTextColor(ContextCompat.getColor(ctx, isToday ? R.color.text_primary : R.color.text_secondary));
            if (isToday) label.setTypeface(label.getTypeface(), Typeface.BOLD);
            cell.addView(label);

            View dot = new View(ctx);
            dot.setBackground(dotDrawable(ctx, done, isToday, future));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dotSize, dotSize);
            lp.topMargin = dp(ctx, 4);
            cell.addView(dot, lp);

            if (onDayClick != null && !future) {
                cell.setOnClickListener(v -> onDayClick.accept(day));
            }
        }
    }

    private static GradientDrawable dotDrawable(Context ctx, boolean done, boolean isToday, boolean future) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        if (done) {
            d.setColor(ContextCompat.getColor(ctx, R.color.highlight));
        } else {
            d.setColor(0x00000000);
            int strokeColor = ContextCompat.getColor(ctx,
                    future ? R.color.graphite_lighter : isToday ? R.color.highlight : R.color.outline);
            d.setStroke(dp(ctx, isToday ? 2 : 1.5f), strokeColor);
        }
        return d;
    }

    private static int dp(Context ctx, float value) {
        return Math.round(value * ctx.getResources().getDisplayMetrics().density);
    }
}
