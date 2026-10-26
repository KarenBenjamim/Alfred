package com.alfred.agenda.ui.tasks;

import android.content.Context;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import com.alfred.agenda.R;
import com.alfred.agenda.data.entity.Tag;
import com.alfred.agenda.data.entity.Task;
import com.alfred.agenda.util.DateUtils;
import com.alfred.agenda.util.TagColors;
import com.alfred.agenda.util.TagPalette;
import com.alfred.agenda.util.TaskAppearance;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lista de tarefas com separadores. Cada linha é um Row: um cabeçalho (dia), um subtítulo
 * de período, uma tarefa (TaskCard) ou o aviso de período vazio. Quem monta é o TasksFragment.
 */
public class TaskAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        void onEdit(Task task);

        void onDelete(Task task);

        void onDoneChanged(Task task, boolean done);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_TASK = 1;
    private static final int TYPE_EMPTY = 2;
    private static final int TYPE_SUBHEADER = 3;

    /** Uma linha da lista. */
    public static final class Row {
        final int type;
        final String header;
        final Task task;
        /** Cor do texto do cabeçalho (0 = padrão). */
        final int headerColor;

        private Row(int type, String header, Task task, int headerColor) {
            this.type = type;
            this.header = header;
            this.task = task;
            this.headerColor = headerColor;
        }

        public static Row header(String title) {
            return new Row(TYPE_HEADER, title, null, 0);
        }

        /** Cabeçalho com cor própria (ex.: "Atrasadas" em vermelho). */
        public static Row header(String title, int color) {
            return new Row(TYPE_HEADER, title, null, color);
        }

        public static Row task(Task task) {
            return new Row(TYPE_TASK, null, task, 0);
        }

        public static Row empty() {
            return new Row(TYPE_EMPTY, null, null, 0);
        }

        /** Período dentro de um dia ("Manhã", "Tarde"...), no modo Agenda. */
        public static Row subheader(String title) {
            return new Row(TYPE_SUBHEADER, title, null, 0);
        }
    }

    private final Listener listener;
    private List<Row> rows = new ArrayList<>();
    private Map<Long, List<Tag>> tagsByTask = new HashMap<>();
    private boolean showPeriod = true;

    public TaskAdapter(Listener listener) {
        this.listener = listener;
    }

    /**
     * @param tagsByTask tags de cada tarefa (id da tarefa -> tags); vazio no Trabalho
     * @param showPeriod mostra o período no cartão (no modo Agenda ele já está no subtítulo)
     */
    public void setRows(List<Row> rows, Map<Long, List<Tag>> tagsByTask, boolean showPeriod) {
        this.rows = rows;
        this.tagsByTask = tagsByTask;
        this.showPeriod = showPeriod;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(inflater.inflate(R.layout.item_section_header, parent, false));
        }
        if (viewType == TYPE_SUBHEADER) {
            return new HeaderHolder(inflater.inflate(R.layout.item_section_subheader, parent, false));
        }
        if (viewType == TYPE_EMPTY) {
            return new RecyclerView.ViewHolder(inflater.inflate(R.layout.item_section_empty, parent, false)) {
            };
        }
        return new TaskHolder(inflater.inflate(R.layout.item_task, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (holder instanceof HeaderHolder) {
            HeaderHolder h = (HeaderHolder) holder;
            h.title.setText(row.header);
            if (row.type == TYPE_HEADER) {
                h.title.setTextColor(row.headerColor != 0 ? row.headerColor
                        : ContextCompat.getColor(h.itemView.getContext(), R.color.text_primary));
            }
        } else if (holder instanceof TaskHolder) {
            bindTask((TaskHolder) holder, row.task);
        }
    }

    private void bindTask(TaskHolder h, Task t) {
        Context ctx = h.itemView.getContext();
        String[] priorities = ctx.getResources().getStringArray(R.array.priority_options);
        String[] statuses = ctx.getResources().getStringArray(R.array.status_options);
        String[] periods = ctx.getResources().getStringArray(R.array.period_options);

        h.title.setText(t.title);
        h.description.setText(t.description);
        h.description.setVisibility(t.description.isEmpty() ? View.GONE : View.VISIBLE);
        h.period.setText(periods[t.period]);
        h.period.setVisibility(showPeriod ? View.VISIBLE : View.GONE);

        // Aparência a partir das tags reais (sem tags: grafite + faixa da prioridade)
        List<Tag> tags = TagPalette.ordered(tagsByTask.getOrDefault(t.id, Collections.emptyList()));
        TaskAppearance.apply(h.card, h.content, h.strip, tags,
                ContextCompat.getColor(ctx, priorityColor(t.priority)), !t.isPersonal());

        // Etiquetas: status, prioridade, atraso e o nome de cada tag
        h.pills.removeAllViews();
        addPill(h.pills, statuses[t.status], ContextCompat.getColor(ctx, statusColor(t.status)));
        addPill(h.pills, priorities[t.priority], ContextCompat.getColor(ctx, priorityColor(t.priority)));
        if (!t.isDone() && t.dueEpochDay < DateUtils.today()) {
            addPill(h.pills, ctx.getString(R.string.overdue, DateUtils.formatDateShortOrYear(t.dueEpochDay)),
                    ContextCompat.getColor(ctx, R.color.error));
        }
        for (Tag tag : tags) addPill(h.pills, tag.name, TagColors.color(ctx, tag.colorIndex));

        // Concluídas ficam riscadas e esmaecidas
        if (t.isDone()) {
            h.title.setPaintFlags(h.title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            h.itemView.setAlpha(0.6f);
        } else {
            h.title.setPaintFlags(h.title.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            h.itemView.setAlpha(1f);
        }

        // Remove o listener antes de marcar, senão o setChecked dispararia um "salvar"
        h.done.setOnCheckedChangeListener(null);
        h.done.setChecked(t.isDone());
        h.done.setContentDescription(t.title);
        h.done.setOnCheckedChangeListener((b, checked) -> listener.onDoneChanged(t, checked));

        h.itemView.setOnClickListener(v -> listener.onEdit(t));
        h.more.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(v.getContext(), v);
            menu.getMenu().add(0, 1, 0, R.string.edit);
            menu.getMenu().add(0, 2, 1, R.string.delete);
            menu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) listener.onEdit(t);
                else listener.onDelete(t);
                return true;
            });
            menu.show();
        });
    }

    /** Etiqueta pequena: texto na cor, fundo bem suave na mesma cor. */
    private static void addPill(ChipGroup group, String text, int color) {
        TextView pill = (TextView) LayoutInflater.from(group.getContext())
                .inflate(R.layout.view_pill, group, false);
        pill.setText(text);
        pill.setTextColor(color);
        pill.getBackground().mutate().setTint(ColorUtils.setAlphaComponent(color, 0x2E));
        group.addView(pill);
    }

    private static int priorityColor(int priority) {
        switch (priority) {
            case Task.PRIORITY_HIGH: return R.color.priority_high;
            case Task.PRIORITY_LOW: return R.color.priority_low;
            default: return R.color.priority_medium;
        }
    }

    private static int statusColor(int status) {
        switch (status) {
            case Task.STATUS_IN_PROGRESS: return R.color.info;
            case Task.STATUS_DONE: return R.color.success;
            default: return R.color.text_secondary;
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView title;

        HeaderHolder(View v) {
            super(v);
            title = v.findViewById(R.id.header_title);
        }
    }

    static class TaskHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        /** Conteúdo do cartão: recebe o fundo com as cores das tags. */
        final View content;
        final View strip;
        final CheckBox done;
        final TextView title;
        final TextView description;
        final TextView period;
        final ChipGroup pills;
        final ImageButton more;

        TaskHolder(View v) {
            super(v);
            card = v.findViewById(R.id.card);
            content = card.getChildAt(0);
            strip = v.findViewById(R.id.priority_strip);
            done = v.findViewById(R.id.check_done);
            title = v.findViewById(R.id.title);
            description = v.findViewById(R.id.description);
            period = v.findViewById(R.id.period);
            pills = v.findViewById(R.id.pills);
            more = v.findViewById(R.id.button_more);
        }
    }
}
