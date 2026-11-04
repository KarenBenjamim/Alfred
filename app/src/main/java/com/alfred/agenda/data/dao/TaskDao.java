package com.alfred.agenda.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.alfred.agenda.data.entity.Task;

import java.util.List;

/**
 * Consultas das tarefas de Trabalho e Pessoal. Status: 0 pendente, 1 em andamento,
 * 2 concluída, 3 cancelada; "status >= 2" = encerrada (fora das pendências).
 * Hora: startMinutes -1 = sem hora; o dia inteiro (allDay) vem antes das com hora.
 */
@Dao
public interface TaskDao {

    /** Tarefas de uma categoria: pendentes primeiro, depois por data, período e prioridade. */
    @Query("SELECT * FROM tasks WHERE category = :category "
            + "ORDER BY status >= 2, dueEpochDay, period, allDay DESC, startMinutes, priority DESC, title")
    LiveData<List<Task>> observeByCategory(int category);

    /**
     * Para a aba Hoje: tudo (trabalho e pessoal) que vence no dia,
     * mais o que está atrasado e ainda não foi concluído.
     */
    @Query("SELECT * FROM tasks WHERE dueEpochDay = :epochDay "
            + "OR (dueEpochDay < :epochDay AND status < 2) "
            + "ORDER BY status >= 2, dueEpochDay, period, allDay DESC, startMinutes, category, priority DESC, title")
    LiveData<List<Task>> observeForToday(long epochDay);

    /**
     * Para a tela Início: a tarefa pendente (não concluída, inclusive atrasada) de MAIOR
     * prioridade da categoria; no empate, a de entrega mais próxima.
     */
    @Query("SELECT * FROM tasks WHERE category = :category AND status < 2 "
            + "ORDER BY priority DESC, dueEpochDay, period, allDay DESC, startMinutes, title LIMIT 1")
    LiveData<Task> observeTopPending(int category);

    @Query("SELECT * FROM tasks")
    List<Task> getAllSync();

    @Query("SELECT * FROM tasks WHERE dueEpochDay = :epochDay")
    List<Task> getForDaySync(long epochDay);

    @Query("SELECT * FROM tasks WHERE id = :id")
    Task getById(long id);

    @Insert
    long insert(Task task);

    @Update
    void update(Task task);

    @Delete
    void delete(Task task);

    // ---- Expurgo ----

    /** Apaga tarefas com entrega antes de :limitDay. Devolve quantas foram apagadas. */
    @Query("DELETE FROM tasks WHERE dueEpochDay < :limitDay")
    int deleteOlderThan(long limitDay);
}
