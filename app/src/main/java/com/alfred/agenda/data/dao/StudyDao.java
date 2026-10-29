package com.alfred.agenda.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.alfred.agenda.data.entity.StudyLog;
import com.alfred.agenda.data.entity.StudyModule;
import com.alfred.agenda.data.entity.StudySubject;
import com.alfred.agenda.data.entity.StudyTopic;

import java.util.List;

@Dao
public interface StudyDao {

    // ---- Temas (abas) ----

    @Query("SELECT * FROM study_topics ORDER BY position, id")
    LiveData<List<StudyTopic>> observeTopics();

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM study_topics")
    int nextTopicPosition();

    @Insert
    long insertTopic(StudyTopic topic);

    @Delete
    void deleteTopic(StudyTopic topic);

    // ---- Módulos (dentro de um tema) ----

    @Query("SELECT * FROM study_modules ORDER BY position, id")
    LiveData<List<StudyModule>> observeModules();

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM study_modules WHERE topicId = :topicId")
    int nextModulePosition(long topicId);

    @Insert
    long insertModule(StudyModule module);

    @Delete
    void deleteModule(StudyModule module);

    // ---- Assuntos ----

    @Query("SELECT * FROM study_subjects ORDER BY position, id")
    LiveData<List<StudySubject>> observeSubjects();

    @Query("SELECT * FROM study_subjects WHERE id = :id")
    StudySubject getSubject(long id);

    @Query("SELECT * FROM study_subjects WHERE runningSince > 0")
    List<StudySubject> getRunningSync();

    @Query("SELECT * FROM study_subjects")
    List<StudySubject> getSubjectsSync();

    @Query("SELECT * FROM study_logs WHERE epochDay = :epochDay")
    List<StudyLog> getLogsForDaySync(long epochDay);

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM study_subjects WHERE moduleId = :moduleId")
    int nextSubjectPosition(long moduleId);

    @Insert
    long insertSubject(StudySubject subject);

    @Delete
    void deleteSubject(StudySubject subject);

    @Query("UPDATE study_subjects SET runningSince = :since WHERE id = :id")
    void setRunningSince(long id, long since);

    @Query("UPDATE study_subjects SET completed = :completed WHERE id = :id")
    void setCompleted(long id, boolean completed);

    // Renomear muda SÓ o nome (e a cor da matéria): módulo, posição, cronômetro e "concluído"
    // ficam como estão no banco, mesmo que a tela tenha uma cópia desatualizada

    @Query("UPDATE study_topics SET name = :name, colorIndex = :colorIndex WHERE id = :id")
    void editTopic(long id, String name, int colorIndex);

    @Query("UPDATE study_modules SET name = :name WHERE id = :id")
    void renameModule(long id, String name);

    @Query("UPDATE study_subjects SET name = :name WHERE id = :id")
    void renameSubject(long id, String name);

    /**
     * Move o assunto para o fim de outro módulo do MESMO tema (o tempo registrado vai junto).
     * Se o módulo não for do tema do assunto, nada muda. Retorna as linhas alteradas.
     */
    @Query("UPDATE study_subjects SET moduleId = :moduleId, "
            + "position = (SELECT COALESCE(MAX(position), -1) + 1 FROM study_subjects WHERE moduleId = :moduleId) "
            + "WHERE id = :id AND topicId = (SELECT topicId FROM study_modules WHERE id = :moduleId)")
    int moveToModule(long id, long moduleId);

    // ---- Tempo por dia ----

    /** Registros a partir de um dia (para os totais de hoje e da semana). */
    @Query("SELECT * FROM study_logs WHERE epochDay >= :fromDay")
    LiveData<List<StudyLog>> observeLogsSince(long fromDay);

    /** Histórico completo de um assunto, do dia mais recente para o mais antigo. */
    @Query("SELECT * FROM study_logs WHERE subjectId = :subjectId ORDER BY epochDay DESC")
    LiveData<List<StudyLog>> observeLogsForSubject(long subjectId);

    @Query("SELECT * FROM study_logs WHERE subjectId = :subjectId AND epochDay = :epochDay")
    StudyLog getLog(long subjectId, long epochDay);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void putLog(StudyLog log);

    /** Expurgo: apaga o tempo registrado antes de :limitDay. */
    @Query("DELETE FROM study_logs WHERE epochDay < :limitDay")
    int deleteLogsOlderThan(long limitDay);
}
