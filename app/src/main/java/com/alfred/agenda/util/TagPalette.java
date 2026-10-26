package com.alfred.agenda.util;

import com.alfred.agenda.data.entity.Tag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Regras (sem Android) para transformar as tags reais de uma tarefa em cores da paleta.
 * A parte visual (pintar o cartão) fica em TaskAppearance; aqui só decidimos QUAIS cores
 * e em QUE ordem, para poder testar com JUnit.
 */
public final class TagPalette {

    /** Quantidade de cores na paleta (R.color.tag_*). */
    public static final int SIZE = 8;

    /** Cor usada quando a tag não tem uma cor válida: o cinza da paleta. */
    public static final int DEFAULT_INDEX = 7;

    private TagPalette() {
    }

    /** Índice válido da paleta; qualquer valor fora de 0..7 vira a cor padrão segura. */
    public static int safeIndex(int colorIndex) {
        return colorIndex >= 0 && colorIndex < SIZE ? colorIndex : DEFAULT_INDEX;
    }

    /**
     * Ordem determinística das tags: por nome (sem diferenciar maiúsculas) e, em empate, por id.
     * Assim o degradê de uma tarefa sai sempre igual.
     */
    public static List<Tag> ordered(List<Tag> tags) {
        List<Tag> list = new ArrayList<>(tags);
        list.sort(Comparator.comparing((Tag t) -> t.name, String.CASE_INSENSITIVE_ORDER)
                .thenComparingLong(t -> t.id));
        return list;
    }

    /** Índices de cor (já validados) de todas as tags, na ordem determinística. */
    public static int[] colorIndices(List<Tag> tags) {
        List<Tag> list = ordered(tags);
        int[] out = new int[list.size()];
        for (int i = 0; i < list.size(); i++) out[i] = safeIndex(list.get(i).colorIndex);
        return out;
    }
}
