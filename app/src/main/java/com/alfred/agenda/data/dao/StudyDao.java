package com.alfred.agenda.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.alfred.agenda.data.entity.StudyLog;
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

    @Update
    void updateTopic(StudyTopic topic);

    @Delete
    void deleteTopic(StudyTopic topic);

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

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM study_subjects WHERE topicId = :topicId")
    int nextSubjectPosition(long topicId);

    @Insert
    long insertSubject(StudySubject subject);

    @Update
    void updateSubject(StudySubject subject);

    @Delete
    void deleteSubject(StudySubject subject);

    @Query("UPDATE study_subjects SET runningSince = :since WHERE id = :id")
    void setRunningSince(long id, long since);

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
