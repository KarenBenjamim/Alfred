package com.alfred.agenda.util;

import android.content.Context;

import androidx.annotation.ColorInt;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.alfred.agenda.R;

/**
 * Cores que o usuário pode dar a uma matéria (tema de estudo). A cor pinta os assuntos ainda
 * não concluídos daquela matéria (faixa, borda e cronômetro) e a bolinha da aba; o resto da
 * tela de Estudos continua dourado.
 */
public final class StudyColors {

    /** Mesma ordem de R.array.study_color_names. */
    public static final int[] COLORS = {
            R.color.study_navy, R.color.study_petrol, R.color.study_moss, R.color.study_wine,
            R.color.study_terracotta, R.color.study_plum, R.color.study_rose, R.color.study_slate
    };

    /** Azul-marinho: cor das matérias antigas e das que não tiverem uma cor válida. */
    public static final int DEFAULT_INDEX = 0;

    /** Quanto da cor do texto entra no tom do cronômetro: legível sem virar neon. */
    private static final float TEXT_MIX = 0.45f;

    private StudyColors() {
    }

    /** Índice válido da paleta; qualquer valor fora da faixa vira o azul-marinho. */
    public static int safeIndex(int colorIndex) {
        return colorIndex >= 0 && colorIndex < COLORS.length ? colorIndex : DEFAULT_INDEX;
    }

    /** Cor da faixa e da borda. */
    @ColorInt
    public static int color(Context ctx, int colorIndex) {
        return ContextCompat.getColor(ctx, COLORS[safeIndex(colorIndex)]);
    }

    /**
     * Versão da cor para texto sobre o card (cronômetro rodando): misturada com a cor do texto
     * principal, fica mais clara no tema escuro e mais escura no claro, sempre legível.
     */
    @ColorInt
    public static int text(Context ctx, int colorIndex) {
        return ColorUtils.blendARGB(color(ctx, colorIndex), ContextCompat.getColor(ctx, R.color.text_primary),
                TEXT_MIX);
    }

    /** Todas as cores resolvidas, na ordem da paleta (para o seletor de bolinhas). */
    public static int[] all(Context ctx) {
        int[] colors = new int[COLORS.length];
        for (int i = 0; i < colors.length; i++) colors[i] = color(ctx, i);
        return colors;
    }
}
