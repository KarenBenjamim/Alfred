package com.alfred.agenda.util;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.alfred.agenda.R;
import com.alfred.agenda.data.entity.Tag;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

/**
 * Aparência de um cartão de tarefa calculada a partir das tags REAIS dela:
 *
 *  - sem tags: cartão grafite padrão (a faixa lateral mostra a prioridade);
 *  - uma tag: fundo escuro com um véu da cor da tag (mais forte à esquerda), borda e faixa na cor;
 *  - duas ou mais: degradê linear com TODAS as cores das tags (na ordem de TagPalette),
 *    em opacidade controlada sobre o fundo escuro; borda e faixa na cor da primeira.
 *
 * O texto continua claro por cima, porque o fundo nunca passa de ~35% de cor.
 * Na aba Pessoal os cartões não têm borda, para não atrapalhar o degradê das tags.
 */
public final class TaskAppearance {

    /** Opacidade das cores das tags sobre o fundo escuro (0x59 ≈ 35%, 0x1F ≈ 12%). */
    private static final int ALPHA_STRONG = 0x59;
    private static final int ALPHA_SOFT = 0x1F;
    private static final int ALPHA_BORDER = 0x8C;

    private TaskAppearance() {
    }

    /**
     * Pinta o cartão.
     *
     * @param content    o filho direto do cartão (recebe o fundo com a cor das tags)
     * @param strip      faixa lateral do cartão
     * @param stripColor cor da faixa quando não há tags (ex.: prioridade)
     * @param border     false = cartão sem borda (aba Pessoal)
     */
    public static void apply(MaterialCardView card, View content, View strip, List<Tag> tags, int stripColor,
                             boolean border) {
        Context ctx = card.getContext();
        card.setStrokeWidth(border ? Math.round(ctx.getResources().getDisplayMetrics().density) : 0);
        int base = ContextCompat.getColor(ctx, R.color.graphite_light);
        int[] indices = TagPalette.colorIndices(tags);

        if (indices.length == 0) {
            content.setBackground(null);
            card.setCardBackgroundColor(base);
            card.setStrokeColor(ContextCompat.getColor(ctx, R.color.outline));
            strip.setBackgroundColor(stripColor);
            return;
        }

        int[] colors = new int[indices.length];
        for (int i = 0; i < indices.length; i++) colors[i] = TagColors.color(ctx, indices[i]);
        int first = colors[0];

        int[] veil;
        if (colors.length == 1) {
            veil = new int[]{ColorUtils.setAlphaComponent(first, ALPHA_STRONG),
                    ColorUtils.setAlphaComponent(first, ALPHA_SOFT)};
        } else {
            veil = new int[colors.length];
            for (int i = 0; i < colors.length; i++) veil[i] = ColorUtils.setAlphaComponent(colors[i], ALPHA_STRONG);
        }

        float radius = card.getRadius();
        GradientDrawable dark = new GradientDrawable();
        dark.setColor(base);
        dark.setCornerRadius(radius);
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, veil);
        gradient.setCornerRadius(radius);

        content.setBackground(new LayerDrawable(new Drawable[]{dark, gradient}));
        card.setCardBackgroundColor(base);
        card.setStrokeColor(ColorUtils.setAlphaComponent(first, ALPHA_BORDER));
        strip.setBackgroundColor(first);
    }
}
