package com.alfred.agenda.ui.habits;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.alfred.agenda.data.AlfredRepository;
import com.alfred.agenda.data.entity.Habit;
import com.alfred.agenda.data.entity.HabitLog;
import com.alfred.agenda.data.entity.WaterLog;
import com.alfred.agenda.util.DateUtils;
import com.alfred.agenda.util.LiveDataUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;

public class HabitsViewModel extends AndroidViewModel {

    private final AlfredRepository repo;

    /** Segunda-feira da semana exibida. Muda se o app ficar aberto e virar a semana. */
    private final MutableLiveData<Long> weekStart = new MutableLiveData<>(DateUtils.currentWeekStart());

    /** Dia da água exibida. Muda se o app ficar aberto e virar o dia (o consumo "zera"). */
    private final MutableLiveData<Long> day = new MutableLiveData<>(DateUtils.today());

    public final LiveData<List<Habit>> habits;
    public final LiveData<List<HabitLog>> weekLogs;
    /** Água de hoje (null enquanto nada foi registrado). */
    public final LiveData<WaterLog> water;

    public HabitsViewModel(@NonNull Application app) {
        super(app);
        repo = AlfredRepository.get(app);
        habits = repo.observeHabits();
        weekLogs = LiveDataUtils.switchMap(weekStart, ws -> repo.observeHabitLogs(ws, ws + 6));
        water = LiveDataUtils.switchMap(day, repo::observeWater);
    }

    /** Chamado ao voltar para a tela: se virou a semana (ou o dia), recarrega. */
    public void refreshWeek() {
        long current = DateUtils.currentWeekStart();
        Long shown = weekStart.getValue();
        if (shown == null || shown != current) weekStart.setValue(current);
        long today = DateUtils.today();
        Long shownDay = day.getValue();
        if (shownDay == null || shownDay != today) day.setValue(today);
    }

    public long getDay() {
        Long d = day.getValue();
        return d != null ? d : DateUtils.today();
    }

    /** @param onSuccess recebe o novo total do dia, só se a gravação deu certo */
    public void addWater(int ml, @androidx.annotation.Nullable IntConsumer onSuccess) {
        repo.addWater(getDay(), ml, onSuccess);
    }

    public void setWater(int ml, @androidx.annotation.Nullable IntConsumer onSuccess) {
        repo.setWater(getDay(), ml, onSuccess);
    }

    public long getWeekStart() {
        Long ws = weekStart.getValue();
        return ws != null ? ws : DateUtils.currentWeekStart();
    }

    public void save(Habit habit) {
        repo.saveHabit(habit);
    }

    public void delete(Habit habit) {
        repo.deleteHabit(habit);
    }

    /** @param onSuccess chamado só se a gravação deu certo (pode ser null) */
    public void setDone(long habitId, long epochDay, boolean done, @androidx.annotation.Nullable Runnable onSuccess) {
        repo.setHabitDone(habitId, epochDay, done, onSuccess);
    }

    /** Agrupa os registros por hábito: habitId -> dias (epochDay) em que foi concluído. */
    public static Map<Long, Set<Long>> groupByHabit(List<HabitLog> logs) {
        Map<Long, Set<Long>> map = new HashMap<>();
        for (HabitLog log : logs) {
            map.computeIfAbsent(log.habitId, k -> new HashSet<>()).add(log.epochDay);
        }
        return map;
    }

    /** true se TODOS os hábitos foram concluídos no dia (e existe ao menos um hábito). */
    public static boolean allDone(List<Habit> habits, Map<Long, Set<Long>> doneByHabit, long day) {
        if (habits.isEmpty()) return false;
        for (Habit h : habits) {
            Set<Long> days = doneByHabit.get(h.id);
            if (days == null || !days.contains(day)) return false;
        }
        return true;
    }
}
