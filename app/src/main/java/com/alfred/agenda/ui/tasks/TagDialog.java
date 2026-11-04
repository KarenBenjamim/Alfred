package com.alfred.agenda.ui.tasks;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.alfred.agenda.R;
import com.alfred.agenda.ui.common.ColorDots;
import com.alfred.agenda.util.Honorific;
import com.alfred.agenda.data.entity.Tag;
import com.alfred.agenda.util.TagColors;
import com.alfred.agenda.util.UiUtils;
import com.alfred.agenda.util.WorkTagColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;
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
        show(ctx, existing, null, onSave, onDelete);
    }

    /**
     * @param workTags tags de Trabalho que já existem: com ela, o diálogo pede o GRUPO (que define
     *                 a cor) em vez da cor, e o tom é escolhido sozinho; null = tag do Pessoal
     */
    public static void show(Context ctx, @Nullable Tag existing, @Nullable List<Tag> workTags, Consumer<Tag> onSave,
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
            tag.tagGroup = existing.tagGroup;
        }
        nameInput.setText(tag.name);

        boolean work = workTags != null;
        final int[] selected = {work ? WorkTagColors.safeGroup(tag.tagGroup) : tag.colorIndex};
        if (work) {
            ((TextView) view.findViewById(R.id.color_label)).setText(R.string.tag_group);
            renderGroups(colors, selected);
        } else {
            renderColors(colors, selected);
        }

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
            if (work) {
                // Tom novo só se mudou de grupo (ou é tag nova): o menos usado no grupo
                if (existing == null || existing.tagGroup != selected[0]) {
                    tag.colorIndex = WorkTagColors.nextShade(selected[0], workTags, tag.id);
                }
                tag.tagGroup = selected[0];
            } else {
                tag.colorIndex = selected[0];
                tag.tagGroup = Tag.NO_GROUP;
            }
            onSave.accept(tag);
            dialog.dismiss();
        });
    }

    /** Trabalho: uma bolinha por grupo (Projeto, Bot, Empresa, Evento/Estudo, Outros) na cor dele. */
    private static void renderGroups(LinearLayout container, int[] selected) {
        Context ctx = container.getContext();
        int[] colors = new int[Tag.GROUP_COUNT];
        for (int g = 0; g < colors.length; g++) colors[g] = WorkTagColors.groupColor(ctx, g);
        ColorDots.render(container, colors, ctx.getResources().getStringArray(R.array.work_tag_groups), selected);
    }

    /** Desenha as 8 bolinhas da paleta de tags; a escolhida ganha um anel claro em volta. */
    private static void renderColors(LinearLayout container, int[] selected) {
        Context ctx = container.getContext();
        int[] colors = new int[TagColors.COLORS.length];
        for (int i = 0; i < colors.length; i++) colors[i] = TagColors.color(ctx, i);
        ColorDots.render(container, colors, ctx.getResources().getStringArray(R.array.tag_color_names), selected);
    }
}
