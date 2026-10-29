package com.alfred.agenda.util;

import com.alfred.agenda.data.entity.StudyLog;
import com.alfred.agenda.data.entity.StudySubject;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Contas de tempo dos estudos (sem Android, testadas em StudyStatsTest).
 * O tempo é sempre cronometrado no assunto; o módulo soma os assuntos dele.
 */
public final class StudyStats {

    private StudyStats() {
    }

    /** Resumo de um módulo: progresso pelos assuntos concluídos e a soma do tempo. */
    public static final class ModuleSummary {
        public final int subjects;
        public final int completed;
        public final long secondsToday;
        public final long secondsTotal;
        public final boolean running;

        ModuleSummary(int subjects, int completed, long secondsToday, long secondsTotal, boolean running) {
            this.subjects = subjects;
            this.completed = completed;
            this.secondsToday = secondsToday;
            this.secondsTotal = secondsTotal;
            this.running = running;
        }

        /** 0 a 100 (0 se o módulo ainda não tem assuntos). */
        public int percent() {
            return subjects == 0 ? 0 : Math.round(100f * completed / subjects);
        }
    }

    /**
     * Segundos de um assunto entre dois dias (inclusive), contando o cronômetro em andamento
     * (só a parte que cai dentro do intervalo).
     */
    public static long seconds(StudySubject s, List<StudyLog> logs, long fromDay, long toDay,
                               long nowMillis, ZoneId zone) {
        long total = 0;
        for (StudyLog log : logs) {
            if (log.subjectId == s.id && log.epochDay >= fromDay && log.epochDay <= toDay) total += log.seconds;
        }
        if (s.isRunning()) {
            long start = Math.max(s.runningSince, startOfDay(fromDay, zone));
            long end = Math.min(nowMillis, startOfDay(toDay + 1, zone));
            if (end > start) total += (end - start) / 1000;
        }
        return total;
    }

    /** Soma os assuntos do módulo (os de outros módulos são ignorados). */
    public static ModuleSummary module(long moduleId, List<StudySubject> subjects, List<StudyLog> logs,
                                       long today, long nowMillis, ZoneId zone) {
        int count = 0, completed = 0;
        long secsToday = 0, secsTotal = 0;
        boolean running = false;
        for (StudySubject s : subjects) {
            if (!s.inModule(moduleId)) continue;
            count++;
            if (s.completed) completed++;
            if (s.isRunning()) running = true;
            secsToday += seconds(s, logs, today, today, nowMillis, zone);
            secsTotal += seconds(s, logs, 0, today, nowMillis, zone); // dia 0 = desde sempre
        }
        return new ModuleSummary(count, completed, secsToday, secsTotal, running);
    }

    private static long startOfDay(long epochDay, ZoneId zone) {
        return LocalDate.ofEpochDay(epochDay).atStartOfDay(zone).toInstant().toEpochMilli();
    }
}
