package com.alfred.agenda.data.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Um assunto dentro de um módulo (ex.: "Verbos irregulares" em Inglês → Gramática).
 * Cada assunto tem um cronômetro: enquanto ele está rodando, runningSince guarda o
 * instante em que começou; ao pausar, o tempo é somado em StudyLog (por dia) e volta a 0.
 */
@Entity(tableName = "study_subjects",
        foreignKeys = {
                @ForeignKey(entity = StudyTopic.class, parentColumns = "id",
                        childColumns = "topicId", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = StudyModule.class, parentColumns = "id",
                        childColumns = "moduleId", onDelete = ForeignKey.CASCADE)},
        indices = {@Index("topicId"), @Index("moduleId")})
public class StudySubject {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** Tema do assunto (o mesmo do módulo; facilita os totais por tema). */
    public long topicId;

    /** Módulo do assunto. Só fica null em bancos antigos antes da migração 6 -> 7. */
    @Nullable
    public Long moduleId;

    @NonNull
    public String name = "";

    public int position;

    /** Milissegundos (System.currentTimeMillis) em que o cronômetro foi iniciado. 0 = parado. */
    public long runningSince;

    /** Marcado como concluído pelo usuário (conta no progresso do módulo). */
    @ColumnInfo(defaultValue = "0")
    public boolean completed;

    public boolean isRunning() {
        return runningSince > 0;
    }

    /** true se o assunto pertence ao módulo. */
    public boolean inModule(long id) {
        return moduleId != null && moduleId == id;
    }
}
