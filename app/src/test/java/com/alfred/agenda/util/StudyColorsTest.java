package com.alfred.agenda.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** A cor da matéria sempre cai numa cor válida da paleta. */
public class StudyColorsTest {

    @Test
    public void validIndexIsKept() {
        for (int i = 0; i < StudyColors.COLORS.length; i++) assertEquals(i, StudyColors.safeIndex(i));
    }

    @Test
    public void invalidIndexFallsBackToNavy() {
        assertEquals(StudyColors.DEFAULT_INDEX, StudyColors.safeIndex(-1));
        assertEquals(StudyColors.DEFAULT_INDEX, StudyColors.safeIndex(StudyColors.COLORS.length));
        assertEquals(0, StudyColors.DEFAULT_INDEX); // matérias antigas (migração 7 -> 8) ficam azul-marinho
    }
}
