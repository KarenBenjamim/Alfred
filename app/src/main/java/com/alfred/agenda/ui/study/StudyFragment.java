package com.alfred.agenda.ui.study;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.alfred.agenda.R;
import com.alfred.agenda.data.entity.StudyLog;
import com.alfred.agenda.data.entity.StudyModule;
import com.alfred.agenda.data.entity.StudySubject;
import com.alfred.agenda.data.entity.StudyTopic;
import com.alfred.agenda.util.DateUtils;
import com.alfred.agenda.util.EmptyState;
import com.alfred.agenda.util.Honorific;
import com.alfred.agenda.util.UiUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Aba "Estudos": Tema -> Módulo -> Assunto.
 *  - No topo, uma aba por tema ("Inglês", "Python"...). O + ao lado cria um tema;
 *    toque e segure numa aba para renomear ou excluir.
 *  - Embaixo, os módulos do tema. Cada módulo mostra o progresso (assuntos concluídos) e a
 *    soma do tempo dos seus assuntos; toque no cabeçalho para recolher/abrir.
 *  - Dentro de cada módulo, os assuntos, cada um com cronômetro (iniciar/pausar). O tempo é
 *    guardado por dia no assunto; toque no assunto para ver o histórico.
 */
public class StudyFragment extends Fragment {

    private StudyViewModel viewModel;
    private TabLayout tabs;
    private TextView summary;
    private View emptyState;
    private View summaryCard;
    private TextView todayTime;
    private TextView studiedCount;
    private LinearProgressIndicator studiedBar;
    private TextView focusTitle;
    private View focusCard;
    private TextView focusName;
    private TextView focusDetail;
    private MaterialButton focusButton;
    private View modulesTitle;
    private StudyAdapter adapter;

    private List<StudyTopic> topics = new ArrayList<>();
    private List<StudyModule> allModules = new ArrayList<>();
    private List<StudySubject> allSubjects = new ArrayList<>();
    private List<StudyLog> logs = new ArrayList<>();
    /** Evita tratar como "toque" as seleções feitas pelo próprio código ao recriar as abas. */
    private boolean rebuildingTabs = false;

    /** Atualiza os cronômetros na tela a cada segundo. */
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (adapter != null && adapter.anyRunning()) {
                adapter.tick();
                renderSummary();
            }
            ticker.postDelayed(this, 1000);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_study, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(StudyViewModel.class);
        tabs = view.findViewById(R.id.tabs);
        summary = view.findViewById(R.id.topic_summary);
        emptyState = view.findViewById(R.id.empty_state);
        summaryCard = view.findViewById(R.id.summary_card);
        todayTime = view.findViewById(R.id.today_time);
        studiedCount = view.findViewById(R.id.studied_count);
        studiedBar = view.findViewById(R.id.studied_bar);
        focusTitle = view.findViewById(R.id.focus_title);
        focusCard = view.findViewById(R.id.focus_card);
        focusName = view.findViewById(R.id.focus_name);
        focusDetail = view.findViewById(R.id.focus_detail);
        focusButton = view.findViewById(R.id.focus_button);
        modulesTitle = view.findViewById(R.id.subjects_title);
        RecyclerView list = view.findViewById(R.id.list);

        adapter = new StudyAdapter(new StudyAdapter.Listener() {
            @Override
            public void onToggleModule(StudyModule module) {
                if (!viewModel.collapsedModules.remove(module.id)) viewModel.collapsedModules.add(module.id);
                renderSubjects();
            }

            @Override
            public void onModuleMenu(StudyModule module) {
                moduleMenu(module);
            }

            @Override
            public void onAddSubject(StudyModule module) {
                newSubject(module);
            }

            @Override
            public void onToggleTimer(StudySubject subject) {
                viewModel.toggleTimer(subject);
            }

            @Override
            public void onSubjectMenu(StudySubject subject) {
                subjectMenu(subject);
            }

            @Override
            public void onOpenHistory(StudySubject subject) {
                StudyHistorySheet.show(StudyFragment.this, subject);
            }
        });
        list.setAdapter(adapter);

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (rebuildingTabs) return;
                viewModel.selectedTopicId = (long) tab.getTag();
                renderSubjects();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        view.findViewById(R.id.button_add_topic).setOnClickListener(v -> newTopic());
        view.findViewById(R.id.fab).setOnClickListener(v -> {
            if (topics.isEmpty()) newTopic();
            else newModule();
        });

