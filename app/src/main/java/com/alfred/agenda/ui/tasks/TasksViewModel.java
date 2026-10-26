package com.alfred.agenda.ui.tasks;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.alfred.agenda.data.AlfredRepository;
import com.alfred.agenda.data.entity.Tag;
import com.alfred.agenda.data.entity.Task;
import com.alfred.agenda.data.entity.TaskTag;
import com.alfred.agenda.util.DateUtils;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

/**
 * ViewModel das abas Trabalho e Pessoal (cada aba tem a sua instância).
 * Guarda o filtro De/Até, que sobrevive à rotação da tela.
 */
public class TasksViewModel extends AndroidViewModel {

    /** Tamanho do filtro padrão: de hoje até 6 dias depois (uma semana). */
    private static final int DEFAULT_RANGE_DAYS = 6;

    private final AlfredRepository repo;
    private LiveData<List<Task>> tasks;

    /** Tags e ligações tarefa-tag (usadas só na aba Pessoal). */
    public final LiveData<List<Tag>> tags;
    public final LiveData<List<TaskTag>> taskTags;

    /** Início e fim do filtro (epochDay, inclusive). */
    public long rangeFrom = DateUtils.today();
    public long rangeTo = DateUtils.today() + DEFAULT_RANGE_DAYS;

    /** true depois do 1º toque no calendário: o próximo toque define o "Até". */
    public boolean awaitingRangeEnd = false;

    /** Modo da tela: Tarefas (lista) ou Agenda (calendário). -1 = ainda não escolhido. */
    public static final int MODE_LIST = 0;
    public static final int MODE_AGENDA = 1;
    public int mode = -1;

    /** Mês exibido no calendário. */
    public YearMonth shownMonth = YearMonth.from(LocalDate.ofEpochDay(rangeFrom));

    public TasksViewModel(@NonNull Application app) {
        super(app);
        repo = AlfredRepository.get(app);
        tags = repo.observeTags();
        taskTags = repo.observeTaskTags();
    }

    /** Tarefas da categoria (Task.CATEGORY_WORK ou Task.CATEGORY_PERSONAL). */
    public LiveData<List<Task>> getTasks(int category) {
        if (tasks == null) tasks = repo.observeTasks(category);
        return tasks;
    }

    /** Atalho de período: de "from" até "to" (inclusive). */
    public void setRange(long from, long to) {
        rangeFrom = from;
        rangeTo = to;
        awaitingRangeEnd = false;
        shownMonth = YearMonth.from(LocalDate.ofEpochDay(from));
    }

    /** Define o "De"; se ficar depois do "Até", o "Até" acompanha. */
    public void setFrom(long day) {
        rangeFrom = day;
        if (rangeTo < day) rangeTo = day;
        awaitingRangeEnd = false;
    }

    /** Define o "Até"; se ficar antes do "De", o "De" acompanha. */
    public void setTo(long day) {
        rangeTo = day;
        if (rangeFrom > day) rangeFrom = day;
        awaitingRangeEnd = false;
    }

    /**
     * Toque no calendário: o 1º toque marca um dia só (De = Até = dia);
     * o 2º toque estende até o dia tocado (em qualquer direção).
     */
    public void onCalendarTap(long day) {
        if (!awaitingRangeEnd) {
            rangeFrom = day;
            rangeTo = day;
            awaitingRangeEnd = true;
        } else {
            if (day < rangeFrom) rangeFrom = day;
            else rangeTo = day;
            awaitingRangeEnd = false;
        }
    }

    /** @param tagIds tags escolhidas; null = não mexer nas tags (Trabalho) */
    public void save(Task task, Set<Long> tagIds) {
        repo.saveTask(task, tagIds);
    }

    public void delete(Task task) {
        repo.deleteTask(task);
    }

    /** @param onSuccess chamado só se a gravação deu certo (pode ser null) */
    public void setDone(Task task, boolean done, @androidx.annotation.Nullable Runnable onSuccess) {
        repo.setTaskDone(task, done, onSuccess);
    }
}
