package com.alfred.agenda.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.alfred.agenda.data.entity.Tag;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/** Cores das tarefas pessoais a partir das tags reais (zero, uma, várias, sem cor). */
public class TagPaletteTest {

    private static Tag tag(long id, String name, int color) {
        Tag t = new Tag();
        t.id = id;
        t.name = name;
        t.colorIndex = color;
        return t;
    }

    @Test
    public void semTags_nenhumaCor() {
        assertEquals(0, TagPalette.colorIndices(Collections.emptyList()).length);
    }

    @Test
    public void umaTag_usaSuaCor() {
        assertArrayEquals(new int[]{4}, TagPalette.colorIndices(Collections.singletonList(tag(1, "Casa", 4))));
    }

    @Test
    public void variasTags_todasAsCores_emOrdemDeterministicaPorNome() {
        int[] a = TagPalette.colorIndices(Arrays.asList(tag(1, "Saúde", 1), tag(2, "casa", 4), tag(3, "Lazer", 6)));
        int[] b = TagPalette.colorIndices(Arrays.asList(tag(3, "Lazer", 6), tag(1, "Saúde", 1), tag(2, "casa", 4)));
        // casa (4), Lazer (6), Saúde (1) — independente da ordem de entrada e de maiúsculas
        assertArrayEquals(new int[]{4, 6, 1}, a);
        assertArrayEquals(a, b);
    }

    @Test
    public void mesmoNome_desempataPorId() {
        int[] out = TagPalette.colorIndices(Arrays.asList(tag(9, "Igual", 2), tag(3, "Igual", 5)));
        assertArrayEquals(new int[]{5, 2}, out);
    }

    @Test
    public void corInvalidaOuAusente_usaCorPadraoSegura() {
        int[] out = TagPalette.colorIndices(Arrays.asList(tag(1, "A", -1), tag(2, "B", 99), tag(3, "C", 7)));
        assertArrayEquals(new int[]{TagPalette.DEFAULT_INDEX, TagPalette.DEFAULT_INDEX, 7}, out);
    }

    @Test
    public void mudarCorDaTag_mudaResultado() {
        Tag t = tag(1, "Casa", 0);
        assertArrayEquals(new int[]{0}, TagPalette.colorIndices(Collections.singletonList(t)));
        t.colorIndex = 3;
        assertArrayEquals(new int[]{3}, TagPalette.colorIndices(Collections.singletonList(t)));
    }
}
