package com.alfred.agenda.util;

import android.content.Context;

import java.util.Random;

/**
 * A "voz" do Alfred: frases educadas, formais e com humor seco britânico.
 * Para dar mais personalidade ao app, basta acrescentar opções nas listas abaixo:
 * uma delas é sorteada a cada uso.
 *
 * O tratamento entra pelos marcadores {voc}/{Voc}, trocados por Honorific (por isso
 * os métodos recebem um Context). As frases usam o tratamento só como vocativo, para
 * servirem a qualquer forma escolhida ("senhor", "senhora", "senhorita" ou o nome).
 */
public final class AlfredPhrases {

    private static final Random RANDOM = new Random();

    private AlfredPhrases() {
    }

    private static String pick(Context ctx, String... options) {
        return Honorific.apply(ctx, options[RANDOM.nextInt(options.length)]);
    }

    // ---------- Notificações ----------

    public static String mealTitle(String mealName) {
        return "Refeição: " + mealName;
    }

    public static String mealReminder(Context ctx, String mealName) {
        return String.format(DateUtils.PT_BR, pick(ctx,
                "{Voc}, sua refeição aguarda: %s.",
                "Perdoe a interrupção, {voc}, mas chegou a hora de: %s. A nutricionista não aprovaria atrasos.",
                "Está servido: %s. Sugiro não deixar esfriar, {voc}. Nem pular.",
                "{Voc}, %s. Nem mesmo em Gotham se enfrenta o dia de estômago vazio."), mealName);
    }

    public static String workTaskTitle(String title) {
        return "Entrega hoje: " + title;
    }

    public static String workTaskReminder(Context ctx, String title) {
        return String.format(DateUtils.PT_BR, pick(ctx,
                "{Voc}, a entrega de \"%s\" é hoje. Recomendo começar antes que o café esfrie.",
                "Lembrete discreto: \"%s\" vence hoje. Eu cuidaria disso, mas não tenho acesso ao seu e-mail.",
                "\"%s\" vence hoje, {voc}. O prazo, receio, não aceita desculpas."), title);
    }

    public static String personalTaskTitle(String title) {
        return "Hoje: " + title;
    }

    public static String personalTaskReminder(Context ctx, String title) {
        return String.format(DateUtils.PT_BR, pick(ctx,
                "{Voc}, não se esqueça: \"%s\" é para hoje.",
                "Um lembrete pessoal, {voc}: \"%s\". Prometo não comentar com ninguém.",
                "\"%s\" está na agenda de hoje, {voc}. Tomei a liberdade de avisar."), title);
    }

    // ---------- Respostas rápidas (toasts) ----------

    public static String saved(Context ctx) {
        return pick(ctx, "Anotado, {voc}.", "Considere feito, {voc}.", "Devidamente registrado.");
    }

    public static String deleted(Context ctx) {
        return pick(ctx, "Removido, {voc}. Como se nunca tivesse existido.", "Feito. Não tocaremos mais no assunto.");
    }
}
