package com.alfred.agenda.ui.habits;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.alfred.agenda.R;
import com.alfred.agenda.util.Honorific;
import com.alfred.agenda.data.entity.Habit;
import com.alfred.agenda.util.AlfredPhrases;
import com.alfred.agenda.util.UiUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.util.function.Consumer;

/** Diálogo para criar/editar um hábito (só o nome). */
public final class HabitDialog {

    private HabitDialog() {
    }

    public static void show(Context ctx, @Nullable Habit existing, Consumer<Habit> onSave) {
        View view = LayoutInflater.from(ctx).inflate(R.layout.dialog_habit, null);
        TextInputLayout nameLayout = view.findViewById(R.id.layout_name);
        EditText nameInput = view.findViewById(R.id.input_name);

        // Trabalhamos numa cópia: se o usuário cancelar, nada muda
        Habit habit = new Habit();
        if (existing != null) {
            habit.id = existing.id;
            habit.name = existing.name;
            habit.position = existing.position;
        }
        nameInput.setText(habit.name);

        AlertDialog dialog = new MaterialAlertDialogBuilder(ctx)
                .setTitle(existing == null ? R.string.habit_new : R.string.habit_edit)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null) // listener definido abaixo para validar antes de fechar
                .show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            String name = UiUtils.text(nameInput);
            if (name.isEmpty()) {
                nameLayout.setError(Honorific.str(ctx, R.string.required_field));
                return;
            }
            habit.name = name;
            onSave.accept(habit);
            UiUtils.toast(ctx, AlfredPhrases.saved(ctx));
            dialog.dismiss();
        });
    }
}
