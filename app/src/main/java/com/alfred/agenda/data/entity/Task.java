package com.alfred.agenda.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Tarefa com data de entrega. A mesma tabela guarda as tarefas de Trabalho e as
 * Pessoais; o campo "category" diz a qual aba cada uma pertence.
 */
@Entity(tableName = "tasks")
public class Task {

    // Categoria da tarefa: em qual aba ela aparece
    public static final int CATEGORY_WORK = 0;
    public static final int CATEGORY_PERSONAL = 1;

    // Valores batem com a posição em R.array.priority_options
    public static final int PRIORITY_LOW = 0;
    public static final int PRIORITY_MEDIUM = 1;
    public static final int PRIORITY_HIGH = 2;

    // Valores batem com a posição em R.array.status_options
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_IN_PROGRESS = 1;
    public static final int STATUS_DONE = 2;

    // Valores batem com a posição em R.array.period_options
    public static final int PERIOD_MORNING = 0;
    public static final int PERIOD_AFTERNOON = 1;
    public static final int PERIOD_NIGHT = 2;

    /** Horário do lembrete de cada período: manhã 08:00, tarde 13:00, noite 18:00. */
    public static final int[] PERIOD_REMINDER_MINUTES = {8 * 60, 13 * 60, 18 * 60};

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String title = "";

    @NonNull
    public String description = "";

    /** Data de entrega em LocalDate.toEpochDay(). */
    public long dueEpochDay;

    public int priority = PRIORITY_MEDIUM;

    public int status = STATUS_PENDING;

    // defaultValue precisa bater com o DEFAULT usado na migração 1 -> 2 (AppDatabase)
    @ColumnInfo(defaultValue = "0")
    public int category = CATEGORY_WORK;

    @ColumnInfo(defaultValue = "0")
    public int period = PERIOD_MORNING;

    public boolean isDone() {
        return status == STATUS_DONE;
    }

    public boolean isPersonal() {
        return category == CATEGORY_PERSONAL;
    }

    /** Quantos períodos cada categoria usa: Trabalho = manhã/tarde; Pessoal = manhã/tarde/noite. */
    public static int periodCount(int category) {
        return category == CATEGORY_PERSONAL ? 3 : 2;
    }

    /** Cópia simples (para alterar sem mexer no objeto exibido na lista). */
    public Task copy() {
        Task t = new Task();
        t.id = id;
        t.title = title;
        t.description = description;
        t.dueEpochDay = dueEpochDay;
        t.priority = priority;
        t.status = status;
        t.category = category;
        t.period = period;
        return t;
    }
}