        viewModel.topics.observe(getViewLifecycleOwner(), items -> {
            topics = items;
            rebuildTabs();
        });
        viewModel.modules.observe(getViewLifecycleOwner(), items -> {
            allModules = items;
            renderSubjects();
        });
        viewModel.subjects.observe(getViewLifecycleOwner(), items -> {
            allSubjects = items;
            renderSubjects();
        });
        viewModel.logs.observe(getViewLifecycleOwner(), items -> {
            logs = items;
            renderSubjects();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        ticker.post(tick);
    }

    @Override
    public void onPause() {
        super.onPause();
        ticker.removeCallbacks(tick);
    }

    // ---------------- Temas (abas) ----------------

    private void newTopic() {
        UiUtils.promptText(requireContext(), R.string.study_topic_new, R.string.study_topic_name, "",
                name -> viewModel.addTopic(name, created -> {
                    viewModel.selectedTopicId = created.id; // já abre o tema novo
                    rebuildTabs();
                    renderSubjects();
                }), null);
    }

    /** Recria as abas a partir da lista de temas, mantendo a aba selecionada. */
    private void rebuildTabs() {
        rebuildingTabs = true;
        tabs.removeAllTabs();
        boolean found = false;
        for (StudyTopic t : topics) if (t.id == viewModel.selectedTopicId) found = true;
        if (!found) viewModel.selectedTopicId = topics.isEmpty() ? 0 : topics.get(0).id;

        for (StudyTopic t : topics) {
            TabLayout.Tab tab = tabs.newTab().setText(t.name).setTag(t.id);
            tabs.addTab(tab, t.id == viewModel.selectedTopicId);
            // Toque e segure: renomear ou excluir o tema
            tab.view.setOnLongClickListener(v -> {
                topicMenu(t);
                return true;
            });
        }
        rebuildingTabs = false;
        renderSubjects();
    }

    private void topicMenu(StudyTopic topic) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(topic.name)
                .setItems(new CharSequence[]{getString(R.string.study_rename), getString(R.string.delete)}, (d, which) -> {
                    if (which == 0) {
                        UiUtils.promptText(requireContext(), R.string.study_topic_edit, R.string.study_topic_name,
                                topic.name, name -> viewModel.renameTopic(topic, name), null);
                    } else {
                        UiUtils.confirmDelete(requireContext(), topic.name, R.string.study_delete_topic_msg,
                                () -> viewModel.deleteTopic(topic));
                    }
                })
                .show();
    }

    // ---------------- Módulos e assuntos ----------------

    private List<StudyModule> modulesOfSelectedTopic() {
        List<StudyModule> list = new ArrayList<>();
        for (StudyModule m : allModules) if (m.topicId == viewModel.selectedTopicId) list.add(m);
        return list;
    }

    private List<StudySubject> subjectsOfSelectedTopic() {
        List<StudySubject> list = new ArrayList<>();
        for (StudySubject s : allSubjects) if (s.topicId == viewModel.selectedTopicId) list.add(s);
        return list;
    }

    private void newModule() {
        if (viewModel.selectedTopicId == 0) return;
        UiUtils.promptText(requireContext(), R.string.study_module_new, R.string.study_module_name, "",
                name -> viewModel.addModule(viewModel.selectedTopicId, name), null);
    }

    private void newSubject(StudyModule module) {
        UiUtils.promptText(requireContext(), R.string.study_subject_new, R.string.study_subject_name, "",
                name -> viewModel.addSubject(module, name), null);
    }

