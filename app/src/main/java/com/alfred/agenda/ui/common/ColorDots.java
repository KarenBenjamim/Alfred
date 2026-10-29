package com.alfred.agenda.ui.common;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import com.alfred.agenda.R;

/**
 * Fileira de bolinhas para escolher uma cor (tags, temas de estudo).
 * A escolhida ganha um anel claro em volta; o índice escolhido fica em selected[0].
 */
public final class ColorDots {

    private ColorDots() {
    }

    /**
     * @param colors cores já resolvidas (ARGB), uma por bolinha
     * @param names  nome de cada cor, para o leitor de tela
     */
    public static void render(LinearLayout container, int[] colors, String[] names, int[] selected) {
        Context ctx = container.getContext();
        container.removeAllViews();
        float density = ctx.getResources().getDisplayMetrics().density;
        int size = Math.round(30 * density);

        for (int i = 0; i < colors.length; i++) {
            int index = i;
            View dot = new View(ctx);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(colors[i]);
            if (i == selected[0]) {
                d.setStroke(Math.round(3 * density), ContextCompat.getColor(ctx, R.color.text_primary));
            }
            dot.setBackground(d);
            dot.setContentDescription(names[i]);
            dot.setOnClickListener(v -> {
                selected[0] = index;
                render(container, colors, names, selected);
            });

            LinearLayout cell = new LinearLayout(ctx);
            cell.setGravity(Gravity.CENTER);
            cell.addView(dot, new LinearLayout.LayoutParams(size, size));
            container.addView(cell, new LinearLayout.LayoutParams(0, Math.round(40 * density), 1f));
        }
    }
}
