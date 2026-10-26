package com.alfred.agenda.ui.diet;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.alfred.agenda.R;
import com.alfred.agenda.util.Honorific;
import com.alfred.agenda.data.entity.Meal;
import com.alfred.agenda.data.entity.MealItem;
import com.alfred.agenda.data.entity.MealWithItems;
import com.alfred.agenda.util.AlfredPhrases;
import com.alfred.agenda.util.DateUtils;
import com.alfred.agenda.util.UiUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

/** Diálogo para criar/editar uma refeição e seus itens. */
public final class MealDialog {

    public interface OnSave {
        void onSave(Meal meal, List<MealItem> items);
    }

    private MealDialog() {
    }

    /** @param existing null para criar uma nova refeição. */
    public static void show(Fragment host, @Nullable MealWithItems existing, OnSave onSave) {
        Context ctx = host.requireContext();
        View view = LayoutInflater.from(ctx).inflate(R.layout.dialog_meal, null);

        TextInputLayout nameLayout = view.findViewById(R.id.layout_name);
        MaterialAutoCompleteTextView nameInput = view.findViewById(R.id.input_name);
        MaterialButton timeButton = view.findViewById(R.id.button_time);
        MaterialSwitch notifySwitch = view.findViewById(R.id.switch_notify);
        LinearLayout itemsContainer = view.findViewById(R.id.items_container);
        MaterialButton addItemButton = view.findViewById(R.id.button_add_item);

        // Trabalhamos numa cópia: se o usuário cancelar, nada muda
        Meal meal = new Meal();
        if (existing != null) {
            meal.id = existing.meal.id;
            meal.name = existing.meal.name;
            meal.timeMinutes = existing.meal.timeMinutes;
            meal.notifyEnabled = existing.meal.notifyEnabled;
            meal.notes = existing.meal.notes;
        }

        nameInput.setSimpleItems(ctx.getResources().getStringArray(R.array.meal_name_suggestions));
        nameInput.setText(meal.name, false);
        notifySwitch.setChecked(meal.notifyEnabled);

        final int[] time = {meal.timeMinutes};
        timeButton.setText(ctx.getString(R.string.time_label, DateUtils.formatTime(time[0])));
        timeButton.setOnClickListener(b -> UiUtils.pickTime(host, time[0], minutes -> {
            time[0] = minutes;
            timeButton.setText(ctx.getString(R.string.time_label, DateUtils.formatTime(minutes)));
        }));

        if (existing != null && !existing.sortedItems().isEmpty()) {
            for (MealItem item : existing.sortedItems()) addItemRow(itemsContainer, item);
        } else {
            addItemRow(itemsContainer, null);
        }
        addItemButton.setOnClickListener(b -> addItemRow(itemsContainer, null).requestFocus());

        AlertDialog dialog = new MaterialAlertDialogBuilder(ctx)
                .setTitle(existing == null ? R.string.meal_new : R.string.meal_edit)
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
            meal.name = name;
            meal.timeMinutes = time[0];
            meal.notifyEnabled = notifySwitch.isChecked();

            List<MealItem> items = new ArrayList<>();
            for (int i = 0; i < itemsContainer.getChildCount(); i++) {
                View row = itemsContainer.getChildAt(i);
                String desc = UiUtils.text(row.findViewById(R.id.input_desc));
                String qty = UiUtils.text(row.findViewById(R.id.input_qty));
                if (desc.isEmpty() && qty.isEmpty()) continue; // linha em branco
                MealItem item = new MealItem();
                item.description = desc;
                item.quantity = qty;
                // O diálogo não mostra substituições/grupo: mantém os do item original
                if (row.getTag() instanceof MealItem) {
                    MealItem original = (MealItem) row.getTag();
                    item.substitutions = original.substitutions;
                    item.groupNumber = original.groupNumber;
                    item.groupPortions = original.groupPortions;
                }
                items.add(item);
            }
            onSave.onSave(meal, items);
            UiUtils.toast(ctx, AlfredPhrases.saved(ctx));
            dialog.dismiss();
        });
    }

    /**
     * Adiciona uma linha "alimento + quantidade" e devolve o campo do alimento.
     * O item original (se houver) fica guardado na tag da linha.
     */
    private static EditText addItemRow(LinearLayout container, @Nullable MealItem original) {
        View row = LayoutInflater.from(container.getContext())
                .inflate(R.layout.item_edit_meal_item, container, false);
        EditText descInput = row.findViewById(R.id.input_desc);
        EditText qtyInput = row.findViewById(R.id.input_qty);
        if (original != null) {
            descInput.setText(original.description);
            qtyInput.setText(original.quantity);
            row.setTag(original);
        }
        row.findViewById(R.id.button_remove).setOnClickListener(v -> container.removeView(row));
        container.addView(row);
        return descInput;
    }
}
