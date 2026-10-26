package com.alfred.agenda.ui.today;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;

import com.alfred.agenda.MainActivity;
import com.alfred.agenda.R;
import com.alfred.agenda.data.entity.Habit;
import com.alfred.agenda.data.entity.HabitLog;
import com.alfred.agenda.data.entity.MealLog;
import com.alfred.agenda.data.entity.MealWithItems;
import com.alfred.agenda.data.entity.StudyLog;
import com.alfred.agenda.data.entity.StudySubject;
import com.alfred.agenda.data.entity.Task;
import com.alfred.agenda.data.entity.WaterLog;
import com.alfred.agenda.ui.common.ProgressRingView;
import com.alfred.agenda.ui.common.WaterGlassView;
import com.alfred.agenda.ui.habits.HabitsViewModel;
import com.alfred.agenda.util.DateUtils;
import com.alfred.agenda.util.DayProgress;
import com.alfred.agenda.util.Honorific;
import com.alfred.agenda.util.MealGroups;
import com.alfred.agenda.util.WaterGoal;
import com.alfred.agenda.util.WeekDots;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Tela Início, nesta ordem:
 *  1. Resumo do dia (anel + barras por categoria; critério em DayProgress);
 *  2. Água (copo que enche até a meta calculada pelo peso do perfil);
 *  3. Próximo compromisso pessoal e 4. de trabalho (o pendente de MAIOR prioridade);
 *  5. Hábitos: só as bolinhas da semana (toque abre a aba Hábitos);
 *  6. Compromissos do dia: uma bolinha por tarefa de hoje (toque abre Trabalho ou Pessoal).
 * Tudo calculado a partir dos dados reais.
 */
public class TodayFragment extends Fragment {

    private static final int DOT_DP = 14;

    private TodayViewModel viewModel;

    private View loading;
    private View content;
    private TextView greeting;
    private ImageView greetingIcon;
    private TextView dateText;
    private ProgressRingView ring;
    private TextView ringCount;
    private TextView ringPercent;
    private LinearLayout categories;
    private WaterGlassView waterGlass;
    private TextView waterAmount;
    private TextView waterDetail;
    private View cardNextPersonal;
    private View cardNextWork;
    private LinearLayout habitsWeek;
    private TextView habitsCount;
    private TextView habitsAllDone;
    private View rowWork;
    private View rowPersonal;
    private TextView commitmentsCount;
    private TextView commitmentsAllDone;

    // Últimos dados recebidos do banco
    private List<MealWithItems> meals;
    private Set<Long> mealsDone;
    private List<Habit> habits;
    private List<HabitLog> habitLogs;
    private List<Task> tasks;
    @Nullable
    private Task nextPersonal;
    @Nullable
    private Task nextWork;
    private List<StudySubject> subjects = new ArrayList<>();
    private List<StudyLog> studyLogs = new ArrayList<>();
    private int waterMl;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_today, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        loading = view.findViewById(R.id.loading);
        content = view.findViewById(R.id.content);
        greeting = view.findViewById(R.id.text_greeting);
        greetingIcon = view.findViewById(R.id.greeting_icon);
        dateText = view.findViewById(R.id.text_date);
        ring = view.findViewById(R.id.ring);
        ringCount = view.findViewById(R.id.ring_count);
        ringPercent = view.findViewById(R.id.ring_percent);
        categories = view.findViewById(R.id.categories);
        waterGlass = view.findViewById(R.id.water_glass);
        waterAmount = view.findViewById(R.id.water_amount);
        waterDetail = view.findViewById(R.id.water_detail);
        cardNextPersonal = view.findViewById(R.id.card_next_personal);
        cardNextWork = view.findViewById(R.id.card_next_work);
        habitsWeek = view.findViewById(R.id.habits_week);
        habitsCount = view.findViewById(R.id.habits_count);
        habitsAllDone = view.findViewById(R.id.habits_all_done);
        rowWork = view.findViewById(R.id.row_work);
        rowPersonal = view.findViewById(R.id.row_personal);
        commitmentsCount = view.findViewById(R.id.commitments_count);
        commitmentsAllDone = view.findViewById(R.id.commitments_all_done);

        MainActivity activity = (MainActivity) requireActivity();
        view.findViewById(R.id.card_habits).setOnClickListener(v -> activity.showTab(MainActivity.TAB_HABITS));
        rowWork.setOnClickListener(v -> activity.showTab(MainActivity.TAB_WORK));
        rowPersonal.setOnClickListener(v -> activity.showTab(MainActivity.TAB_PERSONAL));

