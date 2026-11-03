package com.alfred.agenda.cloud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.alfred.agenda.data.entity.Meal;
import com.alfred.agenda.data.entity.MealItem;
import com.alfred.agenda.data.entity.StudySubject;
import com.alfred.agenda.data.entity.Task;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Conversão entre as entidades do app e os documentos do Firestore (formato compartilhado com a web). */
public class CloudSchemaTest {

    private static final long DAY = LocalDate.of(2026, 11, 3).toEpochDay();

    @Test
    public void newIdsArePositiveAndSafeForJavaScript() {
        for (int i = 0; i < 1000; i++) {
            long id = CloudSchema.newId();
            assertTrue(id > 0);
            assertTrue(id <= (1L << 53) - 1);
        }
    }

    @Test
    public void docIdsRoundTripAndGarbageIsIgnored() {
        assertEquals(42L, CloudSchema.parseId(CloudSchema.docId(42)));
        assertEquals(0, CloudSchema.parseId("abc"));
        assertEquals(0, CloudSchema.parseId("-5"));
        assertEquals(0, CloudSchema.parseId(Long.toString(1L << 53))); // grande demais para o JS
        assertEquals(0, CloudSchema.parseId(null));
    }

    @Test
    public void taskRoundTripKeepsEveryField() {
        Task t = new Task();
        t.id = 7;
        t.title = "Pagar a conta de luz";
        t.description = "Vence às 18h.";
        t.dueEpochDay = DAY;
        t.priority = Task.PRIORITY_HIGH;
        t.status = Task.STATUS_IN_PROGRESS;
        t.category = Task.CATEGORY_PERSONAL;
        t.period = Task.PERIOD_NIGHT;
        Map<String, Object> doc = CloudSchema.task(t, Arrays.asList(3L, 9L));
        assertEquals("2026-11-03", doc.get("dia"));
        assertEquals("pessoal", doc.get("categoria"));

        Task back = CloudSchema.task(7, doc);
        assertEquals(t.title, back.title);
        assertEquals(t.description, back.description);
        assertEquals(DAY, back.dueEpochDay);
        assertEquals(t.priority, back.priority);
        assertEquals(t.status, back.status);
        assertEquals(t.category, back.category);
        assertEquals(t.period, back.period);
        assertEquals(Arrays.asList(3L, 9L), CloudSchema.taskTags(doc));
    }

    @Test
    public void malformedTaskIsSafe() {
        Map<String, Object> doc = new HashMap<>();
        doc.put("dia", "ontem"); // data inválida: documento ignorado
        assertNull(CloudSchema.task(1, doc));

        doc.put("dia", "2026-11-03");
        doc.put("titulo", 123);            // tipo errado -> padrão
        doc.put("prioridade", 99);         // fora da faixa -> limitado
        doc.put("tags", Arrays.asList(5L, "x", -1L, 5L));
        Task t = CloudSchema.task(1, doc);
        assertEquals("", t.title);
        assertEquals(Task.PRIORITY_HIGH, t.priority);
        assertEquals(Collections.singletonList(5L), CloudSchema.taskTags(doc));
    }

    @Test
    public void firestoreDoublesAreReadAsNumbers() {
        Map<String, Object> doc = new HashMap<>();
        doc.put("dia", "2026-11-03");
        doc.put("status", 2.0); // a web pode gravar 2 como double
        assertEquals(Task.STATUS_DONE, CloudSchema.task(1, doc).status);
    }

    @Test
    public void mealWithItemsRoundTrip() {
        Meal meal = new Meal();
        meal.id = 11;
        meal.name = "Café da manhã";
        meal.timeMinutes = 7 * 60 + 30;
        meal.notifyEnabled = false;
        MealItem it = new MealItem();
        it.description = "Pão integral";
        it.quantity = "2 fatias";
        it.substitutions = "Tapioca";
        it.groupNumber = 2;
        it.groupPortions = "1";
        Map<String, Object> doc = CloudSchema.meal(meal, Collections.singletonList(it));
        assertEquals("07:30", doc.get("horario"));
        assertFalse(doc.containsKey(CloudSchema.MEAL_DONE)); // "feitas" só por arrayUnion

        Meal back = CloudSchema.meal(11, doc);
        assertEquals(7 * 60 + 30, back.timeMinutes);
        assertFalse(back.notifyEnabled);
        List<MealItem> items = CloudSchema.mealItems(11, doc);
        assertEquals(1, items.size());
        assertEquals("Pão integral", items.get(0).description);
        assertEquals(2, items.get(0).groupNumber);
        assertEquals(11, items.get(0).mealId);
    }

    @Test
    public void invalidTimeFallsBack() {
        assertEquals(8 * 60, CloudSchema.parseTime("25:00", 8 * 60));
        assertEquals(8 * 60, CloudSchema.parseTime("abc", 8 * 60));
        assertEquals(8 * 60, CloudSchema.parseTime(null, 8 * 60));
    }

    @Test
    public void doneDaysIgnoreGarbageAndDuplicates() {
        assertEquals(Arrays.asList(DAY, DAY - 1),
                CloudSchema.days(Arrays.asList("2026-11-03", "lixo", "2026-11-02", "2026-11-03", 5)));
    }

    @Test
    public void subjectKeepsModuleRunningAndTimeOutOfTheMerge() {
        StudySubject s = new StudySubject();
        s.id = 5;
        s.topicId = 1;
        s.moduleId = 2L;
        s.name = "Verbos";
        s.completed = true;
        s.runningSince = 1234;
        Map<String, Object> doc = CloudSchema.subject(s);
        assertFalse(doc.containsKey(CloudSchema.SUBJECT_TIME)); // tempo só por increment
        StudySubject back = CloudSchema.subject(5, doc);
        assertEquals(Long.valueOf(2), back.moduleId);
        assertTrue(back.completed);
        assertEquals(1234, back.runningSince);

        doc.put("moduloId", null);
        assertNull(CloudSchema.subject(5, doc).moduleId);
        doc.put("materiaId", 0L); // sem matéria: ignorado
        assertNull(CloudSchema.subject(5, doc));
    }

    @Test
    public void timeByDayKeepsOnlyValidPositiveEntries() {
        Map<String, Object> time = new HashMap<>();
        time.put("2026-11-03", 1830L);
        time.put("2026-11-02", 0L);
        time.put("lixo", 99L);
        time.put("2026-11-01", "muito");
        Map<String, Object> doc = new HashMap<>();
        doc.put(CloudSchema.SUBJECT_TIME, time);
        Map<Long, Long> byDay = CloudSchema.timeByDay(doc);
        assertEquals(1, byDay.size());
        assertEquals(Long.valueOf(1830), byDay.get(DAY));
    }
}
