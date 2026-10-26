package com.alfred.agenda.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Meta de água (peso × ml por kg) e o enchimento do copo. */
public class WaterGoalTest {

    @Test
    public void goalIs35mlPerKgByDefault() {
        assertEquals(2450, WaterGoal.goalMl(70f, WaterGoal.DEFAULT_ML_PER_KG));
        assertEquals(2450, WaterGoal.goalMl(70f, 0)); // planilha sem valor -> padrão 35
        assertEquals(2468, WaterGoal.goalMl(70.5f, 35));
        assertEquals(2800, WaterGoal.goalMl(70f, 40)); // valor indicado na planilha
    }

    @Test
    public void invalidWeightHasNoGoal() {
        assertEquals(0, WaterGoal.goalMl(0f, 35));
        assertEquals(0, WaterGoal.goalMl(5f, 35));
        assertEquals(0, WaterGoal.goalMl(1000f, 35));
        assertFalse(WaterGoal.isValidWeight(19.9f));
        assertTrue(WaterGoal.isValidWeight(20f));
    }

    @Test
    public void glassIsFullAtGoalAndNeverOverflows() {
        assertEquals(0f, WaterGoal.fraction(0, 2450), 0f);
        assertEquals(0.5f, WaterGoal.fraction(1225, 2450), 0.0001f);
        assertEquals(1f, WaterGoal.fraction(2450, 2450), 0f);
        assertEquals(1f, WaterGoal.fraction(4000, 2450), 0f);
        assertEquals(0f, WaterGoal.fraction(500, 0), 0f); // sem meta, copo vazio
    }

    @Test
    public void addClampsBetweenZeroAndDailyLimit() {
        assertEquals(700, WaterGoal.add(500, 200));
        assertEquals(0, WaterGoal.add(100, -500));
        assertEquals(WaterGoal.MAX_DAY_ML, WaterGoal.add(WaterGoal.MAX_DAY_ML - 100, 500));
    }

    @Test
    public void parseWeightAcceptsCommaOrDot() {
        assertEquals(70.5f, WaterGoal.parseWeight("70,5"), 0f);
        assertEquals(70.5f, WaterGoal.parseWeight(" 70.5 "), 0f);
        assertEquals(0f, WaterGoal.parseWeight("abc"), 0f);
    }
}
