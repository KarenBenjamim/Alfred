package com.alfred.agenda.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.alfred.agenda.data.entity.StudyLog;
import com.alfred.agenda.data.entity.StudySubject;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

/** O módulo soma o tempo dos seus assuntos e mede o progresso pelos assuntos concluídos. */
public class StudyStatsTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final long TODAY = LocalDate.of(2026, 10, 28).toEpochDay();
    /** 28/10/2026 às 15:00 em São Paulo. */
    private static final long NOW = LocalDate.of(2026, 10, 28).atTime(15, 0).atZone(ZONE).toInstant().toEpochMilli();

    private static StudySubject subject(long id, Long moduleId, boolean completed, long runningSince) {
        StudySubject s = new StudySubject();
        s.id = id;
        s.moduleId = moduleId;
        s.completed = completed;
        s.runningSince = runningSince;
        return s;
    }

    private static StudyLog log(long subjectId, long day, long seconds) {
        StudyLog l = new StudyLog();
        l.subjectId = subjectId;
        l.epochDay = day;
        l.seconds = seconds;
        return l;
    }

    @Test
    public void moduleSumsOnlyItsOwnSubjects() {
        List<StudySubject> subjects = Arrays.asList(
                subject(1, 10L, true, 0), subject(2, 10L, false, 0), subject(3, 20L, true, 0));
        List<StudyLog> logs = Arrays.asList(
                log(1, TODAY, 600), log(1, TODAY - 3, 1200), log(2, TODAY, 300), log(3, TODAY, 9999));

        StudyStats.ModuleSummary m = StudyStats.module(10, subjects, logs, TODAY, NOW, ZONE);
        assertEquals(2, m.subjects);
        assertEquals(1, m.completed);
        assertEquals(50, m.percent());
        assertEquals(900, m.secondsToday);     // 600 + 300
        assertEquals(2100, m.secondsTotal);    // + 1200 de três dias atrás
        assertFalse(m.running);
    }

    @Test
    public void runningTimerCountsInTheModule() {
        long since = NOW - 30 * 60 * 1000L; // rodando há 30 min
        List<StudySubject> subjects = Arrays.asList(subject(1, 10L, false, since));
        StudyStats.ModuleSummary m = StudyStats.module(10, subjects, Arrays.asList(log(1, TODAY, 60)),
                TODAY, NOW, ZONE);
        assertTrue(m.running);
        assertEquals(60 + 1800, m.secondsToday);
        assertEquals(60 + 1800, m.secondsTotal);
    }

    @Test
    public void timerStartedYesterdayOnlyCountsTodaysPartToday() {
        // Começou ontem às 23:00 e ainda roda às 15:00 de hoje: hoje = 15h
        long since = LocalDate.ofEpochDay(TODAY - 1).atTime(23, 0).atZone(ZONE).toInstant().toEpochMilli();
        StudySubject s = subject(1, 10L, false, since);
        assertEquals(15 * 3600, StudyStats.seconds(s, Arrays.asList(), TODAY, TODAY, NOW, ZONE));
        assertEquals(16 * 3600, StudyStats.seconds(s, Arrays.asList(), 0, TODAY, NOW, ZONE));
    }

    @Test
    public void emptyModuleHasZeroProgress() {
        StudyStats.ModuleSummary m = StudyStats.module(99, Arrays.asList(subject(1, 10L, true, 0)),
                Arrays.asList(), TODAY, NOW, ZONE);
        assertEquals(0, m.subjects);
        assertEquals(0, m.percent());
        assertEquals(0, m.secondsTotal);
    }

    @Test
    public void allCompletedIsHundredPercent() {
        List<StudySubject> subjects = Arrays.asList(subject(1, 10L, true, 0), subject(2, 10L, true, 0),
                subject(3, null, false, 0)); // sem módulo (banco antigo): ignorado
        assertEquals(100, StudyStats.module(10, subjects, Arrays.asList(), TODAY, NOW, ZONE).percent());
    }
}
