package com.alfred.agenda.util;

import com.alfred.agenda.data.entity.Task;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agrupamento do modo "Tarefas" (Trabalho e Pessoal): Atrasadas, Hoje, Amanhã e as próximas
 * datas. Tarefas concluídas de dias que já passaram não aparecem (ficam no modo Agenda).
 */
public final class TaskGrouping {

    public static final int KIND_OVERDUE = 0;
    public static final int KIND_TODAY = 1;
    public static final int KIND_TOMORROW = 2;
    public static final int KIND_DATE = 3;

    /** Ordem dentro de um grupo: data, período, pendentes antes das concluídas, prioridade, título. */
    public static final Comparator<Task> ORDER = Comparator
            .comparingLong((Task t) -> t.dueEpochDay)
            .thenComparingInt(t -> t.period)
            .thenComparing(Task::isDone)
            .thenComparing((Task t) -> -t.priority)
            .thenComparing(t -> t.title);

    private TaskGrouping() {
    }

    public static final class Section {
        public final int kind;
        /** Dia do grupo (para KIND_DATE, KIND_TODAY e KIND_TOMORROW). */
        public final long day;
        public final List<Task> tasks = new ArrayList<>();

        Section(int kind, long day) {
            this.kind = kind;
            this.day = day;
        }
    }

    public static List<Section> upcoming(List<Task> tasks, long today) {
        List<Task> sorted = new ArrayList<>(tasks);
        sorted.sort(ORDER);
        Map<Long, Section> byDay = new LinkedHashMap<>();
        Section overdue = new Section(KIND_OVERDUE, today - 1);
        for (Task t : sorted) {
            if (t.dueEpochDay < today) {
                if (!t.isDone()) overdue.tasks.add(t);
                continue;
            }
            Section s = byDay.get(t.dueEpochDay);
            if (s == null) {
                int kind = t.dueEpochDay == today ? KIND_TODAY
                        : t.dueEpochDay == today + 1 ? KIND_TOMORROW : KIND_DATE;
                s = new Section(kind, t.dueEpochDay);
                byDay.put(t.dueEpochDay, s);
            }
            s.tasks.add(t);
        }
        List<Section> out = new ArrayList<>();
        if (!overdue.tasks.isEmpty()) out.add(overdue);
        out.addAll(byDay.values());
        return out;
    }
}
