package com.alfred.agenda.util;

import androidx.annotation.DrawableRes;

import com.alfred.agenda.R;

/**
 * Ícones de perfil (cópias de doc/img, sem o alfred.png que é o ícone do app).
 * A ordem precisa bater com R.array.avatar_names. O perfil guarda só a posição;
 * a primeira é o padrão de quem ainda não escolheu.
 */
public final class Avatars {

    public static final int[] DRAWABLES = {
            R.drawable.batcat_boy, R.drawable.batcat_girl, R.drawable.batdog_boy, R.drawable.batdog_girl
    };

    private Avatars() {
    }

    /** Posição válida (fora da faixa vira o primeiro avatar). */
    public static int safeIndex(int index) {
        return index >= 0 && index < DRAWABLES.length ? index : 0;
    }

    @DrawableRes
    public static int drawable(int index) {
        return DRAWABLES[safeIndex(index)];
    }
}
