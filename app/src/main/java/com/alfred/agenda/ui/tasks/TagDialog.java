package com.alfred.agenda.ui.tasks;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.alfred.agenda.R;
import com.alfred.agenda.util.Honorific;
import com.alfred.agenda.data.entity.Tag;
import com.alfred.agenda.util.TagColors;
import com.alfred.agenda.util.UiUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.util.function.Consumer;

/** Diálogo para criar/editar uma tag: nome + uma das 8 cores da paleta. */
public final class TagDialog {

    private TagDialog() {
    }

    /**
     * @param existing null para criar
     * @param onDelete chamado se a usuária excluir (só aparece ao editar); pode ser null
     */
    public static void show(Context ctx, @Nullable Tag existing, Consumer<Tag> onSave,
                            @Nullable Consumer<Tag> onDelete) {
        View view = LayoutInflater.from(ctx).inflate(R.layout.dialog_tag, null);
        TextInputLayout nameLayout = view.findViewById(R.id.layout_name);
        EditText nameInput = view.findViewById(R.id.input_name);
        LinearLayout colors = view.findViewById(R.id.colors);

        Tag tag = new Tag();
        if (existing != null) {
            tag.id = existing.id;
            tag.name = existing.name;
            tag.colorIndex = existing.colorIndex;
        }
        nameInput.setText(tag.name);

        final int[] selected = {tag.colorIndex};
        renderColors(colors, selected);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(ctx)
                .setTitle(existing == null ? R.string.tag_new : R.string.tag_edit)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null); // listener definido abaixo para validar
        if (existing != null && onDelete != null) {
            builder.setNeutralButton(R.string.delete, (d, w) -> onDelete.accept(existing));
        }
        AlertDialog dialog = builder.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            String name = UiUtils.text(nameInput);
            if (name.isEmpty()) {
                nameLayout.setError(Honorific.str(ctx, R.string.required_field));
                return;
            }
            tag.name = name;
            tag.colorIndex = selected[0];
            onSave.accept(tag);
            dialog.dismiss();
        });
    }

    /** Desenha as 8 bolinhas; a escolhida ganha um anel claro em volta. */
    private static void renderColors(LinearLayout container, int[] selected) {
        Context ctx = container.getContext();
        container.removeAllViews();
        String[] names = ctx.getResources().getStringArray(R.array.tag_color_names);
        float density = ctx.getResources().getDisplayMetrics().density;
        int size = Math.round(30 * density);

        for (int i = 0; i < TagColors.COLORS.length; i++) {
            int index = i;
            View dot = new View(ctx);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(TagColors.color(ctx, i));
            if (i == selected[0]) {
                d.setStroke(Math.round(3 * density), ContextCompat.getColor(ctx, R.color.text_primary));
            }
            dot.setBackground(d);
            dot.setContentDescription(names[i]);
            dot.setOnClickListener(v -> {
                selected[0] = index;
                renderColors(container, selected);
            });

            LinearLayout cell = new LinearLayout(ctx);
            cell.setGravity(android.view.Gravity.CENTER);
            cell.addView(dot, new LinearLayout.LayoutParams(size, size));
            container.addView(cell, new LinearLayout.LayoutParams(0, Math.round(40 * density), 1f));
        }
    }
}
