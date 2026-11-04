package com.alfred.agenda.util;

import static org.junit.Assert.assertEquals;

import com.alfred.agenda.data.entity.Tag;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Ordem das tags de uma tarefa (o degradê do cartão) e cor segura para valores inválidos. */
public class TagPaletteTest {

    private static Tag tag(long id, String name) {
        Tag t = new Tag();
        t.id = id;
        t.name = name;
        return t;
    }

    private static List<Long> ids(List<Tag> tags) {
        List<Long> out = new ArrayList<>();
        for (Tag t : tags) out.add(t.id);
        return out;
    }

    @Test
    public void ordemDeterministicaPorNome_semDiferenciarMaiusculas() {
        List<Tag> a = TagPalette.ordered(Arrays.asList(tag(1, "Saúde"), tag(2, "casa"), tag(3, "Lazer")));
        List<Tag> b = TagPalette.ordered(Arrays.asList(tag(3, "Lazer"), tag(1, "Saúde"), tag(2, "casa")));
        assertEquals(Arrays.asList(2L, 3L, 1L), ids(a));
        assertEquals(ids(a), ids(b));
    }

    @Test
    public void mesmoNome_desempataPorId() {
        assertEquals(Arrays.asList(3L, 9L), ids(TagPalette.ordered(Arrays.asList(tag(9, "Igual"), tag(3, "Igual")))));
    }

    @Test
    public void corInvalida_usaCorPadraoSegura() {
        assertEquals(TagPalette.DEFAULT_INDEX, TagPalette.safeIndex(-1));
        assertEquals(TagPalette.DEFAULT_INDEX, TagPalette.safeIndex(99));
        assertEquals(7, TagPalette.safeIndex(7));
        assertEquals(0, TagPalette.safeIndex(0));
    }
}
