package com.alfred.agenda.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Módulo de um tema de estudo (ex.: "Gramática" dentro de "Inglês"). Agrupa assuntos;
 * o tempo e o progresso do módulo são a soma dos assuntos dele. Excluído junto com o tema.
 */
@Entity(tableName = "study_modules",
        foreignKeys = @ForeignKey(entity = StudyTopic.class, parentColumns = "id",
                childColumns = "topicId", onDelete = ForeignKey.CASCADE),
        indices = @Index("topicId"))
public class StudyModule {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long topicId;

    @NonNull
    public String name = "";

    /** Ordem dentro do tema. */
    public int position;
}
