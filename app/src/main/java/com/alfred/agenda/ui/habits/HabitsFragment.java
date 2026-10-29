package com.alfred.agenda.ui.habits;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.alfred.agenda.MainActivity;
import com.alfred.agenda.R;
import com.alfred.agenda.data.entity.Habit;
import com.alfred.agenda.data.entity.HabitLog;
import com.alfred.agenda.ui.common.WaterGlassView;
import com.alfred.agenda.util.AlfredPhrases;
import com.alfred.agenda.util.DateUtils;
import com.alfred.agenda.util.Honorific;
import com.alfred.agenda.util.WaterGoal;
import com.alfred.agenda.util.EmptyState;
import com.alfred.agenda.util.UiUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputLayout;

import java.text.NumberFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aba "Hábitos":
 *  - água de hoje: copo, "X ml de Y ml" (meta pelo peso do perfil), +200/+300/+500 ml e "Ajustar";
 *  - resumo da semana e um card compacto por hábito, com as bolinhas
 *    de segunda a domingo (dourada = concluído naquele dia).
 */
public class HabitsFragment extends Fragment {

    private HabitsViewModel viewModel;
    private HabitAdapter adapter;
    private View emptyState;
    private View summaryCard;
    private View listTitle;
    private TextView weekPercent;
    private TextView weekDetail;
    private LinearProgressIndicator weekBar;
    private List<Habit> habits = new ArrayList<>();
    private List<HabitLog> logs = new ArrayList<>();
    private WaterGlassView waterGlass;
    private TextView waterAmount;
    private TextView waterDetail;
    private int waterMl;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_habits, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(HabitsViewModel.class);

        RecyclerView list = view.findViewById(R.id.list);
        emptyState = view.findViewById(R.id.empty_state);
        summaryCard = view.findViewById(R.id.summary_card);
        listTitle = view.findViewById(R.id.list_title);
        weekPercent = view.findViewById(R.id.week_percent);
        weekDetail = view.findViewById(R.id.week_detail);
        weekBar = view.findViewById(R.id.week_bar);

        adapter = new HabitAdapter(new HabitAdapter.Listener() {
            @Override
            public void onEdit(Habit habit) {
                HabitDialog.show(requireContext(), habit, viewModel::save);
            }

            @Override
            public void onDelete(Habit habit) {
                UiUtils.confirmDelete(requireContext(), habit.name, () -> viewModel.delete(habit));
            }

            @Override
            public void onDoneChanged(Habit habit, long epochDay, boolean done) {
                viewModel.setDone(habit.id, epochDay, done, done ? () -> {
                    if (isAdded()) ((MainActivity) requireActivity()).celebrateGoal();
                } : null);
            }
        });
        list.setAdapter(adapter);

        viewModel.habits.observe(getViewLifecycleOwner(), items -> {
            habits = items;
            render();
        });
        viewModel.weekLogs.observe(getViewLifecycleOwner(), items -> {
            logs = items;
            render();
        });

        view.findViewById(R.id.fab).setOnClickListener(v -> newHabit());

