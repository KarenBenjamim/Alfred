package com.alfred.agenda.data.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;

/** Total de segundos estudados num assunto em um dia. Uma linha por (assunto, dia). */
@Entity(tableName = "study_logs",
        primaryKeys = {"subjectId", "epochDay"},
        foreignKeys = @ForeignKey(entity = StudySubject.class, parentColumns = "id",
                childColumns = "subjectId", onDelete = ForeignKey.CASCADE))
public class StudyLog {

    public long subjectId;

    /** Dia no formato LocalDate.toEpochDay(). */
    public long epochDay;

    public long seconds;
}
