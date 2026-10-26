package com.alfred.agenda.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.alfred.agenda.data.entity.Habit;
import com.alfred.agenda.data.entity.Meal;
import com.alfred.agenda.data.entity.MealWithItems;
import com.alfred.agenda.data.entity.StudySubject;
import com.alfred.agenda.data.entity.Task;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** Critério do progresso do dia (anel da tela Início) e do "todas as metas concluídas". */
public class DayProgressTest {

    private static final long TODAY = 20000;

    private static Task task(long id, int category, long day, boolean done) {
        Task t = new Task();
        t.id = id;
        t.category = category;
        t.dueEpochDay = day;
        t.status = done ? Task.STATUS_DONE : Task.STATUS_PENDING;
        return t;
    }

    private static Habit habit(long id) {
        Habit h = new Habit();
        h.id = id;
        return h;
    }

    private static MealWithItems meal(long id, String name, int minutes) {
        MealWithItems m = new MealWithItems();
        m.meal = new Meal();
        m.meal.id = id;
        m.meal.name = name;
        m.meal.timeMinutes = minutes;
        m.items = new ArrayList<>();
        return m;
    }

    private static StudySubject subject(long id) {
        StudySubject s = new StudySubject();
        s.id = id;
        return s;
    }

    @Test
    public void somaDasCategorias_bateComOTotal() {
        List<Task> tasks = Arrays.asList(
                task(1, Task.CATEGORY_WORK, TODAY, true),
                task(2, Task.CATEGORY_WORK, TODAY, false),
                task(3, Task.CATEGORY_PERSONAL, TODAY, true),
                task(4, Task.CATEGORY_WORK, TODAY - 2, false)); // atrasada: fora do percentual do dia
        List<MealGroups.Group> meals = MealGroups.group(Arrays.asList(
                meal(10, "Café da manhã", 480),
                meal(11, "Lanche - Opção 1", 1020),
                meal(12, "Lanche - Opção 2", 1020)));
        DayProgress p = DayProgress.compute(TODAY, tasks,
                Arrays.asList(habit(1), habit(2)), new HashSet<>(Collections.singletonList(1L)),
                meals, new HashSet<>(Collections.singletonList(12L)),
                Arrays.asList(subject(1), subject(2), subject(3)), new HashSet<>(Collections.singletonList(2L)));

        assertEquals(1, p.work.done);
        assertEquals(2, p.work.total);          // a atrasada não conta
        assertEquals(1, p.personal.done);
        assertEquals(1, p.personal.total);
        assertEquals(1, p.habits.done);
        assertEquals(2, p.habits.total);
        assertEquals(1, p.diet.done);           // Opção 2 marcada conta para o "Lanche"
        assertEquals(2, p.diet.total);          // 3 opções viram 2 refeições
        assertEquals(1, p.study.done);
        assertEquals(3, p.study.total);
        assertEquals(1 + 1 + 1 + 1 + 1, p.done());
        assertEquals(2 + 1 + 2 + 2 + 3, p.total());
        assertFalse(p.allDone());
    }

    @Test
    public void tudoFeito_allDone() {
        DayProgress p = DayProgress.compute(TODAY,
                Collections.singletonList(task(1, Task.CATEGORY_PERSONAL, TODAY, true)),
                Collections.singletonList(habit(1)), new HashSet<>(Collections.singletonList(1L)),
                Collections.emptyList(), new HashSet<>(),
                Collections.emptyList(), new HashSet<>());
        assertTrue(p.allDone());
        assertEquals(1f, p.fraction(), 0.0001f);
    }

    @Test
    public void semMetas_naoComemora() {
        DayProgress p = DayProgress.compute(TODAY, Collections.emptyList(), Collections.emptyList(),
                new HashSet<>(), Collections.emptyList(), new HashSet<>(), Collections.emptyList(), new HashSet<>());
        assertFalse(p.allDone());
        assertEquals(0f, p.fraction(), 0f);
    }
}
