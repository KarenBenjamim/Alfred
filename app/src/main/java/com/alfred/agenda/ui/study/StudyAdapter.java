package com.alfred.agenda.ui.study;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.alfred.agenda.R;
import com.alfred.agenda.data.entity.StudyLog;
import com.alfred.agenda.data.entity.StudyModule;
import com.alfred.agenda.data.entity.StudySubject;
import com.alfred.agenda.util.DateUtils;
import com.alfred.agenda.util.StudyStats;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Lista de um tema: cada módulo (progresso + soma do tempo) seguido dos seus assuntos,
 * cada assunto com o próprio cronômetro. Módulos recolhidos mostram só o cartão do módulo.
 */
public class StudyAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    /** "Payload" para atualizar só os números a cada segundo (sem redesenhar o cartão inteiro). */
    static final Object TICK = new Object();

    private static final int TYPE_MODULE = 0;
    private static final int TYPE_SUBJECT = 1;
    private static final int TYPE_EMPTY = 2;

    public interface Listener {
        void onToggleModule(StudyModule module);

        void onModuleMenu(StudyModule module);

        void onAddSubject(StudyModule module);

        void onToggleTimer(StudySubject subject);

        void onSubjectMenu(StudySubject subject);

        void onOpenHistory(StudySubject subject);
    }

    /** Uma linha: um módulo, um assunto ou o aviso "módulo vazio". */
    private static final class Row {
        final int type;
        final StudyModule module;
        final StudySubject subject;

        Row(int type, StudyModule module, StudySubject subject) {
            this.type = type;
            this.module = module;
            this.subject = subject;
        }
    }

    private final Listener listener;
    private final List<Row> rows = new ArrayList<>();
    private List<StudySubject> subjects = new ArrayList<>();
    private List<StudyLog> logs = new ArrayList<>();
    private Set<Long> collapsed;

    public StudyAdapter(Listener listener) {
        this.listener = listener;
    }

    /**
     * @param modules   módulos do tema, na ordem
     * @param subjects  assuntos do tema (de todos os módulos)
     * @param collapsed ids dos módulos recolhidos
     */
    public void setData(List<StudyModule> modules, List<StudySubject> subjects, List<StudyLog> logs,
                        Set<Long> collapsed) {
        this.subjects = subjects;
        this.logs = logs;
        this.collapsed = collapsed;
        rows.clear();
        for (StudyModule m : modules) {
            rows.add(new Row(TYPE_MODULE, m, null));
            if (collapsed.contains(m.id)) continue;
            int before = rows.size();
            for (StudySubject s : subjects) if (s.inModule(m.id)) rows.add(new Row(TYPE_SUBJECT, m, s));
            if (rows.size() == before) rows.add(new Row(TYPE_EMPTY, m, null));
        }
        notifyDataSetChanged();
    }

    /** Atualiza os cronômetros rodando e os totais dos módulos deles (uma vez por segundo). */
    public void tick() {
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            boolean running = r.type == TYPE_SUBJECT ? r.subject.isRunning()
                    : r.type == TYPE_MODULE && moduleRunning(r.module.id);
            if (running) notifyItemChanged(i, TICK);
        }
    }

    public boolean anyRunning() {
        for (StudySubject s : subjects) if (s.isRunning()) return true;
        return false;
    }

    private boolean moduleRunning(long moduleId) {
        for (StudySubject s : subjects) if (s.inModule(moduleId) && s.isRunning()) return true;
        return false;
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_MODULE) return new ModuleHolder(inf.inflate(R.layout.item_study_module, parent, false));
        if (viewType == TYPE_SUBJECT) return new SubjectHolder(inf.inflate(R.layout.item_study_subject, parent, false));
        return new EmptyHolder(inf.inflate(R.layout.item_section_empty, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        Row r = rows.get(position);
        if (payloads.contains(TICK)) {
            if (holder instanceof SubjectHolder) bindTimes((SubjectHolder) holder, r.subject);
            else if (holder instanceof ModuleHolder) bindModuleNumbers((ModuleHolder) holder, r.module);
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row r = rows.get(position);
        if (holder instanceof ModuleHolder) bindModule((ModuleHolder) holder, r.module);
        else if (holder instanceof SubjectHolder) bindSubject((SubjectHolder) holder, r.subject);
        else ((EmptyHolder) holder).text.setText(R.string.study_module_empty);
    }

    // ---------------- Módulo ----------------

    private void bindModule(ModuleHolder h, StudyModule m) {
        Context ctx = h.itemView.getContext();
        boolean open = !collapsed.contains(m.id);
        h.name.setText(m.name);
        h.expand.setRotation(open ? 90 : 0);
        h.header.setContentDescription(ctx.getString(open ? R.string.study_module_collapse
                : R.string.study_module_expand, m.name));
        h.header.setOnClickListener(v -> listener.onToggleModule(m));
        h.more.setOnClickListener(v -> listener.onModuleMenu(m));
        h.add.setOnClickListener(v -> listener.onAddSubject(m));
        bindModuleNumbers(h, m);
    }

    /** Progresso (assuntos concluídos) e soma do tempo dos assuntos do módulo. */
    private void bindModuleNumbers(ModuleHolder h, StudyModule m) {
        Context ctx = h.itemView.getContext();
        StudyStats.ModuleSummary sum = StudyStats.module(m.id, subjects, logs, DateUtils.today(),
                System.currentTimeMillis(), ZoneId.systemDefault());
        h.percent.setText(ctx.getString(R.string.percent_value, sum.percent()));
        h.progress.setProgressCompat(sum.percent(), false);
        h.completed.setText(ctx.getResources().getQuantityString(R.plurals.study_module_completed,
                sum.subjects, sum.completed, sum.subjects));
        h.time.setText(ctx.getString(sum.running ? R.string.study_module_time_running : R.string.study_module_time,
                DateUtils.formatDuration(sum.secondsTotal), DateUtils.formatDuration(sum.secondsToday)));
        h.time.setTextColor(ContextCompat.getColor(ctx, sum.running ? R.color.study : R.color.text_secondary));
        int purple = ContextCompat.getColor(ctx, R.color.study);
        h.card.setStrokeColor(sum.running ? purple : ContextCompat.getColor(ctx, R.color.outline));
    }

    // ---------------- Assunto ----------------

    private void bindSubject(SubjectHolder h, StudySubject s) {
        Context ctx = h.itemView.getContext();
        boolean running = s.isRunning();

        h.name.setText(s.name);
        // Concluído: check à esquerda do nome, nome riscado e esmaecido
        h.name.setCompoundDrawablesRelativeWithIntrinsicBounds(s.completed ? R.drawable.ic_check_gold : 0, 0, 0, 0);
        if (s.completed) h.name.setPaintFlags(h.name.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        else h.name.setPaintFlags(h.name.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        h.name.setAlpha(s.completed && !running ? 0.7f : 1f);
        h.name.setContentDescription(s.completed ? ctx.getString(R.string.study_subject_done_a11y, s.name) : s.name);
        bindTimes(h, s);

        // Rodando: cronômetro e borda em roxo (cor de Estudos); o botão é sempre dourado (ação principal)
        int purple = ContextCompat.getColor(ctx, R.color.study);
        h.clock.setTextColor(running ? purple : ContextCompat.getColor(ctx, R.color.text_primary));
        h.card.setStrokeColor(running ? purple : ContextCompat.getColor(ctx, R.color.outline));
        h.timer.setIconResource(running ? R.drawable.ic_pause : R.drawable.ic_play);
        h.timer.setContentDescription(ctx.getString(running ? R.string.study_pause : R.string.study_start));
        h.timer.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.highlight)));
        h.timer.setIconTint(ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.on_highlight)));

        h.timer.setOnClickListener(v -> listener.onToggleTimer(s));
        h.edit.setOnClickListener(v -> listener.onSubjectMenu(s));
        h.itemView.setOnClickListener(v -> listener.onOpenHistory(s));
    }

    private void bindTimes(SubjectHolder h, StudySubject s) {
        long today = DateUtils.today();
        long weekStart = DateUtils.currentWeekStart();
        h.clock.setText(DateUtils.formatClock(StudyViewModel.seconds(s, logs, today, today)));
        h.totals.setText(h.itemView.getContext().getString(R.string.study_week,
                DateUtils.formatDuration(StudyViewModel.seconds(s, logs, weekStart, today))));
    }

    // ---------------- Holders ----------------

    static final class ModuleHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final View header;
        final ImageView expand;
        final TextView name;
        final TextView percent;
        final ImageButton more;
        final LinearProgressIndicator progress;
        final TextView completed;
        final TextView time;
        final MaterialButton add;

        ModuleHolder(View v) {
            super(v);
            card = v.findViewById(R.id.card);
            header = v.findViewById(R.id.header);
            expand = v.findViewById(R.id.expand);
            name = v.findViewById(R.id.name);
            percent = v.findViewById(R.id.percent);
            more = v.findViewById(R.id.button_more);
            progress = v.findViewById(R.id.progress);
            completed = v.findViewById(R.id.completed);
            time = v.findViewById(R.id.time);
            add = v.findViewById(R.id.button_add_subject);
        }
    }

    static final class SubjectHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView clock;
        final TextView totals;
        final ImageButton edit;
        final MaterialButton timer;
        final MaterialCardView card;

        SubjectHolder(View v) {
            super(v);
            card = v.findViewById(R.id.card);
            name = v.findViewById(R.id.name);
            clock = v.findViewById(R.id.clock);
            totals = v.findViewById(R.id.totals);
            edit = v.findViewById(R.id.button_edit);
            timer = v.findViewById(R.id.button_timer);
        }
    }

    static final class EmptyHolder extends RecyclerView.ViewHolder {
        final TextView text;

        EmptyHolder(View v) {
            super(v);
            text = (TextView) v; // item_section_empty é um TextView
        }
    }
}
