package com.alfred.agenda.util;

import android.content.Context;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.alfred.agenda.R;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.timepicker.TimeFormat;

import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

/** Atalhos de interface usados por várias telas. */
public final class UiUtils {

    private UiUtils() {
    }

    /** Abre o relógio do Material 3 (24h). Devolve o horário escolhido em minutos. */
    public static void pickTime(Fragment host, int minutes, IntConsumer onPicked) {
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(minutes / 60)
                .setMinute(minutes % 60)
                .setTitleText(R.string.pick_time)
                .build();
        picker.addOnPositiveButtonClickListener(v ->
                onPicked.accept(picker.getHour() * 60 + picker.getMinute()));
        picker.show(host.getChildFragmentManager(), "time_picker");
    }

    /** Abre o calendário do Material 3. Devolve a data escolhida em epochDay. */
    public static void pickDate(Fragment host, long epochDay, LongConsumer onPicked) {
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setSelection(DateUtils.utcMillisFromEpochDay(epochDay))
                .setTitleText(R.string.pick_date)
                .build();
        picker.addOnPositiveButtonClickListener(selection ->
                onPicked.accept(DateUtils.epochDayFromUtcMillis(selection)));
        picker.show(host.getChildFragmentManager(), "date_picker");
    }

    /** Pergunta "Tem certeza?" antes de excluir. */
    public static void confirmDelete(Context context, String name, Runnable onConfirm) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.confirm_delete_title, name))
                .setMessage(Honorific.str(context, R.string.confirm_delete_msg))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    onConfirm.run();
                    toast(context, AlfredPhrases.deleted(context));
                })
                .show();
    }

    /**
     * Pede um texto (ex.: nome de um tema). Com onDelete != null, mostra também "Excluir".
     * O "Salvar" só fecha se o campo não estiver vazio.
     */
    public static void promptText(Context ctx, int titleRes, int hintRes, String initial,
                                  Consumer<String> onSave, @Nullable Runnable onDelete) {
        View view = LayoutInflater.from(ctx).inflate(R.layout.dialog_text_input, null);
        TextInputLayout layout = view.findViewById(R.id.layout_text);
        EditText input = view.findViewById(R.id.input_text);
        layout.setHint(hintRes);
        input.setText(initial);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(ctx)
                .setTitle(titleRes)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null);
        if (onDelete != null) builder.setNeutralButton(R.string.delete, (d, w) -> onDelete.run());
        AlertDialog dialog = builder.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            String text = text(input);
            if (text.isEmpty()) {
                layout.setError(Honorific.str(ctx, R.string.required_field));
                return;
            }
            onSave.accept(text);
            dialog.dismiss();
        });
    }

    /** Confirmação de exclusão com mensagem própria. */
    public static void confirmDelete(Context context, String name, int messageRes, Runnable onConfirm) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.confirm_delete_title, name))
                .setMessage(Honorific.str(context, messageRes))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    onConfirm.run();
                    toast(context, AlfredPhrases.deleted(context));
                })
                .show();
    }

    public static void toast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    /** Texto do campo sem espaços nas pontas (nunca null). */
    public static String text(EditText field) {
        Editable e = field.getText();
        return e == null ? "" : e.toString().trim();
    }
}
