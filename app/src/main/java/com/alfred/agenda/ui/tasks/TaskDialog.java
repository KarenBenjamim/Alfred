package com.alfred.agenda.ui.tasks;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.alfred.agenda.R;
import com.alfred.agenda.data.AlfredRepository;
import com.alfred.agenda.data.entity.Tag;
import com.alfred.agenda.data.entity.Task;
import com.alfred.agenda.util.AlfredPhrases;
import com.alfred.agenda.util.DateUtils;
import com.alfred.agenda.util.Honorific;
import com.alfred.agenda.util.TagColors;
import com.alfred.agenda.util.UiUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Diálogo para criar/editar uma tarefa (de Trabalho ou Pessoal).
 * Trabalho tem ainda: tipo de demanda, dia inteiro ou hora inicial/final, tags por grupo de cor
 * e o botão "Adiar" (pergunta a nova data); "Cancelada" é um dos status.
 */
public final class TaskDialog {

    /** Recebe a tarefa e as tags escolhidas (null = não mexer nas tags). */
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
     * @param allTags       tags existentes (o diálogo usa as da categoria: Trabalho ou Pessoal)
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

        // Tags: as do Pessoal (8 cores) ou as do Trabalho (grupo de cor)
        boolean work = !task.isPersonal();
        List<Tag> tags = new ArrayList<>();
        for (Tag t : allTags) if (t.isWork() == work) tags.add(t);
        Set<Long> selectedTags = new HashSet<>(currentTags);
        view.findViewById(R.id.tags_section).setVisibility(View.VISIBLE);
        renderTags(view.findViewById(R.id.tags_group), tags, selectedTags, work);

        // Trabalho: tipo de demanda e horário. Tarefa antiga sem hora começa no início do período.
        if (work) {
            view.findViewById(R.id.work_section).setVisibility(View.VISIBLE);
            view.findViewById(R.id.layout_period).setVisibility(View.GONE);
        }
        MaterialAutoCompleteTextView typeInput = view.findViewById(R.id.input_type);
        String[] types = ctx.getResources().getStringArray(R.array.demand_types);
        final int[] type = {Math.max(0, Math.min(Task.TYPE_COUNT - 1, task.demandType))};
        typeInput.setSimpleItems(types);
        typeInput.setText(types[type[0]], false);
        typeInput.setOnItemClickListener((p, v, position, id) -> type[0] = position);

        MaterialSwitch allDaySwitch = view.findViewById(R.id.switch_all_day);
        View timeGroup = view.findViewById(R.id.group_time);
        View timeError = view.findViewById(R.id.time_error);
        MaterialButton startButton = view.findViewById(R.id.button_start);
        MaterialButton endButton = view.findViewById(R.id.button_end);
        final int[] start = {task.startMinutes >= 0 ? task.startMinutes
                : Task.PERIOD_REMINDER_MINUTES[Math.min(task.period, Task.PERIOD_REMINDER_MINUTES.length - 1)]};
        final int[] end = {task.endMinutes > start[0] ? task.endMinutes : Math.min(24 * 60 - 1, start[0] + 60)};
        Runnable showTimes = () -> {
            startButton.setText(ctx.getString(R.string.task_start, DateUtils.formatTime(start[0])));
            endButton.setText(ctx.getString(R.string.task_end, DateUtils.formatTime(end[0])));
            timeError.setVisibility(end[0] > start[0] ? View.GONE : View.VISIBLE);
        };
        showTimes.run();
        allDaySwitch.setChecked(task.allDay);
        timeGroup.setVisibility(task.allDay ? View.GONE : View.VISIBLE);
        allDaySwitch.setOnCheckedChangeListener((b, checked) -> {
            timeGroup.setVisibility(checked ? View.GONE : View.VISIBLE);
            timeError.setVisibility(checked || end[0] > start[0] ? View.GONE : View.VISIBLE);
        });
        startButton.setOnClickListener(b -> UiUtils.pickTime(host, start[0], picked -> {
            int length = Math.max(15, end[0] - start[0]); // mantém a duração ao mudar o início
            start[0] = picked;
            end[0] = Math.min(24 * 60 - 1, picked + length);
            showTimes.run();
        }));
        endButton.setOnClickListener(b -> UiUtils.pickTime(host, end[0], picked -> {
            end[0] = picked;
            showTimes.run();
        }));

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

