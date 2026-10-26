package com.alfred.agenda.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Etiqueta para tarefas pessoais (ex.: "Saúde", "Casa"), com uma cor da paleta (TagColors). */
@Entity(tableName = "tags")
public class Tag {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name = "";

    /** Índice da cor em TagColors.COLORS (0 a 7). */
    public int colorIndex;
}
