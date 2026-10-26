package com.alfred.agenda.ui.tasks;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.alfred.agenda.MainActivity;
import com.alfred.agenda.R;
import com.alfred.agenda.data.entity.Tag;
import com.alfred.agenda.data.entity.Task;
import com.alfred.agenda.data.entity.TaskTag;
import com.alfred.agenda.util.DateUtils;
import com.alfred.agenda.util.EmptyState;
import com.alfred.agenda.util.TaskGrouping;
import com.alfred.agenda.util.UiUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Abas "Trabalho" e "Pessoal" (a mesma tela; a categoria vem nos argumentos — use newInstance).
 *
 *  - Modo "Tarefas": Atrasadas, Hoje, Amanhã e próximas datas, com o progresso de hoje no topo.
 *  - Modo "Agenda": filtro De/Até + atalhos (Hoje, 7 dias, Este mês) + calendário que destaca o
 *    período; só aparecem as tarefas do filtro, por dia e por período.
 * No Pessoal, cada cartão ganha a cor das suas tags (TaskAppearance).
 */
public class TasksFragment extends Fragment {

    private static final String ARG_CATEGORY = "category";

    private int category;
    private TasksViewModel viewModel;
    private TaskAdapter adapter;
    private View todayCard;
    private TextView todayCount;
    private TextView todayPercent;
    private LinearProgressIndicator todayBar;
    private View agendaSection;
    private RangeCalendarView calendar;
    private MaterialButton buttonFrom;
    private MaterialButton buttonTo;
    private TextView summary;
    private TextView hint;
    private View emptyState;
    private List<Task> allTasks = new ArrayList<>();
    private List<Tag> allTags = new ArrayList<>();
    private List<TaskTag> links = new ArrayList<>();
    /** id da tarefa -> tags dela (montado a partir de allTags + links). */
    private Map<Long, List<Tag>> tagsByTask = new HashMap<>();