    /** Menu ⋮ do módulo: novo assunto, renomear ou excluir (com os assuntos e o tempo). */
    private void moduleMenu(StudyModule module) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(module.name)
                .setItems(new CharSequence[]{getString(R.string.study_subject_new), getString(R.string.study_rename),
                        getString(R.string.delete)}, (d, which) -> {
                    if (which == 0) {
                        newSubject(module);
                    } else if (which == 1) {
                        UiUtils.promptText(requireContext(), R.string.study_module_edit, R.string.study_module_name,
                                module.name, name -> viewModel.renameModule(module, name), null);
                    } else {
                        UiUtils.confirmDelete(requireContext(), module.name, R.string.study_delete_module_msg,
                                () -> viewModel.deleteModule(module));
                    }
                })
                .show();
    }

    /** Menu ⋮ do assunto: marcar/desmarcar como concluído, renomear ou excluir. */
    private void subjectMenu(StudySubject subject) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(subject.name)
                .setItems(new CharSequence[]{
                        getString(subject.completed ? R.string.study_subject_undone : R.string.study_subject_done),
                        getString(R.string.study_rename), getString(R.string.delete)}, (d, which) -> {
                    if (which == 0) {
                        boolean done = !subject.completed;
                        viewModel.setCompleted(subject, done, done ? () -> {
                            if (isAdded()) ((com.alfred.agenda.MainActivity) requireActivity())
                                    .showMessage(Honorific.str(requireContext(), R.string.study_subject_done_msg));
                        } : null);
                    } else if (which == 1) {
                        UiUtils.promptText(requireContext(), R.string.study_subject_edit, R.string.study_subject_name,
                                subject.name, name -> viewModel.renameSubject(subject, name), null);
                    } else {
                        UiUtils.confirmDelete(requireContext(), subject.name, R.string.study_delete_subject_msg,
                                () -> viewModel.deleteSubject(subject));
                    }
                })
                .show();
    }

    private void renderSubjects() {
        if (adapter == null) return;
        List<StudyModule> modules = modulesOfSelectedTopic();
        List<StudySubject> shown = subjectsOfSelectedTopic();
        adapter.setData(modules, shown, logs, viewModel.collapsedModules);

        boolean hasModules = !topics.isEmpty() && !modules.isEmpty();
        summaryCard.setVisibility(topics.isEmpty() ? View.GONE : View.VISIBLE);
        modulesTitle.setVisibility(hasModules ? View.VISIBLE : View.GONE);
        if (topics.isEmpty()) {
            EmptyState.show(emptyState, R.drawable.ic_study, R.string.study_empty_title,
                    R.string.study_empty_topics, R.string.create_topic, this::newTopic);
        } else if (modules.isEmpty()) {
            EmptyState.show(emptyState, R.drawable.ic_study, R.string.study_empty_title,
                    R.string.study_empty_modules, R.string.create_module, this::newModule);
        } else {
            EmptyState.hide(emptyState);
        }
        renderSummary();
    }

    /** Resumo do tema e a atividade em destaque (atualizados a cada segundo pelo ticker). */
    private void renderSummary() {
        if (topics.isEmpty()) {
            focusTitle.setVisibility(View.GONE);
            focusCard.setVisibility(View.GONE);
            return;
        }
        long today = DateUtils.today();
        long weekStart = DateUtils.currentWeekStart();
        long t = 0, w = 0, all = 0;
        int studied = 0;
        List<StudySubject> shown = subjectsOfSelectedTopic();
        for (StudySubject s : shown) {
            long secsToday = StudyViewModel.seconds(s, logs, today, today);
            t += secsToday;
            if (secsToday > 0) studied++;
            w += StudyViewModel.seconds(s, logs, weekStart, today);
            all += StudyViewModel.seconds(s, logs, 0, today);
        }
        todayTime.setText(DateUtils.formatDuration(t));
        studiedCount.setText(getString(R.string.study_studied_today, studied, shown.size()));
        studiedBar.setProgressCompat(shown.isEmpty() ? 0 : Math.round(100f * studied / shown.size()), true);
        summary.setText(getString(R.string.study_week_total, DateUtils.formatDuration(w), DateUtils.formatDuration(all)));
        renderFocus(shown, today, weekStart);
    }

    /**
     * Atividade em destaque: o assunto com cronômetro rodando; senão, o último estudado
     * neste tema; senão, o primeiro assunto.
     */
    private void renderFocus(List<StudySubject> shown, long today, long weekStart) {
        if (shown.isEmpty()) {
            focusTitle.setVisibility(View.GONE);
            focusCard.setVisibility(View.GONE);
            return;
        }
        StudySubject focus = null;
        for (StudySubject s : shown) if (s.isRunning()) focus = s;
        if (focus == null) {
            long lastDay = Long.MIN_VALUE;
            for (StudyLog log : logs) {
                for (StudySubject s : shown) {
                    if (s.id == log.subjectId && log.epochDay > lastDay && log.seconds > 0) {
                        lastDay = log.epochDay;
                        focus = s;
                    }
                }
            }
        }
        if (focus == null) focus = shown.get(0);

        StudySubject subject = focus;
        boolean running = subject.isRunning();
        focusTitle.setVisibility(View.VISIBLE);
        focusCard.setVisibility(View.VISIBLE);
        focusTitle.setText(running ? R.string.study_focus_running : R.string.study_focus_next);
        focusName.setText(subject.name);
        focusDetail.setText(getString(R.string.study_focus_detail,
                DateUtils.formatClock(StudyViewModel.seconds(subject, logs, today, today)),
                DateUtils.formatDuration(StudyViewModel.seconds(subject, logs, weekStart, today))));
        focusDetail.setTextColor(ContextCompat.getColor(requireContext(), running ? R.color.study : R.color.text_secondary));
        focusButton.setIconResource(running ? R.drawable.ic_pause : R.drawable.ic_play);
        focusButton.setContentDescription(getString(running ? R.string.study_pause : R.string.study_start));
        focusButton.setOnClickListener(v -> viewModel.toggleTimer(subject));
        focusCard.setOnClickListener(v -> StudyHistorySheet.show(this, subject));
    }
}
