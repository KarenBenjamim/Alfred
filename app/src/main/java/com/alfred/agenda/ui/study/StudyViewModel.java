package com.alfred.agenda.ui.study;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.alfred.agenda.data.AlfredRepository;
import com.alfred.agenda.data.entity.StudyLog;
import com.alfred.agenda.data.entity.StudySubject;
import com.alfred.agenda.data.entity.StudyTopic;
import com.alfred.agenda.util.DateUtils;

import java.util.List;
import java.util.function.Consumer;

public class StudyViewModel extends AndroidViewModel {

    private final AlfredRepository repo;

    public final LiveData<List<StudyTopic>> topics;
    public final LiveData<List<StudySubject>> subjects;
    /** Todo o tempo registrado (no máximo 2 anos, por causa do expurgo). */
    public final LiveData<List<StudyLog>> logs;

    /** Tema (aba) selecionado; sobrevive à rotação. 0 = nenhum ainda. */
    public long selectedTopicId = 0;

    public StudyViewModel(@NonNull Application app) {
        super(app);
        repo = AlfredRepository.get(app);
        topics = repo.observeStudyTopics();
        subjects = repo.observeStudySubjects();
        logs = repo.observeStudyLogsSince(0);
    }

    public void addTopic(String name, Consumer<StudyTopic> onCreated) {
        StudyTopic t = new StudyTopic();
        t.name = name;
        repo.saveStudyTopic(t, onCreated);
    }

    public void renameTopic(StudyTopic topic, String name) {
        topic.name = name;
        repo.saveStudyTopic(topic, null);
    }

    public void deleteTopic(StudyTopic topic) {
        repo.deleteStudyTopic(topic);
    }

    public void addSubject(long topicId, String name) {
        StudySubject s = new StudySubject();
        s.topicId = topicId;
        s.name = name;
        repo.saveStudySubject(s);
    }

    public void renameSubject(StudySubject subject, String name) {
        subject.name = name;
        repo.saveStudySubject(subject);
    }

    public void deleteSubject(StudySubject subject) {
        repo.deleteStudySubject(subject);
    }

    /** Liga/desliga o cronômetro do assunto. */
    public void toggleTimer(StudySubject subject) {
        if (subject.isRunning()) repo.stopStudyTimer(subject.id);
        else repo.startStudyTimer(subject.id);
    }

    // ---------------- Contas de tempo ----------------

    /** Segundos de um assunto entre dois dias (inclusive), contando o cronômetro em andamento. */
    public static long seconds(StudySubject s, List<StudyLog> logs, long fromDay, long toDay) {
        long total = 0;
        for (StudyLog log : logs) {
            if (log.subjectId == s.id && log.epochDay >= fromDay && log.epochDay <= toDay) total += log.seconds;
        }
        if (s.isRunning()) {
            long start = Math.max(s.runningSince, DateUtils.startOfDayMillis(fromDay));
            long end = Math.min(System.currentTimeMillis(), DateUtils.startOfDayMillis(toDay + 1));
            if (end > start) total += (end - start) / 1000;
        }
        return total;
    }
}
