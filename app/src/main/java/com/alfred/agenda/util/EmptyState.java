package com.alfred.agenda.util;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.alfred.agenda.R;
import com.google.android.material.button.MaterialButton;

/**
 * Preenche o layout view_empty_state (EmptyState e ErrorState).
 * Use com o view raiz do include (id empty_state). Os textos recebem o tratamento (Honorific).
 */
public final class EmptyState {

    private EmptyState() {
    }

    /**
     * @param buttonText 0 = sem botão
     * @param action     ação do botão (só usada se buttonText != 0)
     */
    public static void show(View root, int icon, int title, int message, int buttonText,
                            @Nullable Runnable action) {
        ((ImageView) root.findViewById(R.id.empty_icon)).setImageResource(icon);
        ((TextView) root.findViewById(R.id.empty_title)).setText(Honorific.str(root.getContext(), title));
        ((TextView) root.findViewById(R.id.empty_message)).setText(Honorific.str(root.getContext(), message));
        MaterialButton button = root.findViewById(R.id.empty_action);
        if (buttonText != 0 && action != null) {
            button.setText(buttonText);
            button.setOnClickListener(v -> action.run());
            button.setVisibility(View.VISIBLE);
        } else {
            button.setVisibility(View.GONE);
        }
        root.setVisibility(View.VISIBLE);
    }

    /** Variante de erro: ícone de alerta em vermelho. */
    public static void showError(View root, int title, int message, int buttonText, Runnable retry) {
        show(root, R.drawable.ic_warning, title, message, buttonText, retry);
        ImageView icon = root.findViewById(R.id.empty_icon);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(root.getContext(), R.color.error)));
    }

    public static void hide(View root) {
        root.setVisibility(View.GONE);
    }
}
