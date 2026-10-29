package com.alfred.agenda.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.alfred.agenda.data.entity.StudyLog;
import com.alfred.agenda.data.entity.StudySubject;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Casos de borda de água, cronômetro de estudo e módulos (QA). */
public class BordasEstudosEAguaTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    private static long millis(LocalDate d, int h, int m) {
        return d.atTime(h, m).atZone(SP).toInstant().toEpochMilli();
    }

    private static StudySubject subject(long id, Long moduleId, long runningSince) {
        StudySubject s = new StudySubject();
        s.id = id;
        s.moduleId = moduleId;
        s.runningSince = runningSince;
        return s;
    }

    // ---------------- Água ----------------

    @Test
    public void pesoNaNOuInfinitoNaoGeraMeta() {
        assertFalse(WaterGoal.isValidWeight(Float.NaN));
        assertFalse(WaterGoal.isValidWeight(Float.POSITIVE_INFINITY));
        assertEquals(0, WaterGoal.goalMl(Float.NaN, 35));
        assertEquals(0f, WaterGoal.parseWeight(""), 0f);
        assertEquals(0, WaterGoal.goalMl(WaterGoal.parseWeight("Infinity"), 35));
        assertEquals(0, WaterGoal.goalMl(WaterGoal.parseWeight("NaN"), 35));
    }

    @Test
    public void metaNosLimitesDoPeso() {
        assertEquals(0, WaterGoal.goalMl(19.9f, 35));
        assertEquals(700, WaterGoal.goalMl(20f, 35));
        assertEquals(14000, WaterGoal.goalMl(400f, 35));
        assertEquals(0, WaterGoal.goalMl(400.1f, 35));
    }

    @Test
    public void fracaoNuncaPassaDeUmNemFicaNegativa() {
        assertEquals(1f, WaterGoal.fraction(Integer.MAX_VALUE, 1), 0f);
        assertEquals(0f, WaterGoal.fraction(-50, 2000), 0f);
        assertEquals(0f, WaterGoal.fraction(500, -1), 0f);
    }

    @Test
    public void somaDeAguaNaoEstouraInteiro() {
        assertEquals(WaterGoal.MAX_DAY_ML, WaterGoal.add(WaterGoal.MAX_DAY_ML, Integer.MAX_VALUE / 2));
        assertEquals(0, WaterGoal.add(0, Integer.MIN_VALUE / 2));
    }

    @Test
    public void transbordaAoSairDoZeroDireitoParaAlemDaMeta() {
        assertTrue(WaterGoal.overflows(0, 3000, 2450));
    }

    // ---------------- Cronômetro ----------------

    @Test
    public void intervaloInvertidoPeloRelogioNaoGeraTempo() {
        long start = millis(LocalDate.of(2026, 10, 28), 15, 0);
        assertTrue(StudyTime.splitByDay(start, start - 60_000, SP).isEmpty());
        assertTrue(StudyTime.splitByDay(start, start, SP).isEmpty());
    }

    @Test
    public void intervaloMenorQueUmSegundoNaoGeraTempo() {
        long start = millis(LocalDate.of(2026, 10, 28), 15, 0);
        assertTrue(StudyTime.splitByDay(start, start + 999, SP).isEmpty());
    }

    @Test
    public void cronometroEmVariosDiasSomaCadaDia() {
        LocalDate d = LocalDate.of(2026, 10, 27);
        Map<Long, Long> out = StudyTime.splitByDay(millis(d, 22, 0), millis(d.plusDays(2), 2, 0), SP);
        assertEquals(3, out.size());
        assertEquals(2 * 3600L, (long) out.get(d.toEpochDay()));
        assertEquals(24 * 3600L, (long) out.get(d.plusDays(1).toEpochDay()));
        assertEquals(2 * 3600L, (long) out.get(d.plusDays(2).toEpochDay()));
    }

    @Test
    public void diaDeHorarioDeVeraoComVinteETresHorasSomaCorretamente() {
        ZoneId londres = ZoneId.of("Europe/London");
        LocalDate dia = LocalDate.of(2026, 3, 29); // 1h pula para 2h
        long start = dia.atStartOfDay(londres).toInstant().toEpochMilli();
        long end = dia.plusDays(1).atStartOfDay(londres).toInstant().toEpochMilli();
        Map<Long, Long> out = StudyTime.splitByDay(start, end, londres);
        assertEquals(1, out.size());
        assertEquals(23 * 3600L, (long) out.get(dia.toEpochDay()));
    }

    @Test
    public void cronometroRodandoDesdeOntemContaSoHojeNaJanelaDeHoje() {
        LocalDate hoje = LocalDate.of(2026, 10, 28);
        long agora = millis(hoje, 1, 0);
        StudySubject s = subject(1, 10L, millis(hoje.minusDays(1), 23, 0));
        List<StudyLog> logs = Collections.emptyList();
        assertEquals(3600L, StudyStats.seconds(s, logs, hoje.toEpochDay(), hoje.toEpochDay(), agora, SP));
        assertEquals(3600L, StudyStats.seconds(s, logs, hoje.minusDays(1).toEpochDay(),
                hoje.minusDays(1).toEpochDay(), agora, SP));
    }

    @Test
    public void cronometroComRelogioNoFuturoNaoSomaTempoNegativo() {
        LocalDate hoje = LocalDate.of(2026, 10, 28);
        long agora = millis(hoje, 10, 0);
        StudySubject s = subject(1, 10L, agora + 3_600_000L); // começou "no futuro"
        assertEquals(0L, StudyStats.seconds(s, Collections.emptyList(), hoje.toEpochDay(), hoje.toEpochDay(),
                agora, SP));
    }

    // ---------------- Módulos ----------------

    @Test
    public void assuntoSemModuloNaoPertenceAModuloNenhum() {
        StudySubject s = subject(1, null, 0);
        assertFalse(s.inModule(0));
        assertFalse(s.inModule(1));
    }

    @Test
    public void moduloSemAssuntosTemZeroPorCentoSemDividirPorZero() {
        LocalDate hoje = LocalDate.of(2026, 10, 28);
        StudyStats.ModuleSummary r = StudyStats.module(99, Collections.emptyList(), Collections.emptyList(),
                hoje.toEpochDay(), millis(hoje, 12, 0), SP);
        assertEquals(0, r.subjects);
        assertEquals(0, r.percent());
        assertFalse(r.running);
    }

    @Test
    public void moverAssuntoDeModuloMudaOResumoDosDois() {
        LocalDate hoje = LocalDate.of(2026, 10, 28);
        long agora = millis(hoje, 12, 0);
        StudySubject s = subject(1, 10L, 0);
        s.completed = true;
        List<StudySubject> lista = Collections.singletonList(s);
        assertEquals(1, StudyStats.module(10, lista, Collections.emptyList(), hoje.toEpochDay(), agora, SP).completed);
        s.moduleId = 20L; // o que moveToModule faz no banco
        assertEquals(0, StudyStats.module(10, lista, Collections.emptyList(), hoje.toEpochDay(), agora, SP).subjects);
        assertEquals(100, StudyStats.module(20, lista, Collections.emptyList(), hoje.toEpochDay(), agora, SP).percent());
    }
}
