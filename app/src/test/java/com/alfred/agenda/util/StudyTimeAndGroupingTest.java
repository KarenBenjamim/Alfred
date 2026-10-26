package com.alfred.agenda.util;

import static org.junit.Assert.assertEquals;

import com.alfred.agenda.data.entity.Task;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Cronômetro (divisão por dia), nomes das refeições e agrupamento do modo "Tarefas". */
public class StudyTimeAndGroupingTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    @Test
    public void cronometro_passandoDaMeiaNoite_divideEntreOsDias() {
        LocalDate day = LocalDate.of(2026, 10, 24);
        long start = day.atTime(23, 0).atZone(SP).toInstant().toEpochMilli();
        long end = day.plusDays(1).atTime(1, 30).atZone(SP).toInstant().toEpochMilli();
        Map<Long, Long> split = StudyTime.splitByDay(start, end, SP);
        assertEquals(2, split.size());
        assertEquals(Long.valueOf(3600), split.get(day.toEpochDay()));
        assertEquals(Long.valueOf(5400), split.get(day.plusDays(1).toEpochDay()));
    }

    @Test
    public void cronometro_mesmoDia() {
        LocalDate day = LocalDate.of(2026, 10, 24);
        long start = day.atTime(9, 0).atZone(SP).toInstant().toEpochMilli();
        Map<Long, Long> split = StudyTime.splitByDay(start, start + 25 * 60 * 1000, SP);
        assertEquals(Long.valueOf(1500), split.get(day.toEpochDay()));
    }

    @Test
    public void nomesDasOpcoesDeRefeicao() {
        assertEquals("Lanche", MealGroups.baseName("Lanche - Opção 2"));
        assertEquals("Opção 2", MealGroups.optionLabel("Lanche - Opção 2"));
        assertEquals("Almoço", MealGroups.baseName("Almoço"));
    }

    private static Task task(long id, long day, boolean done) {
        Task t = new Task();
        t.id = id;
        t.title = "T" + id;
        t.dueEpochDay = day;
        t.status = done ? Task.STATUS_DONE : Task.STATUS_PENDING;
        return t;
    }

    @Test
    public void agrupamento_atrasadasHojeAmanhaEFuturas() {
        long today = 100;
        List<Task> tasks = Arrays.asList(
                task(1, today + 5, false),
                task(2, today - 3, false),   // atrasada
                task(3, today - 1, true),    // concluída no passado: fica fora da lista
                task(4, today, false),
                task(5, today + 1, false));
        List<TaskGrouping.Section> s = TaskGrouping.upcoming(tasks, today);
        assertEquals(4, s.size());
        assertEquals(TaskGrouping.KIND_OVERDUE, s.get(0).kind);
        assertEquals(2, s.get(0).tasks.get(0).id);
        assertEquals(TaskGrouping.KIND_TODAY, s.get(1).kind);
        assertEquals(TaskGrouping.KIND_TOMORROW, s.get(2).kind);
        assertEquals(TaskGrouping.KIND_DATE, s.get(3).kind);
        assertEquals(today + 5, s.get(3).day);
    }
}
