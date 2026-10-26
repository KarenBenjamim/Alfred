package com.alfred.agenda.ui.study;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.alfred.agenda.R;
import com.alfred.agenda.data.entity.StudyLog;
import com.alfred.agenda.data.entity.StudySubject;
import com.alfred.agenda.util.DateUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/** Assuntos de um tema, cada um com seu cronômetro. */
public class SubjectAdapter extends RecyclerView.Adapter<SubjectAdapter.Holder> {

    /** "Payload" para atualizar só os números a cada segundo (sem redesenhar o cartão inteiro). */
    static final Object TICK = new Object();

    public interface Listener {
        void onToggle(StudySubject subject);

        void onEdit(StudySubject subject);

        void onOpenHistory(StudySubject subject);
    }

    private final Listener listener;
    private List<StudySubject> subjects = new ArrayList<>();
    private List<StudyLog> logs = new ArrayList<>();

    public SubjectAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setData(List<StudySubject> subjects, List<StudyLog> logs) {
        this.subjects = subjects;
        this.logs = logs;
        notifyDataSetChanged();
    }

    /** Atualiza os cronômetros que estão rodando (chamado uma vez por segundo). */
    public void tick() {
        for (int i = 0; i < subjects.size(); i++) {
            if (subjects.get(i).isRunning()) notifyItemChanged(i, TICK);
        }
    }

    public boolean anyRunning() {
        for (StudySubject s : subjects) if (s.isRunning()) return true;
        return false;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_study_subject, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position, @NonNull List<Object> payloads) {
        if (payloads.contains(TICK)) bindTimes(h, subjects.get(position));
        else super.onBindViewHolder(h, position, payloads);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        StudySubject s = subjects.get(position);
        Context ctx = h.itemView.getContext();
        boolean running = s.isRunning();

        h.name.setText(s.name);
        bindTimes(h, s);

        // Rodando: cronômetro e borda em roxo (cor de Estudos); o botão é sempre dourado (ação principal)
        int purple = ContextCompat.getColor(ctx, R.color.study);
        h.clock.setTextColor(running ? purple : ContextCompat.getColor(ctx, R.color.text_primary));
        h.card.setStrokeColor(running ? purple : ContextCompat.getColor(ctx, R.color.outline));
        h.timer.setIconResource(running ? R.drawable.ic_pause : R.drawable.ic_play);
        h.timer.setContentDescription(ctx.getString(running ? R.string.study_pause : R.string.study_start));
        h.timer.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.highlight)));
        h.timer.setIconTint(ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.on_highlight)));

        h.timer.setOnClickListener(v -> listener.onToggle(s));
        h.edit.setOnClickListener(v -> listener.onEdit(s));
        h.itemView.setOnClickListener(v -> listener.onOpenHistory(s));
    }

    private void bindTimes(Holder h, StudySubject s) {
        long today = DateUtils.today();
        long weekStart = DateUtils.currentWeekStart();
        h.clock.setText(DateUtils.formatClock(StudyViewModel.seconds(s, logs, today, today)));
        h.totals.setText(h.itemView.getContext().getString(R.string.study_week,
                DateUtils.formatDuration(StudyViewModel.seconds(s, logs, weekStart, today))));
    }

    @Override
    public int getItemCount() {
        return subjects.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView clock;
        final TextView totals;
        final ImageButton edit;
        final MaterialButton timer;
        final MaterialCardView card;

        Holder(View v) {
            super(v);
            card = v.findViewById(R.id.card);
            name = v.findViewById(R.id.name);
            clock = v.findViewById(R.id.clock);
            totals = v.findViewById(R.id.totals);
            edit = v.findViewById(R.id.button_edit);
            timer = v.findViewById(R.id.button_timer);
        }
    }
}
