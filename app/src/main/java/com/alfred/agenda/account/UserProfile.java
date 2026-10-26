package com.alfred.agenda.account;

import android.content.Context;
import android.content.SharedPreferences;

import com.alfred.agenda.util.Honorific;

/**
 * Perfil do usuário logado: nome, forma de tratamento, peso e ícone de perfil.
 * Fica nas preferências privadas do app, um arquivo por conta ("alfred_profile_u<id>").
 * "onboarded" diz se o primeiro acesso (onboarding) já foi concluído.
 */
public final class UserProfile {

    private static final String KEY_NAME = "name";
    private static final String KEY_FORM = "form";
    private static final String KEY_WEIGHT = "weight_kg";
    private static final String KEY_AVATAR = "avatar";
    private static final String KEY_ONBOARDED = "onboarded";

    public String name = "";
    public int form = Honorific.FORM_NAME;
    public float weightKg;
    /** Posição em Avatars.DRAWABLES (o primeiro é o padrão). */
    public int avatar;
    public boolean onboarded;

    private static SharedPreferences prefs(Context ctx, int userId) {
        return ctx.getApplicationContext().getSharedPreferences("alfred_profile_u" + userId, Context.MODE_PRIVATE);
    }

    /** Perfil de quem está logado (vazio se ninguém estiver). */
    public static UserProfile current(Context ctx) {
        UserProfile p = new UserProfile();
        int id = AccountStore.currentUserId(ctx);
        if (id == 0) return p;
        SharedPreferences sp = prefs(ctx, id);
        p.name = sp.getString(KEY_NAME, "");
        p.form = Honorific.safeForm(sp.getInt(KEY_FORM, Honorific.FORM_NAME));
        p.weightKg = sp.getFloat(KEY_WEIGHT, 0f);
        p.avatar = sp.getInt(KEY_AVATAR, 0);
        p.onboarded = sp.getBoolean(KEY_ONBOARDED, false);
        return p;
    }

    /** Grava o perfil do usuário logado. Devolve false se a gravação falhar. */
    public boolean save(Context ctx) {
        int id = AccountStore.currentUserId(ctx);
        if (id == 0) return false;
        return prefs(ctx, id).edit()
                .putString(KEY_NAME, Honorific.normalizeName(name))
                .putInt(KEY_FORM, Honorific.safeForm(form))
                .putFloat(KEY_WEIGHT, weightKg)
                .putInt(KEY_AVATAR, avatar)
                .putBoolean(KEY_ONBOARDED, onboarded)
                .commit();
    }

    public UserProfile copy() {
        UserProfile p = new UserProfile();
        p.name = name;
        p.form = form;
        p.weightKg = weightKg;
        p.avatar = avatar;
        p.onboarded = onboarded;
        return p;
    }

    /**
     * Peso que o app antigo guardava na calculadora de água da Dieta (antes do perfil existir).
     * Usado só para já sugerir o peso no onboarding de quem atualizou o app.
     */
    public static float legacyWaterWeight(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences("alfred_prefs", Context.MODE_PRIVATE)
                .getFloat("water_weight_kg", 0f);
    }
}
