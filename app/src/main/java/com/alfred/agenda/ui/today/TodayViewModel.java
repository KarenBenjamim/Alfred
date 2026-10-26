package com.alfred.agenda.ui.today;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.alfred.agenda.data.AlfredRepository;
import com.alfred.agenda.data.entity.Habit;
import com.alfred.agenda.data.entity.HabitLog;
import com.alfred.agenda.data.entity.MealLog;
import com.alfred.agenda.data.entity.MealWithItems;
import com.alfred.agenda.data.entity.StudyLog;
import com.alfred.agenda.data.entity.StudySubject;
import com.alfred.agenda.data.entity.Task;
import com.alfred.agenda.data.entity.WaterLog;
import com.alfred.agenda.util.DateUtils;
import com.alfred.agenda.util.LiveDataUtils;

import java.time.LocalDate;
import java.util.List;

/** Junta tudo o que aparece na tela Início. */
public class TodayViewModel extends AndroidViewModel {

    /** O "hoje" atual. Muda se o app ficar aberto e virar a meia-noite (ver refreshDay). */
    private final MutableLiveData<Long> day = new MutableLiveData<>(DateUtils.today());

    public final LiveData<List<MealWithItems>> meals;
    public final LiveData<List<MealLog>> mealLogs;
    public final LiveData<List<Habit>> habits;
    /** Hábitos concluídos na semana de hoje (segunda a domingo). */
    public final LiveData<List<HabitLog>> habitLogs;
    /** Tarefas com entrega hoje + atrasadas não concluídas. */
    public final LiveData<List<Task>> tasks;
    /** Tarefa pendente de maior prioridade de cada categoria. */
    public final LiveData<Task> nextPersonal;
    public final LiveData<Task> nextWork;
    public final LiveData<List<StudySubject>> subjects;
    /** Tempo de estudo a partir de hoje. */
    public final LiveData<List<StudyLog>> studyLogs;
    /** Água de hoje (null enquanto nada foi registrado). */
    public final LiveData<WaterLog> water;

    public TodayViewModel(@NonNull Application app) {
        super(app);
        AlfredRepository repo = AlfredRepository.get(app);
        meals = repo.observeMeals();
        habits = repo.observeHabits();
        subjects = repo.observeStudySubjects();
        nextPersonal = repo.observeTopPendingTask(Task.CATEGORY_PERSONAL);
        nextWork = repo.observeTopPendingTask(Task.CATEGORY_WORK);
        // Estas dependem do dia, então são recriadas quando "day" muda
        mealLogs = LiveDataUtils.switchMap(day, repo::observeMealLogs);
        habitLogs = LiveDataUtils.switchMap(day, d -> {
            long weekStart = d - (LocalDate.ofEpochDay(d).getDayOfWeek().getValue() - 1);
            return repo.observeHabitLogs(weekStart, weekStart + 6);
        });
        tasks = LiveDataUtils.switchMap(day, repo::observeTasksForToday);
        studyLogs = LiveDataUtils.switchMap(day, repo::observeStudyLogsSince);
        water = LiveDataUtils.switchMap(day, repo::observeWater);
    }

    /** Chamado ao voltar para a tela: se virou o dia, recarrega. */
    public void refreshDay() {
        long today = DateUtils.today();
        Long current = day.getValue();
        if (current == null || current != today) day.setValue(today);
    }

    public long getDay() {
        Long d = day.getValue();
        return d != null ? d : DateUtils.today();
    }
}
