package com.alfred.agenda.cloud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.alfred.agenda.data.entity.Tag;
import com.alfred.agenda.data.entity.Task;
import com.alfred.agenda.util.WorkTagColors;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Trabalho: tipo de demanda, horário, adiar/cancelar e tags por grupo de cor. */
public class WorkTasksTest {

    private static final long DAY = LocalDate.of(2026, 11, 4).toEpochDay();

    private static Task meeting() {
        Task t = new Task();
        t.id = 9;
        t.title = "Daily do projeto";
        t.dueEpochDay = DAY;
        t.category = Task.CATEGORY_WORK;
        t.demandType = Task.TYPE_MEETING;
        t.startMinutes = 14 * 60;
        t.endMinutes = 15 * 60 + 30;
        t.period = Task.periodFor(false, t.startMinutes);
        return t;
    }

    @Test
    public void workFieldsRoundTripThroughTheCloud() {
        Task t = meeting();
        t.postponedFrom = DAY - 2;
        Map<String, Object> doc = CloudSchema.task(t, Collections.emptyList());
        assertEquals("14:00", doc.get("inicio"));
        assertEquals("15:30", doc.get("fim"));
        assertEquals("2026-11-02", doc.get("adiadaDe"));
        Task back = CloudSchema.task(9, doc);
        assertEquals(Task.TYPE_MEETING, back.demandType);
        assertFalse(back.allDay);
        assertEquals(14 * 60, back.startMinutes);
        assertEquals(15 * 60 + 30, back.endMinutes);
        assertEquals(DAY - 2, back.postponedFrom);
    }

    @Test
    public void oldDocumentWithoutWorkFieldsKeepsThePeriod() {
        Map<String, Object> doc = new HashMap<>();
        doc.put("dia", "2026-11-04");
        doc.put("periodo", 1L);
        Task t = CloudSchema.task(1, doc);
        assertEquals(Task.TYPE_OTHER, t.demandType);
        assertFalse(t.hasTime());
        assertEquals(0, t.postponedFrom);
        assertEquals(13 * 60, t.reminderMinutes()); // lembrete no início do período, como antes
    }

    @Test
    public void allDayHasNoTimesAndRemindsAtEight() {
        Task t = meeting();
        t.allDay = true;
        t.startMinutes = -1;
        t.endMinutes = -1;
        Map<String, Object> doc = CloudSchema.task(t, Collections.emptyList());
        assertNull(doc.get("inicio"));
        assertEquals(Boolean.TRUE, doc.get("diaInteiro"));
        assertEquals(Task.ALL_DAY_REMINDER_MINUTES, CloudSchema.task(9, doc).reminderMinutes());
    }

    @Test
    public void reminderAtTheStartTimeAndGroupedByPeriod() {
        Task t = meeting();
        assertEquals(14 * 60, t.reminderMinutes());
        assertEquals(Task.PERIOD_AFTERNOON, Task.periodFor(false, 14 * 60));
        assertEquals(Task.PERIOD_MORNING, Task.periodFor(false, 9 * 60));
        assertEquals(Task.PERIOD_MORNING, Task.periodFor(true, -1));
    }

    @Test
    public void postponingMovesTheDayAndRemembersTheOriginal() {
        Task t = meeting();
        t.status = Task.STATUS_IN_PROGRESS;
        t.postponeTo(DAY + 3);
        assertEquals(DAY + 3, t.dueEpochDay);
        assertEquals(DAY, t.postponedFrom);
        assertEquals(Task.STATUS_PENDING, t.status); // volta a contar como pendente
        t.postponeTo(DAY + 7);                         // adiada de novo: guarda o dia ORIGINAL
        assertEquals(DAY, t.postponedFrom);
    }

    @Test
    public void canceledIsClosedButNotDone() {
        Task t = meeting();
        t.status = Task.STATUS_CANCELED;
        assertTrue(t.isClosed());
        assertFalse(t.isDone());
        assertTrue(t.isCanceled());
        Map<String, Object> doc = CloudSchema.task(t, Collections.emptyList());
        assertEquals(Task.STATUS_CANCELED, CloudSchema.task(9, doc).status); // a nuvem aceita o status 3
    }

    @Test
    public void workTagKeepsItsGroupAndShade() {
        Tag tag = new Tag();
        tag.id = 4;
        tag.name = "Bot Cobrança";
        tag.tagGroup = Tag.GROUP_BOT;
        tag.colorIndex = 2;
        Tag back = CloudSchema.tag(4, CloudSchema.tag(tag));
        assertTrue(back.isWork());
        assertEquals(Tag.GROUP_BOT, back.tagGroup);
        assertEquals(2, back.colorIndex);

        Map<String, Object> personal = new HashMap<>();
        personal.put("nome", "Casa");
        personal.put("cor", 3L);
        assertFalse(CloudSchema.tag(5, personal).isWork()); // sem grupo: tag do Pessoal
    }

    @Test
    public void newTagInAGroupGetsTheLeastUsedShade() {
        Tag a = workTag(1, Tag.GROUP_PROJECT, 0);
        Tag b = workTag(2, Tag.GROUP_PROJECT, 1);
        Tag other = workTag(3, Tag.GROUP_BOT, 2);
        List<Tag> all = Arrays.asList(a, b, other);
        assertEquals(2, WorkTagColors.nextShade(Tag.GROUP_PROJECT, all, 0));
        assertEquals(0, WorkTagColors.nextShade(Tag.GROUP_BOT, all, 0));
        assertEquals(0, WorkTagColors.nextShade(Tag.GROUP_COMPANY, all, 0));
        assertEquals(Tag.GROUP_OTHER, WorkTagColors.safeGroup(99));
    }

    private static Tag workTag(long id, int group, int shade) {
        Tag t = new Tag();
        t.id = id;
        t.tagGroup = group;
        t.colorIndex = shade;
        return t;
    }
}
