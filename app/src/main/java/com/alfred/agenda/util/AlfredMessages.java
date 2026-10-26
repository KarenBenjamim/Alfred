package com.alfred.agenda.util;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.alfred.agenda.R;
import com.google.android.material.snackbar.Snackbar;

import java.util.Random;

/**
 * Mensagens discretas do Alfred (snackbar acima da barra inferior), mostradas SÓ depois
 * que uma ação foi gravada com sucesso. Não há IA: são frases fixas, com o tratamento
 * escolhido no perfil (Honorific).
 */
public final class AlfredMessages {

    private static final Random RANDOM = new Random();

    private AlfredMessages() {
    }

    /** Depois de concluir uma meta (tarefa, hábito, refeição). */
    public static String goalDone(Context ctx) {
        String[] options = {
                "Missão cumprida, {voc}!",
                "Parabéns, {voc}! Mais uma meta concluída.",
                "Excelente, {voc}. Uma a menos."
        };
        return Honorific.apply(ctx, options[RANDOM.nextInt(options.length)]);
    }

    /** Quando todas as metas do dia ficam concluídas. */
    public static String allGoalsDone(Context ctx) {
        return Honorific.apply(ctx, "Todas as metas de hoje foram concluídas! Estou quase emocionado, {voc}.");
    }

    /**
     * Mostra a mensagem acima da barra de navegação.
     *
     * @param anchor view acima da qual a snackbar aparece (a barra inferior); pode ser null
     */
    public static void show(Activity activity, String text, View anchor) {
        View root = activity.findViewById(android.R.id.content);
        if (root == null) return;
        Snackbar bar = Snackbar.make(root, text, Snackbar.LENGTH_SHORT);
        if (anchor != null && anchor.getVisibility() == View.VISIBLE) bar.setAnchorView(anchor);
        bar.setBackgroundTint(ContextCompat.getColor(activity, R.color.graphite_lighter));
        bar.setTextColor(ContextCompat.getColor(activity, R.color.text_primary));
        TextView tv = bar.getView().findViewById(com.google.android.material.R.id.snackbar_text);
        if (tv != null) {
            tv.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check_gold, 0, 0, 0);
            tv.setCompoundDrawablePadding(Math.round(10 * activity.getResources().getDisplayMetrics().density));
        }
        bar.show();
    }
}
