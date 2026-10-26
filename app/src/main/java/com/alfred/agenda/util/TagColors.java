package com.alfred.agenda.util;

import android.content.Context;
import android.content.res.ColorStateList;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.alfred.agenda.R;
import com.google.android.material.chip.Chip;

/**
 * Paleta de 8 cores das tags. A tag guarda só o índice (0 a 7), então dá para trocar
 * os tons em colors.xml sem mexer no banco.
 */
public final class TagColors {

    public static final int[] COLORS = {
            R.color.tag_lavender, R.color.tag_pink, R.color.tag_coral, R.color.tag_orange,
            R.color.tag_mint, R.color.tag_green, R.color.tag_blue, R.color.tag_gray
    };

    private TagColors() {
    }

    public static int color(Context ctx, int index) {
        return ContextCompat.getColor(ctx, COLORS[TagPalette.safeIndex(index)]);
    }

    /**
     * Pinta um chip com a cor da tag.
     *  - Selecionável (no formulário): contorno e texto na cor; marcado = fundo cheio na cor.
     *  - Só exibição (no cartão): fundo bem suave na cor e texto na cor.
     */
    public static void style(Chip chip, int index, boolean selectable) {
        Context ctx = chip.getContext();
        int color = color(ctx, index);
        int dark = ContextCompat.getColor(ctx, R.color.on_accent);
        int soft = ColorUtils.setAlphaComponent(color, 0x33);
        int[][] states = {{android.R.attr.state_checked}, {}};

        chip.setChipStrokeWidth(ctx.getResources().getDisplayMetrics().density);
        chip.setChipStrokeColor(ColorStateList.valueOf(color));
        if (selectable) {
            chip.setChipBackgroundColor(new ColorStateList(states, new int[]{color, 0x00000000}));
            chip.setTextColor(new ColorStateList(states, new int[]{dark, color}));
        } else {
            chip.setChipBackgroundColor(ColorStateList.valueOf(soft));
            chip.setTextColor(color);
        }
    }
}
