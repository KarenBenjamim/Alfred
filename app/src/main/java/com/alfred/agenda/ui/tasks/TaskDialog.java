package com.alfred.agenda.ui.tasks;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.alfred.agenda.R;
import com.alfred.agenda.util.Honorific;
import com.alfred.agenda.data.AlfredRepository;
import com.alfred.agenda.data.entity.Tag;
import com.alfred.agenda.data.entity.Task;
import com.alfred.agenda.util.AlfredPhrases;
import com.alfred.agenda.util.DateUtils;
import com.alfred.agenda.util.TagColors;
import com.alfred.agenda.util.UiUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Diálogo para criar/editar uma tarefa (de Trabalho ou Pessoal). */
public final class TaskDialog {

    /** Recebe a tarefa e as tags escolhidas (null = tarefa sem tags, ex.: Trabalho). */
    public interface OnSave {
        void onSave(Task task, @Nullable Set<Long> tagIds);
    }

    private TaskDialog() {
    }

    /**
     * @param existing      null para criar
     * @param category      categoria de uma tarefa nova (Task.CATEGORY_*)
     * @param defaultDay    data de entrega sugerida (o dia selecionado no calendário)
     * @param defaultPeriod período sugerido para uma tarefa nova
     * @param allTags       tags existentes (só usadas nas tarefas pessoais)
     * @param currentTags   ids das tags que a tarefa já tem
     */
    public static void show(Fragment host, @Nullable Task existing, int category, long defaultDay,
                            int defaultPeriod, List<Tag> allTags, Set<Long> currentTags, OnSave onSave) {
        Context ctx = host.requireContext();
        View view = LayoutInflater.from(ctx).inflate(R.layout.dialog_task, null);

        TextInputLayout titleLayout = view.findViewById(R.id.layout_title);
        EditText titleInput = view.findViewById(R.id.input_title);
        EditText descInput = view.findViewById(R.id.input_description);
        MaterialButton dateButton = view.findViewById(R.id.button_date);
        MaterialAutoCompleteTextView periodInput = view.findViewById(R.id.input_period);
        MaterialAutoCompleteTextView priorityInput = view.findViewById(R.id.input_priority);
        MaterialAutoCompleteTextView statusInput = view.findViewById(R.id.input_status);

        // Trabalhamos numa cópia: se o usuário cancelar, nada muda
        Task task = existing != null ? existing.copy() : new Task();
        if (existing == null) {
            task.category = category;
            task.dueEpochDay = defaultDay;
            task.period = defaultPeriod;
        }

        titleInput.setText(task.title);
        descInput.setText(task.description);

        // Tags (só para tarefas pessoais)
        boolean withTags = task.isPersonal();
        List<Tag> tags = new ArrayList<>(allTags);
        Set<Long> selectedTags = new HashSet<>(currentTags);
        if (withTags) {
            view.findViewById(R.id.tags_section).setVisibility(View.VISIBLE);
            renderTags(view.findViewById(R.id.tags_group), tags, selectedTags);
        }

        final long[] day = {task.dueEpochDay};
        dateButton.setText(ctx.getString(R.string.due_label, DateUtils.formatDate(day[0])));
        dateButton.setOnClickListener(b -> UiUtils.pickDate(host, day[0], picked -> {
            day[0] = picked;
            dateButton.setText(ctx.getString(R.string.due_label, DateUtils.formatDate(picked)));
        }));

        // Trabalho oferece Manhã/Tarde; Pessoal oferece Manhã/Tarde/Noite
        String[] periods = Arrays.copyOf(ctx.getResources().getStringArray(R.array.period_options),
                Task.periodCount(task.category));
        final int[] period = {Math.min(task.period, periods.length - 1)};
        periodInput.setSimpleItems(periods);
        periodInput.setText(periods[period[0]], false);
        periodInput.setOnItemClickListener((p, v, position, id) -> period[0] = position);

        // As posições dos menus são os próprios valores de prioridade/status
        String[] priorities = ctx.getResources().getStringArray(R.array.priority_options);
        final int[] priority = {task.priority};
        priorityInput.setSimpleItems(priorities);
        priorityInput.setText(priorities[priority[0]], false);
        priorityInput.setOnItemClickListener((p, v, position, id) -> priority[0] = position);

        String[] statuses = ctx.getResources().getStringArray(R.array.status_options);
        final int[] status = {task.status};
        statusInput.setSimpleItems(statuses);
        statusInput.setText(statuses[status[0]], false);
        statusInput.setOnItemClickListener((p, v, position, id) -> status[0] = position);

        AlertDialog dialog = new MaterialAlertDialogBuilder(ctx)
                .setTitle(existing == null ? R.string.task_new : R.string.task_edit)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null) // listener definido abaixo para validar antes de fechar
                .show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            String title = UiUtils.text(titleInput);
            if (title.isEmpty()) {
                titleLayout.setError(Honorific.str(ctx, R.string.required_field));
                return;
            }
            task.title = title;
            task.description = UiUtils.text(descInput);
            task.dueEpochDay = day[0];
            task.period = period[0];
            task.priority = priority[0];
            task.status = status[0];
            onSave.onSave(task, withTags ? selectedTags : null);
            UiUtils.toast(ctx, AlfredPhrases.saved(ctx));
            dialog.dismiss();
        });
    }

    /**
     * Desenha os chips das tags (marcáveis, cada um na sua cor) e o chip "+ Nova tag".
     * Toque e segure numa tag para editar ou excluir.
     */
    private static void renderTags(ChipGroup group, List<Tag> tags, Set<Long> selected) {
        Context ctx = group.getContext();
        AlfredRepository repo = AlfredRepository.get(ctx);
        group.removeAllViews();

        for (Tag tag : tags) {
            Chip chip = new Chip(ctx);
            chip.setText(tag.name);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setChecked(selected.contains(tag.id));
            TagColors.style(chip, tag.colorIndex, true);
            chip.setOnCheckedChangeListener((b, checked) -> {
                if (checked) selected.add(tag.id);
                else selected.remove(tag.id);
            });
            chip.setOnLongClickListener(v -> {
                TagDialog.show(ctx, tag,
                        edited -> repo.saveTag(edited, saved -> {
                            tags.set(tags.indexOf(tag), saved);
                            renderTags(group, tags, selected);
                        }),
                        deleted -> UiUtils.confirmDelete(ctx, deleted.name, R.string.tag_delete_msg, () -> {
                            repo.deleteTag(deleted);
                            tags.remove(tag);
                            selected.remove(tag.id);
                            renderTags(group, tags, selected);
                        }));
                return true;
            });
            group.addView(chip);
        }

        // "+ Nova tag": cria e já deixa marcada nesta tarefa
        Chip add = new Chip(ctx);
        add.setText(R.string.tag_add);
        add.setChipIconResource(R.drawable.ic_add);
        add.setOnClickListener(v -> TagDialog.show(ctx, null, created -> repo.saveTag(created, saved -> {
            tags.add(saved);
            selected.add(saved.id);
            renderTags(group, tags, selected);
        }), null));
        group.addView(add);
    }
}
