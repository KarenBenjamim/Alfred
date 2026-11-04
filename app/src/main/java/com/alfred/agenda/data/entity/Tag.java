package com.alfred.agenda.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Etiqueta de tarefa.
 *  - Pessoal (tagGroup = -1): uma das 8 cores da paleta (TagColors), ex.: "Saúde", "Casa".
 *  - Trabalho (tagGroup 0 a 4): o grupo define a cor (Projeto azul, Bot verde, Empresa laranja,
 *    Evento/Estudo roxo, Outros rosa) e colorIndex é o tom dentro do grupo (WorkTagColors).
 */
@Entity(tableName = "tags")
public class Tag {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name = "";

    /** Pessoal: índice em TagColors.COLORS (0 a 7). Trabalho: tom dentro do grupo (0 a 3). */
    public int colorIndex;

    public static final int NO_GROUP = -1;
    public static final int GROUP_PROJECT = 0;
    public static final int GROUP_BOT = 1;
    public static final int GROUP_COMPANY = 2;
    public static final int GROUP_EVENT = 3;
    public static final int GROUP_OTHER = 4;
    public static final int GROUP_COUNT = 5;

    /** Grupo da tag de Trabalho (GROUP_*); NO_GROUP = tag do Pessoal. */
    @ColumnInfo(defaultValue = "-1")
    public int tagGroup = NO_GROUP;

    public boolean isWork() {
        return tagGroup >= 0;
    }
}
