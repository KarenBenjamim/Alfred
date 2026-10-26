package com.alfred.agenda.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/** Contas do cronômetro de estudo (sem Android, para poder testar). */
public final class StudyTime {

    private StudyTime() {
    }

    /**
     * Divide o intervalo [startMillis, endMillis) entre os dias do calendário local.
     * Ex.: das 23h às 1h = 1h no primeiro dia + 1h no segundo.
     *
     * @return dia (epochDay) -> segundos, em ordem cronológica (dias com 0s não aparecem)
     */
    public static Map<Long, Long> splitByDay(long startMillis, long endMillis, ZoneId zone) {
        Map<Long, Long> out = new LinkedHashMap<>();
        long cursor = startMillis;
        while (cursor < endMillis) {
            LocalDate day = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate();
            long dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
            long segmentEnd = Math.min(dayEnd, endMillis);
            long seconds = (segmentEnd - cursor) / 1000;
            if (seconds > 0) out.merge(day.toEpochDay(), seconds, Long::sum);
            cursor = segmentEnd;
        }
        return out;
    }
}