        // Água
        waterGlass = view.findViewById(R.id.water_glass);
        waterAmount = view.findViewById(R.id.water_amount);
        waterDetail = view.findViewById(R.id.water_detail);
        LinearLayout buttons = view.findViewById(R.id.water_buttons);
        for (int i = 0; i < WaterGoal.QUICK_ADD_ML.length; i++) {
            int ml = WaterGoal.QUICK_ADD_ML[i];
            MaterialButton b = (MaterialButton) getLayoutInflater()
                    .inflate(R.layout.item_water_button, buttons, false);
            b.setText(getString(R.string.water_add, ml));
            b.setContentDescription(getString(R.string.water_add_a11y, ml));
            b.setOnClickListener(v -> addWater(ml));
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) b.getLayoutParams();
            if (i > 0) lp.setMarginStart(dp(8));
            buttons.addView(b, lp);
        }
        view.findViewById(R.id.water_adjust).setOnClickListener(v -> adjustWater());
        viewModel.water.observe(getViewLifecycleOwner(), w -> {
            waterMl = w == null ? 0 : w.ml;
            renderWater();
        });
    }

    // ---------------- Água ----------------

    private void renderWater() {
        int goal = WaterGoal.goalMl(requireContext());
        NumberFormat n = NumberFormat.getIntegerInstance(DateUtils.PT_BR);
        waterGlass.setWater(waterMl, goal);
        if (goal <= 0) {
            waterAmount.setText(getString(R.string.water_amount_no_goal, n.format(waterMl)));
            waterDetail.setText(R.string.water_no_weight);
        } else {
            waterAmount.setText(getString(R.string.water_amount, n.format(waterMl), n.format(goal)));
            waterDetail.setText(waterMl >= goal ? Honorific.str(requireContext(), R.string.water_goal_reached)
                    : getString(R.string.water_remaining, n.format(goal - waterMl)));
        }
    }

    /** Soma ao dia; se a meta acabou de ser atingida, o Alfred comenta. */
    private void addWater(int ml) {
        int before = waterMl;
        viewModel.addWater(ml, total -> {
            if (!isAdded()) return;
            int goal = WaterGoal.goalMl(requireContext());
            MainActivity activity = (MainActivity) requireActivity();
            if (goal > 0 && before < goal && total >= goal) {
                activity.showMessage(Honorific.str(requireContext(), R.string.water_goal_reached));
            } else {
                activity.showMessage(getString(R.string.water_added, ml));
            }
        });
    }

    /** Corrige o total de hoje (ex.: registrou a mais por engano). */
    private void adjustWater() {
        View content = getLayoutInflater().inflate(R.layout.dialog_text_input, null);
        TextInputLayout layout = content.findViewById(R.id.layout_text);
        EditText input = content.findViewById(R.id.input_text);
        layout.setHint(R.string.water_adjust_hint);
        layout.setSuffixText(getString(R.string.unit_ml));
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(waterMl));
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.water_adjust_title)
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            int ml;
            try {
                ml = Integer.parseInt(UiUtils.text(input));
            } catch (NumberFormatException e) {
                ml = -1;
            }
            if (ml < 0 || ml > WaterGoal.MAX_DAY_ML) {
                layout.setError(getString(R.string.water_adjust_invalid, WaterGoal.MAX_DAY_ML));
                return;
            }
            viewModel.setWater(ml, total -> {
                if (isAdded()) ((MainActivity) requireActivity()).showMessage(AlfredPhrases.saved(requireContext()));
            });
            dialog.dismiss();
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void newHabit() {
        HabitDialog.show(requireContext(), null, viewModel::save);
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.refreshWeek();
        render(); // o dia de hoje pode ter mudado
        renderWater(); // e o peso (meta) pode ter mudado no Perfil
    }

    private void render() {
        if (adapter == null) return;
        long today = DateUtils.today();
        long weekStart = viewModel.getWeekStart();
        Map<Long, Set<Long>> doneByHabit = HabitsViewModel.groupByHabit(logs);
        adapter.setData(habits, doneByHabit, weekStart, today);

        boolean empty = habits.isEmpty();
        summaryCard.setVisibility(empty ? View.GONE : View.VISIBLE);
        listTitle.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) {
            EmptyState.show(emptyState, R.drawable.ic_habits, R.string.habits_empty_title,
                    R.string.habits_empty, R.string.create_habit, this::newHabit);
            return;
        }
        EmptyState.hide(emptyState);

        // Resumo: marcações da semana até hoje / (hábitos x dias decorridos)
        int days = (int) (today - weekStart + 1);
        int possible = habits.size() * days;
        int done = 0, doneToday = 0;
        for (Habit h : habits) {
            Set<Long> set = doneByHabit.get(h.id);
            if (set == null) continue;
            for (long d = weekStart; d <= today; d++) if (set.contains(d)) done++;
            if (set.contains(today)) doneToday++;
        }
        int percent = possible == 0 ? 0 : Math.round(100f * done / possible);
        weekPercent.setText(getString(R.string.percent_value, percent));
        weekBar.setProgressCompat(percent, true);
        weekDetail.setText(getString(R.string.habits_week_detail, doneToday, habits.size(), done, possible));
    }
}