        viewModel = new ViewModelProvider(this).get(TodayViewModel.class);
        observe(viewModel.meals, list -> meals = list);
        observe(viewModel.mealLogs, logs -> {
            mealsDone = new HashSet<>();
            for (MealLog l : logs) mealsDone.add(l.mealId);
        });
        observe(viewModel.habits, list -> habits = list);
        observe(viewModel.habitLogs, list -> habitLogs = list);
        observe(viewModel.tasks, list -> tasks = list);
        observe(viewModel.nextPersonal, t -> nextPersonal = t);
        observe(viewModel.nextWork, t -> nextWork = t);
        observe(viewModel.subjects, list -> subjects = list);
        observe(viewModel.studyLogs, list -> studyLogs = list);
        observe(viewModel.water, (WaterLog w) -> waterMl = w == null ? 0 : w.ml);
    }

    /** Guarda o dado recebido e redesenha a tela. */
    private <T> void observe(LiveData<T> live, Consumer<T> store) {
        live.observe(getViewLifecycleOwner(), value -> {
            store.accept(value);
            renderAll();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.refreshDay();
        int hour = LocalTime.now().getHour();
        // O tratamento pode ter mudado no Perfil: a saudação é montada de novo a cada volta
        greeting.setText(Honorific.greeting(requireContext(), hour));
        greetingIcon.setImageResource(hour >= 5 && hour < 18 ? R.drawable.ic_sun : R.drawable.ic_moon);
        dateText.setText(DateUtils.formatLongDay(LocalDate.now()));
        renderAll();
    }

    /** LoadingState até os dados principais chegarem; depois, a tela completa. */
    private void renderAll() {
        if (meals == null || mealsDone == null || habits == null || habitLogs == null || tasks == null) {
            loading.setVisibility(View.VISIBLE);
            content.setVisibility(View.GONE);
            return;
        }
        loading.setVisibility(View.GONE);
        content.setVisibility(View.VISIBLE);
        renderSummary();
        renderWater();
        renderNext(cardNextPersonal, nextPersonal, Task.CATEGORY_PERSONAL);
        renderNext(cardNextWork, nextWork, Task.CATEGORY_WORK);
        renderHabits();
        renderCommitments();
    }

    // ---------------- 1. Resumo do dia ----------------

    private DayProgress progress() {
        long today = viewModel.getDay();
        Set<Long> habitsDoneToday = new HashSet<>();
        for (HabitLog l : habitLogs) if (l.epochDay == today) habitsDoneToday.add(l.habitId);
        Set<Long> studied = new HashSet<>();
        for (StudyLog l : studyLogs) if (l.epochDay == today && l.seconds > 0) studied.add(l.subjectId);
        for (StudySubject s : subjects) if (s.isRunning()) studied.add(s.id);
        return DayProgress.compute(today, tasks, habits, habitsDoneToday,
                MealGroups.group(meals), mealsDone, subjects, studied);
    }

    private void renderSummary() {
        DayProgress p = progress();
        ring.setProgress(p.fraction());
        ringCount.setText(getString(R.string.home_ring_count, p.done(), p.total()));
        ringPercent.setText(p.total() == 0 ? getString(R.string.home_ring_empty)
                : getString(R.string.home_ring_percent, Math.round(p.fraction() * 100)));

        categories.removeAllViews();
        addCategory(R.string.cat_work, R.color.cat_work, p.work);
        addCategory(R.string.cat_study, R.color.cat_study, p.study);
        addCategory(R.string.cat_habits, R.color.cat_habits, p.habits);
        addCategory(R.string.cat_personal, R.color.cat_personal, p.personal);
        addCategory(R.string.cat_diet, R.color.cat_diet, p.diet);
    }

    private void addCategory(int label, int colorRes, DayProgress.Count count) {
        View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_category_row, categories, false);
        int color = ContextCompat.getColor(requireContext(), colorRes);
        row.findViewById(R.id.swatch).getBackground().mutate().setTint(color);
        ((TextView) row.findViewById(R.id.label)).setText(label);
        ((TextView) row.findViewById(R.id.count)).setText(getString(R.string.home_category_count, count.done, count.total));
        LinearProgressIndicator bar = row.findViewById(R.id.bar);
        bar.setIndicatorColor(color);
        bar.setProgressCompat(Math.round(count.fraction() * 100), true);
        row.setContentDescription(getString(R.string.home_category_a11y, getString(label), count.done, count.total));
        categories.addView(row);
    }

    // ---------------- 2. Água ----------------

    private void renderWater() {
        MainActivity activity = (MainActivity) requireActivity();
        View card = requireView().findViewById(R.id.card_water);
        int goal = WaterGoal.goalMl(requireContext());
        NumberFormat n = NumberFormat.getIntegerInstance(DateUtils.PT_BR);
        waterGlass.setFraction(WaterGoal.fraction(waterMl, goal));
        if (goal <= 0) {
            // Sem peso no perfil não há meta: o card leva ao Perfil
            waterAmount.setText(getString(R.string.water_amount_no_goal, n.format(waterMl)));
            waterDetail.setText(R.string.water_no_weight);
            card.setOnClickListener(v -> activity.showTab(MainActivity.TAB_PROFILE));
        } else {
            waterAmount.setText(getString(R.string.water_amount, n.format(waterMl), n.format(goal)));
            waterDetail.setText(waterMl >= goal ? Honorific.str(requireContext(), R.string.water_goal_reached)
                    : getString(R.string.water_percent, Math.round(WaterGoal.fraction(waterMl, goal) * 100)));
            card.setOnClickListener(v -> activity.showTab(MainActivity.TAB_HABITS));
        }
        card.setContentDescription(waterAmount.getText() + ". " + waterDetail.getText());
    }

    // ---------------- 3 e 4. Próximo compromisso pessoal / de trabalho ----------------

    private void renderNext(View card, @Nullable Task task, int category) {
        boolean personal = category == Task.CATEGORY_PERSONAL;
        int tab = personal ? MainActivity.TAB_PERSONAL : MainActivity.TAB_WORK;
        int color = ContextCompat.getColor(requireContext(), personal ? R.color.cat_personal : R.color.cat_work);
        MainActivity activity = (MainActivity) requireActivity();

        TextView label = card.findViewById(R.id.next_label);
        TextView when = card.findViewById(R.id.next_when);
        TextView title = card.findViewById(R.id.next_title);
        TextView priority = card.findViewById(R.id.next_priority);
        ImageView icon = card.findViewById(R.id.next_icon);
        View chevron = card.findViewById(R.id.next_chevron);
        View add = card.findViewById(R.id.next_add);

        label.setText(personal ? R.string.home_next_personal : R.string.home_next_work);
        icon.setImageResource(personal ? R.drawable.ic_personal : R.drawable.ic_work);
        icon.setImageTintList(ColorStateList.valueOf(color));

        if (task == null) {
            // EmptyState do card: mensagem + botão para criar na aba certa
            when.setVisibility(View.GONE);
            priority.setVisibility(View.GONE);
            chevron.setVisibility(View.GONE);
            title.setText(Honorific.str(requireContext(),
                    personal ? R.string.home_next_personal_empty : R.string.home_next_work_empty));
            add.setVisibility(View.VISIBLE);
            add.setOnClickListener(v -> activity.openTab(tab, MainActivity.ACTION_NEW_TASK));
            card.setOnClickListener(null);
            card.setClickable(false);
            return;
        }

        long today = viewModel.getDay();
        String[] periods = getResources().getStringArray(R.array.period_options);
        String[] priorities = getResources().getStringArray(R.array.priority_options);
        when.setVisibility(View.VISIBLE);
        if (task.dueEpochDay < today) {
            when.setText(getString(R.string.overdue, DateUtils.formatDateShortOrYear(task.dueEpochDay)));
            when.setTextColor(ContextCompat.getColor(requireContext(), R.color.error));
        } else {
            String day = task.dueEpochDay == today ? getString(R.string.today_label)
                    : task.dueEpochDay == today + 1 ? getString(R.string.tomorrow_label)
                    : DateUtils.formatLongDay(LocalDate.ofEpochDay(task.dueEpochDay));
            when.setText(getString(R.string.home_next_when, day, periods[task.period]));
            when.setTextColor(color);
        }
        title.setText(task.title);
        priority.setVisibility(View.VISIBLE);
        stylePill(priority, getString(R.string.home_priority, priorities[task.priority]),
                ContextCompat.getColor(requireContext(), priorityColor(task.priority)));
        chevron.setVisibility(View.VISIBLE);
        add.setVisibility(View.GONE);
        card.setOnClickListener(v -> activity.showTab(tab));
    }

    private static int priorityColor(int priority) {
        switch (priority) {
            case Task.PRIORITY_HIGH: return R.color.priority_high;
            case Task.PRIORITY_LOW: return R.color.priority_low;
            default: return R.color.priority_medium;
        }
    }

    // ---------------- 5. Hábitos (só as bolinhas) ----------------

    /** Semana: bolinha dourada = TODOS os hábitos concluídos naquele dia. */
    private void renderHabits() {
        long today = viewModel.getDay();
        Map<Long, Set<Long>> doneByHabit = HabitsViewModel.groupByHabit(habitLogs);
        WeekDots.render(habitsWeek, DateUtils.currentWeekStart(), today,
                day -> HabitsViewModel.allDone(habits, doneByHabit, day), null);

        int doneToday = 0;
        for (Habit h : habits) {
            Set<Long> days = doneByHabit.get(h.id);
            if (days != null && days.contains(today)) doneToday++;
        }
        habitsCount.setText(habits.isEmpty() ? getString(R.string.home_habits_none)
                : getString(R.string.home_today_count, doneToday, habits.size()));
        boolean allDone = HabitsViewModel.allDone(habits, doneByHabit, today);
        habitsAllDone.setVisibility(allDone ? View.VISIBLE : View.GONE);
        if (allDone) habitsAllDone.setText(Honorific.str(requireContext(), R.string.today_habits_all_done));
    }

    // ---------------- 6. Compromissos do dia (bolinhas) ----------------

    /** Uma bolinha por tarefa com entrega hoje; cheia = concluída; todas concluídas = amarelas. */
    private void renderCommitments() {
        long today = viewModel.getDay();
        List<Task> work = new ArrayList<>();
        List<Task> personal = new ArrayList<>();
        for (Task t : tasks) {
            if (t.dueEpochDay != today) continue; // atrasadas não são "do dia"
            (t.isPersonal() ? personal : work).add(t);
        }
        int total = work.size() + personal.size();
        int done = countDone(work) + countDone(personal);
        boolean allDone = total > 0 && done == total;

        commitmentsCount.setText(total == 0 ? getString(R.string.home_commitments_free)
                : getResources().getQuantityString(R.plurals.home_commitments_count, total, done, total));
        commitmentsAllDone.setVisibility(allDone ? View.VISIBLE : View.GONE);
        if (allDone) commitmentsAllDone.setText(Honorific.str(requireContext(), R.string.home_commitments_all_done));

        bindCommitmentRow(rowWork, R.string.cat_work, R.color.cat_work, work, allDone);
        bindCommitmentRow(rowPersonal, R.string.cat_personal, R.color.cat_personal, personal, allDone);
    }

    private void bindCommitmentRow(View row, int label, int colorRes, List<Task> list, boolean allDone) {
        int color = ContextCompat.getColor(requireContext(), colorRes);
        row.findViewById(R.id.swatch).getBackground().mutate().setTint(color);
        ((TextView) row.findViewById(R.id.label)).setText(label);
        ChipGroup dots = row.findViewById(R.id.dots);
        TextView empty = row.findViewById(R.id.empty);
        dots.removeAllViews();
        dots.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
        empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);

        int size = dp(DOT_DP);
        for (Task t : list) {
            View dot = new View(requireContext());
            dot.setBackground(dotDrawable(t.isDone(), allDone));
            dots.addView(dot, new ViewGroup.LayoutParams(size, size));
        }
        row.setContentDescription(getString(R.string.home_category_a11y, getString(label), countDone(list), list.size()));
    }

    /** Pendente: só o contorno. Concluída: cheia (cinza claro; amarela quando o dia todo foi concluído). */
    private GradientDrawable dotDrawable(boolean done, boolean allDone) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        if (done) {
            d.setColor(ContextCompat.getColor(requireContext(), allDone ? R.color.highlight : R.color.accent));
        } else {
            d.setColor(0x00000000);
            d.setStroke(dp(1.5f), ContextCompat.getColor(requireContext(), R.color.text_secondary));
        }
        return d;
    }

    private static int countDone(List<Task> list) {
        int n = 0;
        for (Task t : list) if (t.isDone()) n++;
        return n;
    }

    // ---------------- Utilidades ----------------

    /** Etiqueta (pill) com fundo suave e texto na cor indicada. */
    private static void stylePill(TextView pill, String text, int color) {
        pill.setText(text);
        pill.setTextColor(color);
        pill.getBackground().mutate().setTint(ColorUtils.setAlphaComponent(color, 0x2E));
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
