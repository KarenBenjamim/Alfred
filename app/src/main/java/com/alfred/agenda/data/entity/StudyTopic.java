package com.alfred.agenda.data.entity;

import androidx.annotation.NonNull;
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
}