    public static TasksFragment newInstance(int category) {
        Bundle args = new Bundle();
        args.putInt(ARG_CATEGORY, category);
        TasksFragment f = new TasksFragment();
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tasks, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        category = requireArguments().getInt(ARG_CATEGORY, Task.CATEGORY_WORK);
        viewModel = new ViewModelProvider(this).get(TasksViewModel.class);
        MainActivity activity = (MainActivity) requireActivity();

        MaterialButtonToggleGroup modeToggle = view.findViewById(R.id.toggle_mode);
        todayCard = view.findViewById(R.id.today_card);
        todayCount = view.findViewById(R.id.today_count);
        todayPercent = view.findViewById(R.id.today_percent);
        todayBar = view.findViewById(R.id.today_bar);
        agendaSection = view.findViewById(R.id.agenda_section);
        calendar = view.findViewById(R.id.calendar);
        buttonFrom = view.findViewById(R.id.button_from);
        buttonTo = view.findViewById(R.id.button_to);
        summary = view.findViewById(R.id.range_summary);
        hint = view.findViewById(R.id.range_hint);
        emptyState = view.findViewById(R.id.empty_state);
        RecyclerView list = view.findViewById(R.id.list);

        adapter = new TaskAdapter(new TaskAdapter.Listener() {
            @Override
            public void onEdit(Task task) {
                TaskDialog.show(TasksFragment.this, task, category, task.dueEpochDay, task.period,
                        allTags, tagIdsOf(task.id), viewModel::save);
            }

            @Override
            public void onDelete(Task task) {
                UiUtils.confirmDelete(requireContext(), task.title, () -> viewModel.delete(task));
            }

            @Override
            public void onDoneChanged(Task task, boolean done) {
                viewModel.setDone(task, done, done ? () -> {
                    if (isAdded()) ((MainActivity) requireActivity()).celebrateGoal();
                } : null);
            }
        });
        list.setAdapter(adapter);

        // Modo inicial: Trabalho abre na Agenda; Pessoal, na lista de Tarefas.
        // O card vazio da tela Início pode pedir para abrir o formulário de nova tarefa.
        String action = activity.consumeAction(category == Task.CATEGORY_PERSONAL
                ? MainActivity.TAB_PERSONAL : MainActivity.TAB_WORK);
        if (viewModel.mode < 0) {
            viewModel.mode = category == Task.CATEGORY_WORK ? TasksViewModel.MODE_AGENDA : TasksViewModel.MODE_LIST;
        }
        modeToggle.check(viewModel.mode == TasksViewModel.MODE_AGENDA ? R.id.button_mode_agenda : R.id.button_mode_list);
        modeToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            viewModel.mode = checkedId == R.id.button_mode_agenda ? TasksViewModel.MODE_AGENDA : TasksViewModel.MODE_LIST;
            render();
        });

        // Botões De / Até abrem o seletor de data
        buttonFrom.setOnClickListener(v -> UiUtils.pickDate(this, viewModel.rangeFrom, day -> {
            viewModel.setFrom(day);
            viewModel.shownMonth = YearMonth.from(LocalDate.ofEpochDay(day));
            render();
        }));
        buttonTo.setOnClickListener(v -> UiUtils.pickDate(this, viewModel.rangeTo, day -> {
            viewModel.setTo(day);
            viewModel.shownMonth = YearMonth.from(LocalDate.ofEpochDay(day));
            render();
        }));
        buildRangeChips(view.findViewById(R.id.range_chips));

        // Calendário: 1º toque = início, 2º toque = fim
        calendar.setMonth(viewModel.shownMonth);
        calendar.setOnMonthChangedListener(() -> viewModel.shownMonth = calendar.getMonth());
        calendar.setOnDayClickListener(day -> {
            viewModel.onCalendarTap(day);
            YearMonth tapped = YearMonth.from(LocalDate.ofEpochDay(day));
            if (!tapped.equals(viewModel.shownMonth)) viewModel.shownMonth = tapped; // tocou num dia de outro mês
            render();
        });

        viewModel.getTasks(category).observe(getViewLifecycleOwner(), tasks -> {
            allTasks = tasks;
            render();
        });
        if (category == Task.CATEGORY_PERSONAL) {
            viewModel.tags.observe(getViewLifecycleOwner(), tags -> {
                allTags = tags;
                rebuildTagMap();
            });
            viewModel.taskTags.observe(getViewLifecycleOwner(), loaded -> {
                links = loaded;
                rebuildTagMap();
            });
        }

        view.findViewById(R.id.fab).setOnClickListener(v -> newTask());
        if (MainActivity.ACTION_NEW_TASK.equals(action)) newTask();
    }

    private void newTask() {
        TaskDialog.show(this, null, category, suggestedDay(), suggestedPeriod(),
                allTags, Collections.emptySet(), viewModel::save);
    }

    /** Atalhos de período do modo Agenda. */
    private void buildRangeChips(ChipGroup group) {
        long today = DateUtils.today();
        LocalDate now = LocalDate.now();
        addRangeChip(group, R.string.range_today, today, today);
        addRangeChip(group, R.string.range_next_7, today, today + 6);
        addRangeChip(group, R.string.range_this_month,
                now.withDayOfMonth(1).toEpochDay(), now.withDayOfMonth(now.lengthOfMonth()).toEpochDay());
    }

    private void addRangeChip(ChipGroup group, int label, long from, long to) {
        Chip chip = new Chip(requireContext());
        chip.setText(label);
        chip.setOnClickListener(v -> {
            viewModel.setRange(from, to);
            render();
        });
        group.addView(chip);
    }

    // ---------------- Desenho ----------------

    private void render() {
        boolean agenda = viewModel.mode == TasksViewModel.MODE_AGENDA;
        agendaSection.setVisibility(agenda ? View.VISIBLE : View.GONE);
        todayCard.setVisibility(agenda ? View.GONE : View.VISIBLE);
        if (agenda) renderAgenda();
        else renderList();
    }

    /** Modo Tarefas: progresso de hoje + Atrasadas / Hoje / Amanhã / próximas datas. */
    private void renderList() {
        long today = DateUtils.today();
        int doneToday = 0, totalToday = 0;
        for (Task t : allTasks) {
            if (t.dueEpochDay != today) continue;
            totalToday++;
            if (t.isDone()) doneToday++;
        }
        int percent = totalToday == 0 ? 0 : Math.round(100f * doneToday / totalToday);
        todayCount.setText(getString(R.string.tasks_today_progress, doneToday, totalToday));
        todayPercent.setText(getString(R.string.percent_value, percent));
        todayBar.setProgressCompat(percent, true);

        List<TaskAdapter.Row> rows = new ArrayList<>();
        for (TaskGrouping.Section s : TaskGrouping.upcoming(allTasks, today)) {
            if (s.kind == TaskGrouping.KIND_OVERDUE) {
                rows.add(TaskAdapter.Row.header(getString(R.string.tasks_overdue_header),
                        ContextCompat.getColor(requireContext(), R.color.error)));
            } else {
                rows.add(TaskAdapter.Row.header(dayTitle(s.day)));
            }
            for (Task t : s.tasks) rows.add(TaskAdapter.Row.task(t));
        }
        adapter.setRows(rows, tagsByTask, true);
        if (rows.isEmpty()) {
            EmptyState.show(emptyState, R.drawable.ic_task, R.string.tasks_empty_title,
                    R.string.tasks_empty_list, R.string.create_task, this::newTask);
        } else {
            EmptyState.hide(emptyState);
        }
    }

    /** Modo Agenda: filtro, calendário e tarefas do período. */
    private void renderAgenda() {
        long from = viewModel.rangeFrom;
        long to = viewModel.rangeTo;

        buttonFrom.setText(getString(R.string.range_from, DateUtils.formatDateShort(from)));
        buttonTo.setText(getString(R.string.range_to, DateUtils.formatDateShort(to)));
        hint.setVisibility(viewModel.awaitingRangeEnd ? View.VISIBLE : View.GONE);

        // Pontinhos no calendário: todos os dias que têm tarefa (mesmo fora do filtro)
        Set<Long> marked = new HashSet<>();
        for (Task t : allTasks) marked.add(t.dueEpochDay);
        if (!calendar.getMonth().equals(viewModel.shownMonth)) calendar.setMonth(viewModel.shownMonth);
        calendar.setMarkedDays(marked);
        calendar.setRange(from, to);

        // Só as tarefas dentro do filtro
        List<Task> inRange = new ArrayList<>();
        for (Task t : allTasks) {
            if (t.dueEpochDay >= from && t.dueEpochDay <= to) inRange.add(t);
        }
        inRange.sort(TaskGrouping.ORDER);

        int days = (int) (to - from + 1);
        summary.setText(getString(R.string.range_summary,
                getResources().getQuantityString(R.plurals.range_days, days, days),
                getResources().getQuantityString(R.plurals.range_tasks, inRange.size(), inRange.size())));

        List<TaskAdapter.Row> rows = new ArrayList<>();
        if (from == to) buildSingleDayRows(rows, inRange);
        else buildMultiDayRows(rows, inRange);
        adapter.setRows(rows, tagsByTask, false);
        if (from != to && inRange.isEmpty()) {
            EmptyState.show(emptyState, R.drawable.ic_dates, R.string.tasks_empty_title,
                    R.string.tasks_empty_range, R.string.create_task, this::newTask);
        } else {
            EmptyState.hide(emptyState);
        }
    }

    /** Junta as tags e as ligações num mapa "tarefa -> tags" e redesenha. */
    private void rebuildTagMap() {
        Map<Long, Tag> byId = new HashMap<>();
        for (Tag t : allTags) byId.put(t.id, t);
        Map<Long, List<Tag>> map = new HashMap<>();
        for (TaskTag link : links) {
            Tag tag = byId.get(link.tagId);
            if (tag != null) map.computeIfAbsent(link.taskId, k -> new ArrayList<>()).add(tag);
        }
        tagsByTask = map;
        render();
    }

    private Set<Long> tagIdsOf(long taskId) {
        Set<Long> ids = new HashSet<>();
        for (Tag t : tagsByTask.getOrDefault(taskId, Collections.emptyList())) ids.add(t.id);
        return ids;
    }

    /** Um dia só: um separador por período, com "Livre." quando vazio. */
    private void buildSingleDayRows(List<TaskAdapter.Row> rows, List<Task> tasks) {
        rows.add(TaskAdapter.Row.header(dayTitle(viewModel.rangeFrom)));
        String[] periods = getResources().getStringArray(R.array.period_options);
        int count = Task.periodCount(category);
        for (int p = 0; p < count; p++) {
            rows.add(TaskAdapter.Row.subheader(periods[p]));
            boolean any = false;
            for (Task t : tasks) {
                if (Math.min(t.period, count - 1) == p) {
                    rows.add(TaskAdapter.Row.task(t));
                    any = true;
                }
            }
            if (!any) rows.add(TaskAdapter.Row.empty());
        }
    }

    /** Vários dias: separador por dia (só dias com tarefas) e, dentro, os períodos. */
    private void buildMultiDayRows(List<TaskAdapter.Row> rows, List<Task> tasks) {
        String[] periods = getResources().getStringArray(R.array.period_options);
        int count = Task.periodCount(category);
        long lastDay = Long.MIN_VALUE;
        int lastPeriod = -1;
        for (Task t : tasks) {
            if (t.dueEpochDay != lastDay) {
                rows.add(TaskAdapter.Row.header(dayTitle(t.dueEpochDay)));
                lastDay = t.dueEpochDay;
                lastPeriod = -1;
            }
            int period = Math.min(t.period, count - 1);
            if (period != lastPeriod) {
                rows.add(TaskAdapter.Row.subheader(periods[period]));
                lastPeriod = period;
            }
            rows.add(TaskAdapter.Row.task(t));
        }
    }

    /** "Hoje · Sábado, 25 de outubro", "Amanhã · …" ou só a data. */
    private String dayTitle(long day) {
        String text = DateUtils.formatLongDay(LocalDate.ofEpochDay(day));
        long today = DateUtils.today();
        if (day == today) return getString(R.string.range_today_header, text);
        if (day == today + 1) return getString(R.string.range_tomorrow_header, text);
        return text;
    }

    /** Data sugerida para uma tarefa nova: hoje, se estiver no filtro; senão, o início do filtro. */
    private long suggestedDay() {
        long today = DateUtils.today();
        if (viewModel.mode != TasksViewModel.MODE_AGENDA) return today;
        return today >= viewModel.rangeFrom && today <= viewModel.rangeTo ? today : viewModel.rangeFrom;
    }

    /** Sugere o período atual para uma tarefa nova (ex.: à tarde, sugere "Tarde"). */
    private int suggestedPeriod() {
        int hour = LocalTime.now().getHour();
        int period = hour < 12 ? Task.PERIOD_MORNING : hour < 18 ? Task.PERIOD_AFTERNOON : Task.PERIOD_NIGHT;
        return Math.min(period, Task.periodCount(category) - 1);
    }
}