        // "Adiar" só para compromissos de Trabalho que já existem e ainda não foram encerrados
        boolean canPostpone = work && existing != null && !existing.isClosed();
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(ctx)
                .setTitle(existing == null ? R.string.task_new : R.string.task_edit)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null); // listener definido abaixo para validar antes de fechar
        if (canPostpone) builder.setNeutralButton(R.string.task_postpone, null);
        AlertDialog dialog = builder.show();

        // Copia o formulário para a tarefa; false = algo inválido (o erro já aparece na tela)
        BooleanSupplier collect = () -> {
            String title = UiUtils.text(titleInput);
            if (title.isEmpty()) {
                titleLayout.setError(Honorific.str(ctx, R.string.required_field));
                return false;
            }
            boolean allDay = work && allDaySwitch.isChecked();
            if (work && !allDay && end[0] <= start[0]) {
                timeError.setVisibility(View.VISIBLE);
                return false;
            }
            task.title = title;
            task.description = UiUtils.text(descInput);
            task.dueEpochDay = day[0];
            task.priority = priority[0];
            task.status = status[0];
            if (work) {
                task.demandType = type[0];
                task.allDay = allDay;
                task.startMinutes = allDay ? -1 : start[0];
                task.endMinutes = allDay ? -1 : end[0];
                task.period = Task.periodFor(allDay, task.startMinutes); // agrupa como as antigas
            } else {
                task.period = period[0];
            }
            return true;
        };

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            if (!collect.getAsBoolean()) return;
            onSave.onSave(task, selectedTags);
            UiUtils.toast(ctx, AlfredPhrases.saved(ctx));
            dialog.dismiss();
        });
        if (canPostpone) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(b -> {
                if (!collect.getAsBoolean()) return;
                long from = task.dueEpochDay;
                UiUtils.pickDate(host, Math.max(from + 1, DateUtils.today()), picked -> {
                    if (picked == from) return;
                    task.postponeTo(picked);
                    onSave.onSave(task, selectedTags);
                    UiUtils.toast(ctx, Honorific.str(ctx, R.string.task_postponed_msg, DateUtils.formatDate(picked)));
                    dialog.dismiss();
                });
            });
        }
    }

    /**
     * Desenha os chips das tags (marcáveis, cada um na sua cor) e o chip "+ Nova tag".
     * Toque e segure numa tag para editar ou excluir.
     */
    private static void renderTags(ChipGroup group, List<Tag> tags, Set<Long> selected, boolean work) {
        Context ctx = group.getContext();
        AlfredRepository repo = AlfredRepository.get(ctx);
        group.removeAllViews();

        for (Tag tag : tags) {
            Chip chip = new Chip(ctx);
            chip.setText(tag.name);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setChecked(selected.contains(tag.id));
            TagColors.style(chip, tag, true);
            chip.setOnCheckedChangeListener((b, checked) -> {
                if (checked) selected.add(tag.id);
                else selected.remove(tag.id);
            });
            chip.setOnLongClickListener(v -> {
                TagDialog.show(ctx, tag, work ? tags : null,
                        edited -> repo.saveTag(edited, saved -> {
                            tags.set(tags.indexOf(tag), saved);
                            renderTags(group, tags, selected, work);
                        }),
                        deleted -> UiUtils.confirmDelete(ctx, deleted.name, R.string.tag_delete_msg, () -> {
                            repo.deleteTag(deleted);
                            tags.remove(tag);
                            selected.remove(tag.id);
                            renderTags(group, tags, selected, work);
                        }));
                return true;
            });
            group.addView(chip);
        }

        // "+ Nova tag": cria e já deixa marcada nesta tarefa
        Chip add = new Chip(ctx);
        add.setText(R.string.tag_add);
        add.setChipIconResource(R.drawable.ic_add);
        add.setOnClickListener(v -> TagDialog.show(ctx, null, work ? tags : null, created -> repo.saveTag(created, saved -> {
            tags.add(saved);
            selected.add(saved.id);
            renderTags(group, tags, selected, work);
        }), null));
        group.addView(add);
    }
}
