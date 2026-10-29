package com.alfred.agenda.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Aparência do app: escuro (padrão, o visual original do Alfred), claro ou seguir o sistema.
 * Vale para o aparelho todo (não é por conta) e não é dado sensível, então fica em
 * SharedPreferences comuns.
 */
public final class ThemePrefs {

    // Valores batem com a posição em R.array.theme_options e são gravados nas preferências
    public static final int DARK = 0;
    public static final int LIGHT = 1;
    public static final int SYSTEM = 2;

    private static final String FILE = "alfred_appearance";
    private static final String KEY_MODE = "mode";

    private ThemePrefs() {
    }

    /** Valor inválido (ex.: preferência corrompida) volta para o escuro. */
    public static int safeMode(int mode) {
        return mode >= DARK && mode <= SYSTEM ? mode : DARK;
    }

    /** Converte a escolha para o modo noturno do AppCompat. */
    public static int nightMode(int mode) {
        switch (safeMode(mode)) {
            case LIGHT: return AppCompatDelegate.MODE_NIGHT_NO;
            case SYSTEM: return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            default: return AppCompatDelegate.MODE_NIGHT_YES;
        }
    }

    public static int get(Context ctx) {
        return safeMode(prefs(ctx).getInt(KEY_MODE, DARK));
    }

    /** Grava e aplica na hora (a tela aberta é recriada com o tema novo). */
    public static void set(Context ctx, int mode) {
        int m = safeMode(mode);
        prefs(ctx).edit().putInt(KEY_MODE, m).apply();
        AppCompatDelegate.setDefaultNightMode(nightMode(m));
    }

    /** Chamado ao iniciar o app, antes de qualquer tela. */
    public static void apply(Context ctx) {
        AppCompatDelegate.setDefaultNightMode(nightMode(get(ctx)));
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
