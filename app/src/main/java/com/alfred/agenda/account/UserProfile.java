package com.alfred.agenda.account;

import android.content.Context;
import android.content.SharedPreferences;

import com.alfred.agenda.util.AppLog;
import com.alfred.agenda.util.DataCipher;
import com.alfred.agenda.util.Honorific;

import java.security.GeneralSecurityException;

/**
 * Perfil do usuário logado: nome, forma de tratamento, peso e ícone de perfil.
 * Fica nas preferências privadas do app, um arquivo por conta ("alfred_profile_u<id>").
 * "onboarded" diz se o primeiro acesso (onboarding) já foi concluído.
 *
 * Nome e peso são dados pessoais: ficam cifrados (DataCipher, chave no Android Keystore).
 * Perfis gravados antes da criptografia são convertidos na primeira leitura.
 * O perfil decifrado fica em memória, porque é lido a cada texto da tela (forma de tratamento).
 */
public final class UserProfile {

    private static final String KEY_NAME = "name_enc";
    private static final String KEY_FORM = "form";
    private static final String KEY_WEIGHT = "weight_enc";
    /** Chaves em texto puro das versões antigas (só lidas para converter). */
    private static final String LEGACY_NAME = "name";
    private static final String LEGACY_WEIGHT = "weight_kg";
    private static final String KEY_AVATAR = "avatar";
    private static final String KEY_ONBOARDED = "onboarded";

    public String name = "";
    public int form = Honorific.FORM_NAME;
    public float weightKg;
    /** Posição em Avatars.DRAWABLES (o primeiro é o padrão). */
    public int avatar;
    public boolean onboarded;

    /** Último perfil lido/gravado e de qual conta ele é. */
    private static volatile UserProfile cached;
    private static volatile int cachedUser;

    private static SharedPreferences prefs(Context ctx, int userId) {
        return ctx.getApplicationContext().getSharedPreferences("alfred_profile_u" + userId, Context.MODE_PRIVATE);
    }

    /** Perfil de quem está logado (vazio se ninguém estiver). */
    public static UserProfile current(Context ctx) {
        int id = AccountStore.currentUserId(ctx);
        if (id == 0) return new UserProfile();
        UserProfile c = cached;
        if (c != null && cachedUser == id) return c.copy();

        UserProfile p = new UserProfile();
        SharedPreferences sp = prefs(ctx, id);
        p.form = Honorific.safeForm(sp.getInt(KEY_FORM, Honorific.FORM_NAME));
        p.avatar = sp.getInt(KEY_AVATAR, 0);
        p.onboarded = sp.getBoolean(KEY_ONBOARDED, false);
        boolean legacy = sp.contains(LEGACY_NAME) || sp.contains(LEGACY_WEIGHT);
        if (legacy) {
            p.name = sp.getString(LEGACY_NAME, "");
            p.weightKg = sp.getFloat(LEGACY_WEIGHT, 0f);
        } else {
            p.name = decrypt(sp.getString(KEY_NAME, null), "");
            p.weightKg = parseWeight(decrypt(sp.getString(KEY_WEIGHT, null), "0"));
        }
        cache(p, id);
        // Perfil antigo, em texto puro: grava cifrado e apaga as chaves antigas
        if (legacy) p.write(ctx, id);
        return p.copy();
    }

    /** Grava o perfil do usuário logado. Devolve false se a gravação falhar. */
    public boolean save(Context ctx) {
        int id = AccountStore.currentUserId(ctx);
        if (id == 0) return false;
        name = Honorific.normalizeName(name);
        form = Honorific.safeForm(form);
        if (!write(ctx, id)) return false;
        cache(this, id);
        return true;
    }

    /** Grava cifrado (nome e peso) e remove as chaves antigas em texto puro. */
    private boolean write(Context ctx, int id) {
        try {
            return prefs(ctx, id).edit()
                    .putString(KEY_NAME, DataCipher.encryptString(name))
                    .putInt(KEY_FORM, form)
                    .putString(KEY_WEIGHT, DataCipher.encryptString(Float.toString(weightKg)))
                    .putInt(KEY_AVATAR, avatar)
                    .putBoolean(KEY_ONBOARDED, onboarded)
                    .remove(LEGACY_NAME)
                    .remove(LEGACY_WEIGHT)
                    .commit();
        } catch (GeneralSecurityException e) {
            // Keystore indisponível: não grava em texto puro; quem chamou mostra o erro
            AppLog.e("Falha ao cifrar o perfil", e);
            return false;
        }
    }

    private static void cache(UserProfile p, int id) {
        cached = p.copy();
        cachedUser = id;
    }

    /** Esquece o perfil em memória (ao sair da conta). */
    public static void forget() {
        cached = null;
        cachedUser = 0;
    }

    private static String decrypt(String sealed, String fallback) {
        if (sealed == null) return fallback;
        try {
            return DataCipher.decryptString(sealed);
        } catch (GeneralSecurityException e) {
            // Chave perdida ou valor danificado: o app segue e o perfil pede os dados de novo
            AppLog.e("Falha ao decifrar o perfil", e);
            return fallback;
        }
    }

    private static float parseWeight(String text) {
        try {
            return Float.parseFloat(text);
        } catch (NumberFormatException e) {
            return 0f;
        }
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
