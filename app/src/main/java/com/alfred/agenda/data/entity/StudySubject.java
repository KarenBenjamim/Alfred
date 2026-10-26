package com.alfred.agenda.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Um assunto dentro de um tema de estudo (ex.: "Verbos irregulares" dentro de "Inglês").
 * Cada assunto tem um cronômetro: enquanto ele está rodando, runningSince guarda o
 * instante em que começou; ao pausar, o tempo é somado em StudyLog (por dia) e volta a 0.
 */
@Entity(tableName = "study_subjects",
        foreignKeys = @ForeignKey(entity = StudyTopic.class, parentColumns = "id",
                childColumns = "topicId", onDelete = ForeignKey.CASCADE),
        indices = @Index("topicId"))
public class StudySubject {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long topicId;

    @NonNull
    public String name = "";

    public int position;

    /** Milissegundos (System.currentTimeMillis) em que o cronômetro foi iniciado. 0 = parado. */
    public long runningSince;

    public boolean isRunning() {
        return runningSince > 0;
    }
}
