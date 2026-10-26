package com.alfred.agenda.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/**
 * Conversões de data/hora usadas no app inteiro.
 *
 * Convenções:
 *  - datas são guardadas como "epochDay" (LocalDate.toEpochDay()), sem fuso horário;
 *  - horários são guardados como minutos desde a meia-noite (0..1439).
 */
public final class DateUtils {

    public static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    public static final long DAY_MILLIS = 24L * 60 * 60 * 1000;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", PT_BR);
    private static final DateTimeFormatter DATE_SHORT = DateTimeFormatter.ofPattern("dd/MM", PT_BR);
    private static final DateTimeFormatter LONG_DAY = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PT_BR);
    private static final DateTimeFormatter MONTH_SHORT = DateTimeFormatter.ofPattern("MMM", PT_BR);

    private DateUtils() {
    }

    public static long today() {
        return LocalDate.now().toEpochDay();
    }

    /** Segunda-feira da semana atual, em epochDay. */
    public static long currentWeekStart() {
        return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay();
    }

    /** 1 = segunda ... 7 = domingo. */
    public static int todayDayOfWeek() {
        return LocalDate.now().getDayOfWeek().getValue();
    }

    public static String formatTime(int minutes) {
        return String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60);
    }

    public static String formatDate(long epochDay) {
        return LocalDate.ofEpochDay(epochDay).format(DATE);
    }

    public static String formatDateShort(long epochDay) {
        return LocalDate.ofEpochDay(epochDay).format(DATE_SHORT);
    }

    /** "26/10" neste ano; "26/10/2023" se for de outro ano (ex.: atrasada há muito tempo). */
    public static String formatDateShortOrYear(long epochDay) {
        LocalDate date = LocalDate.ofEpochDay(epochDay);
        return date.getYear() == LocalDate.now().getYear() ? date.format(DATE_SHORT) : date.format(DATE);
    }

    /** "Sexta-feira, 24 de outubro". */
    public static String formatLongDay(LocalDate date) {
        return capitalize(date.format(LONG_DAY));
    }

    /** "Segunda-feira" a partir de 1..7. */
    public static String dayOfWeekName(int dayOfWeek) {
        return capitalize(DayOfWeek.of(dayOfWeek).getDisplayName(TextStyle.FULL, PT_BR));
    }

    /** Nomes de segunda (índice 0) a domingo (índice 6). */
    public static String[] dayOfWeekNames() {
        String[] names = new String[7];
        for (int i = 0; i < 7; i++) names[i] = dayOfWeekName(i + 1);
        return names;
    }

    /** Cronômetro: 3725 s -> "01:02:05". */
    public static String formatClock(long seconds) {
        return String.format(Locale.US, "%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    /** Duração resumida: 3725 s -> "1h 02min"; 300 s -> "5min"; 20 s -> "<1min"; 0 -> "0min". */
    public static String formatDuration(long seconds) {
        long h = seconds / 3600, m = (seconds % 3600) / 60;
        if (h > 0) return String.format(Locale.US, "%dh %02dmin", h, m);
        if (m > 0) return m + "min";
        return seconds > 0 ? "<1min" : "0min";
    }

    /** Meia-noite (início) do dia, em milissegundos no fuso local. */
    public static long startOfDayMillis(long epochDay) {
        return LocalDate.ofEpochDay(epochDay).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** Converte dia + horário local em milissegundos (para o AlarmManager). */
    public static long millisAt(LocalDate date, int minutes) {
        return date.atTime(minutes / 60, minutes % 60)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** O MaterialDatePicker trabalha com meia-noite UTC em milissegundos. */
    public static long utcMillisFromEpochDay(long epochDay) {
        return epochDay * DAY_MILLIS;
    }

    public static long epochDayFromUtcMillis(long millis) {
        return Math.floorDiv(millis, DAY_MILLIS);
    }

    public static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
