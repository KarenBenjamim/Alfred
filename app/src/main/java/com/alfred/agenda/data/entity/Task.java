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
    /** Não vai acontecer: fica riscada no histórico e não conta como pendente nem como feita. */
    public static final int STATUS_CANCELED = 3;

    // Tipo de demanda (só Trabalho). Valores batem com a posição em R.array.demand_types
    public static final int TYPE_MEETING = 0;
    public static final int TYPE_DELIVERY = 1;
    public static final int TYPE_DEVELOPMENT = 2;
    public static final int TYPE_EXTERNAL = 3;
    public static final int TYPE_TRAINING = 4;
    public static final int TYPE_OTHER = 5;
    public static final int TYPE_COUNT = 6;

    /** Lembrete de uma tarefa de "dia inteiro". */
    public static final int ALL_DAY_REMINDER_MINUTES = 8 * 60;

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

    // ---- Trabalho (versão 9) ----

    /** Tipo de demanda (TYPE_*). */
    @ColumnInfo(defaultValue = "5")
    public int demandType = TYPE_OTHER;

    /** Compromisso do dia inteiro (sem hora). */
    @ColumnInfo(defaultValue = "0")
    public boolean allDay;

    /** Hora inicial e final em minutos do dia; -1 = sem hora (tarefas antigas usam o período). */
    @ColumnInfo(defaultValue = "-1")
    public int startMinutes = -1;

    @ColumnInfo(defaultValue = "-1")
    public int endMinutes = -1;

    /** Dia em que a tarefa estava marcada antes de ser adiada (0 = nunca adiada). */
    @ColumnInfo(defaultValue = "0")
    public long postponedFrom;

    public boolean isDone() {
        return status == STATUS_DONE;
    }

    public boolean isCanceled() {
        return status == STATUS_CANCELED;
    }

    /** Concluída ou cancelada: sem lembrete, sem atraso, fora das pendências. */
    public boolean isClosed() {
        return status == STATUS_DONE || status == STATUS_CANCELED;
    }

    public boolean hasTime() {
        return !allDay && startMinutes >= 0;
    }

    /** Minuto do lembrete: a hora inicial, 08:00 no dia inteiro, ou o início do período. */
    public int reminderMinutes() {
        if (allDay) return ALL_DAY_REMINDER_MINUTES;
        if (startMinutes >= 0) return startMinutes;
        return PERIOD_REMINDER_MINUTES[Math.max(0, Math.min(PERIOD_REMINDER_MINUTES.length - 1, period))];
    }

    /**
     * Período (Manhã/Tarde) que corresponde ao horário escolhido: assim as tarefas com hora
     * continuam agrupadas e ordenadas como as antigas. Dia inteiro fica na manhã.
     */
    public static int periodFor(boolean allDay, int startMinutes) {
        if (allDay || startMinutes < 0) return PERIOD_MORNING;
        return startMinutes < 13 * 60 ? PERIOD_MORNING : PERIOD_AFTERNOON;
    }

    /** Adia para outro dia (guarda o dia original da primeira vez) e volta a ficar pendente. */
    public void postponeTo(long newDay) {
        if (postponedFrom == 0) postponedFrom = dueEpochDay;
        dueEpochDay = newDay;
        status = STATUS_PENDING;
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
        t.demandType = demandType;
        t.allDay = allDay;
        t.startMinutes = startMinutes;
        t.endMinutes = endMinutes;
        t.postponedFrom = postponedFrom;
        return t;
    }
}
