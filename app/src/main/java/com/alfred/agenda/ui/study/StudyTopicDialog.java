package com.alfred.agenda.ui.study;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.alfred.agenda.R;
import com.alfred.agenda.data.entity.StudyTopic;
import com.alfred.agenda.ui.common.ColorDots;
import com.alfred.agenda.util.Honorific;
import com.alfred.agenda.util.StudyColors;
import com.alfred.agenda.util.UiUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

/** Diálogo para criar/editar uma matéria (tema de estudo): nome + cor dos assuntos a estudar. */
final class StudyTopicDialog {

    interface OnSave {
        void save(String name, int colorIndex);
    }

    private StudyTopicDialog() {
    }

    /** @param existing null para criar (começa no azul-marinho) */
    static void show(Context ctx, @Nullable StudyTopic existing, OnSave onSave) {
        View view = LayoutInflater.from(ctx).inflate(R.layout.dialog_study_topic, null);
        TextInputLayout nameLayout = view.findViewById(R.id.layout_name);
        EditText nameInput = view.findViewById(R.id.input_name);
        LinearLayout colors = view.findViewById(R.id.colors);

        nameInput.setText(existing == null ? "" : existing.name);
        int[] selected = {existing == null ? StudyColors.DEFAULT_INDEX : StudyColors.safeIndex(existing.colorIndex)};
        ColorDots.render(colors, StudyColors.all(ctx), ctx.getResources().getStringArray(R.array.study_color_names),
                selected);

        AlertDialog dialog = new MaterialAlertDialogBuilder(ctx)
                .setTitle(existing == null ? R.string.study_topic_new : R.string.study_topic_edit)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null) // listener definido abaixo para validar
                .show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            String name = UiUtils.text(nameInput);
            if (name.isEmpty()) {
                nameLayout.setError(Honorific.str(ctx, R.string.required_field));
                return;
            }
            onSave.save(name, selected[0]);
            dialog.dismiss();
        });
    }
}
