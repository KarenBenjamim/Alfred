package com.alfred.agenda.ui.study;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.alfred.agenda.data.AlfredRepository;
import com.alfred.agenda.data.entity.StudyLog;
import com.alfred.agenda.data.entity.StudyModule;
import com.alfred.agenda.data.entity.StudySubject;
import com.alfred.agenda.data.entity.StudyTopic;
import com.alfred.agenda.util.StudyColors;
import com.alfred.agenda.util.StudyStats;

import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class StudyViewModel extends AndroidViewModel {

    private final AlfredRepository repo;

    public final LiveData<List<StudyTopic>> topics;
    public final LiveData<List<StudyModule>> modules;
    public final LiveData<List<StudySubject>> subjects;
    /** Todo o tempo registrado (no máximo 2 anos, por causa do expurgo). */
    public final LiveData<List<StudyLog>> logs;

    /** Tema (aba) selecionado; sobrevive à rotação. 0 = nenhum ainda. */
    public long selectedTopicId = 0;

    /** Módulos recolhidos (mostram só o cabeçalho); sobrevive à rotação. */
    public final Set<Long> collapsedModules = new HashSet<>();

    public StudyViewModel(@NonNull Application app) {
        super(app);
        repo = AlfredRepository.get(app);
        topics = repo.observeStudyTopics();
        modules = repo.observeStudyModules();
        subjects = repo.observeStudySubjects();
        logs = repo.observeStudyLogsSince(0);
    }

    // ---------------- Temas ----------------

    public void addTopic(String name, int colorIndex, Consumer<StudyTopic> onCreated) {
        StudyTopic t = new StudyTopic();
        t.name = name;
        t.colorIndex = StudyColors.safeIndex(colorIndex);
        repo.saveStudyTopic(t, onCreated);
    }

    /** Muda nome e cor da matéria. */
    public void editTopic(StudyTopic topic, String name, int colorIndex) {
        // Não mexe no objeto da lista: a tela só muda quando o banco confirmar
        repo.editStudyTopic(topic.id, name, StudyColors.safeIndex(colorIndex));
    }

    public void deleteTopic(StudyTopic topic) {
        repo.deleteStudyTopic(topic);
    }

    // ---------------- Módulos ----------------

    public void addModule(long topicId, String name) {
        StudyModule m = new StudyModule();
        m.topicId = topicId;
        m.name = name;
        // Um módulo novo aparece aberto
        repo.saveStudyModule(m, created -> collapsedModules.remove(created.id));
    }

    public void renameModule(StudyModule module, String name) {
        repo.renameStudyModule(module.id, name);
    }

    public void deleteModule(StudyModule module) {
        repo.deleteStudyModule(module);
    }

    // ---------------- Assuntos ----------------

    public void addSubject(StudyModule module, String name) {
        StudySubject s = new StudySubject();
        s.topicId = module.topicId;
        s.moduleId = module.id;
        s.name = name;
        collapsedModules.remove(module.id);
        repo.saveStudySubject(s);
    }

    public void renameSubject(StudySubject subject, String name) {
        repo.renameStudySubject(subject.id, name);
    }

    public void deleteSubject(StudySubject subject) {
        repo.deleteStudySubject(subject);
    }

    /** @param onSuccess chamado só se a gravação deu certo (pode ser null) */
    public void setCompleted(StudySubject subject, boolean completed, @Nullable Runnable onSuccess) {
        repo.setStudySubjectCompleted(subject.id, completed, onSuccess);
    }

    /** Move o assunto para outro módulo do tema e abre esse módulo, para o assunto aparecer. */
    public void moveSubject(StudySubject subject, StudyModule target, @Nullable Runnable onSuccess) {
        if (subject.inModule(target.id) || target.topicId != subject.topicId) return;
        collapsedModules.remove(target.id);
        repo.moveStudySubject(subject.id, target.id, onSuccess);
    }

    /** Liga/desliga o cronômetro do assunto. */
    public void toggleTimer(StudySubject subject) {
        if (subject.isRunning()) repo.stopStudyTimer(subject.id);
        else repo.startStudyTimer(subject.id);
    }

    // ---------------- Contas de tempo ----------------

    /** Segundos de um assunto entre dois dias (inclusive), contando o cronômetro em andamento. */
    public static long seconds(StudySubject s, List<StudyLog> logs, long fromDay, long toDay) {
        return StudyStats.seconds(s, logs, fromDay, toDay, System.currentTimeMillis(), ZoneId.systemDefault());
    }
}
