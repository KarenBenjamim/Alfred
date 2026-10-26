package com.alfred.agenda.util;

import android.content.Context;

import androidx.annotation.PluralsRes;
import androidx.annotation.StringRes;

import com.alfred.agenda.R;
import com.alfred.agenda.account.UserProfile;

import java.util.Locale;

/**
 * Forma de tratamento escolhida no perfil ("Senhor", "Senhora", "Senhorita" ou "Pelo nome").
 * É o ÚNICO lugar que sabe transformar o tratamento em texto.
 *
 * Os textos do app (strings.xml e as frases do Alfred) usam marcadores no lugar do tratamento:
 *  - {voc}  vocativo no meio da frase: "Anotado, {voc}." -> "Anotado, senhor." / "Anotado, Bruno."
 *  - {Voc}  vocativo no início da frase: "{Voc}, sua refeição..." -> "Senhor, sua refeição..."
 * As frases só usam o tratamento como vocativo (entre vírgulas), então nenhuma depende de gênero.
 *
 * Para exibir um texto, use str()/plural()/apply() em vez de getString(): assim, quando o
 * tratamento muda no perfil, a próxima tela desenhada já usa o novo.
 */
public final class Honorific {

    // Valores batem com a posição em R.array.honorific_options e são gravados no perfil
    public static final int FORM_SENHOR = 0;
    public static final int FORM_SENHORA = 1;
    public static final int FORM_SENHORITA = 2;
    public static final int FORM_NAME = 3;

    private static final String[] TITLES = {"senhor", "senhora", "senhorita"};

    public static final String TOKEN = "{voc}";
    public static final String TOKEN_CAPITALIZED = "{Voc}";

    private Honorific() {
    }

    // ===================== Regras puras (testadas em HonorificTest) =====================

    /** Forma inválida (ex.: perfil antigo) vira "Pelo nome". */
    public static int safeForm(int form) {
        return form >= FORM_SENHOR && form <= FORM_NAME ? form : FORM_NAME;
    }

    /** Vocativo no meio da frase: "senhor", "senhora", "senhorita" ou o nome. */
    public static String vocative(int form, String name) {
        int f = safeForm(form);
        return f == FORM_NAME ? name.trim() : TITLES[f];
    }

    /** Usado na saudação: "Senhor Bruno", "Senhorita Ana" ou só "Bruno". */
    public static String fullName(int form, String name) {
        int f = safeForm(form);
        String n = name.trim();
        if (f == FORM_NAME) return n;
        String title = capitalize(TITLES[f]);
        return n.isEmpty() ? title : title + " " + n;
    }

    /**
     * Troca os marcadores pelo tratamento. Se não houver tratamento (ex.: "Pelo nome" sem nome),
     * o vocativo some junto com a vírgula: "Anotado, {voc}." -> "Anotado."
     */
    public static String apply(String text, int form, String name) {
        if (text.indexOf('{') < 0) return text;
        String voc = vocative(form, name);
        if (voc.isEmpty()) {
            return text.replace(", " + TOKEN, "")
                    .replace(TOKEN_CAPITALIZED + ", ", "")
                    .replace(TOKEN, "")
                    .replace(TOKEN_CAPITALIZED, "");
        }
        return text.replace(TOKEN_CAPITALIZED, capitalize(voc)).replace(TOKEN, voc);
    }

    /** "bruno  de souza" -> "Bruno De Souza" (nomes são exibidos com inicial maiúscula). */
    public static String normalizeName(String name) {
        String[] words = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(capitalize(w));
        }
        return sb.toString();
    }

    private static String capitalize(String s) {
        if (s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase(new Locale("pt", "BR")) + s.substring(1);
    }

    // ===================== Atalhos com o perfil atual =====================

    /** Aplica o tratamento do usuário logado a um texto qualquer. */
    public static String apply(Context ctx, String text) {
        UserProfile p = UserProfile.current(ctx);
        return apply(text, p.form, p.name);
    }

    /** getString() já com o tratamento. */
    public static String str(Context ctx, @StringRes int res, Object... args) {
        return apply(ctx, args.length == 0 ? ctx.getString(res) : ctx.getString(res, args));
    }

    /** getQuantityString() já com o tratamento. */
    public static String plural(Context ctx, @PluralsRes int res, int quantity, Object... args) {
        return apply(ctx, ctx.getResources().getQuantityString(res, quantity, args));
    }

    /** "Bom dia, Senhor Bruno." conforme a hora (0 a 23). */
    public static String greeting(Context ctx, int hour) {
        UserProfile p = UserProfile.current(ctx);
        String who = fullName(p.form, p.name);
        int res;
        if (hour < 5) res = R.string.greeting_late;
        else if (hour < 12) res = R.string.greeting_morning;
        else if (hour < 18) res = R.string.greeting_afternoon;
        else res = R.string.greeting_evening;
        return ctx.getString(res, who);
    }
}
