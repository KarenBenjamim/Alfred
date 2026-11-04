package com.alfred.agenda.util;

import android.content.Context;

import androidx.annotation.ColorInt;
import androidx.core.content.ContextCompat;

import com.alfred.agenda.R;
import com.alfred.agenda.data.entity.Tag;

import java.util.Collection;

/**
 * Cores das tags de Trabalho: o grupo define a família de cor e cada tag nova do grupo ganha
 * o próximo tom, para tags do mesmo grupo não ficarem idênticas.
 *   Projeto azul · Bot verde · Empresa laranja · Evento/Estudo roxo · Outros rosa
 * No tema claro os tons são mais fechados e no escuro mais abertos (values-night).
 */
public final class WorkTagColors {

    public static final int SHADES = 4;

    /** [grupo][tom], mesma ordem de Tag.GROUP_* e de R.array.work_tag_groups. */
    private static final int[][] COLORS = {
            {R.color.wtag_project_0, R.color.wtag_project_1, R.color.wtag_project_2, R.color.wtag_project_3},
            {R.color.wtag_bot_0, R.color.wtag_bot_1, R.color.wtag_bot_2, R.color.wtag_bot_3},
            {R.color.wtag_company_0, R.color.wtag_company_1, R.color.wtag_company_2, R.color.wtag_company_3},
            {R.color.wtag_event_0, R.color.wtag_event_1, R.color.wtag_event_2, R.color.wtag_event_3},
            {R.color.wtag_other_0, R.color.wtag_other_1, R.color.wtag_other_2, R.color.wtag_other_3},
    };

    private WorkTagColors() {
    }

    /** Grupo válido; qualquer outro valor vira "Outros". */
    public static int safeGroup(int group) {
        return group >= 0 && group < Tag.GROUP_COUNT ? group : Tag.GROUP_OTHER;
    }

    public static int safeShade(int shade) {
        return shade >= 0 && shade < SHADES ? shade : 0;
    }

    @ColorInt
    public static int color(Context ctx, int group, int shade) {
        return ContextCompat.getColor(ctx, COLORS[safeGroup(group)][safeShade(shade)]);
    }

    /** Cor que representa o grupo (o primeiro tom), para o seletor. */
    @ColorInt
    public static int groupColor(Context ctx, int group) {
        return color(ctx, group, 0);
    }

    /** Tom para uma tag nova no grupo: o menos usado entre as tags que já existem nele. */
    public static int nextShade(int group, Collection<Tag> existing, long ignoreTagId) {
        int[] used = new int[SHADES];
        for (Tag t : existing) {
            if (t.id != ignoreTagId && t.tagGroup == group) used[safeShade(t.colorIndex)]++;
        }
        int best = 0;
        for (int s = 1; s < SHADES; s++) if (used[s] < used[best]) best = s;
        return best;
    }
}
