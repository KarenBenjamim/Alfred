package com.alfred.agenda.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Algo que a usuária quer aprender (ex.: "Inglês"). Vira uma aba no topo da tela de Estudos. */
@Entity(tableName = "study_topics")
public class StudyTopic {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name = "";

    /** Ordem das abas. */
    public int position;

    /** Cor da matéria (índice em StudyColors): pinta os assuntos ainda não concluídos. 0 = azul-marinho. */
    @ColumnInfo(defaultValue = "0")
    public int colorIndex;
}
