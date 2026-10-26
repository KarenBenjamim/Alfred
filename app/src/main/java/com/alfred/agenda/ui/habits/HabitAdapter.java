package com.alfred.agenda.ui.habits;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.alfred.agenda.R;
import com.alfred.agenda.data.entity.Habit;
import com.alfred.agenda.util.WeekDots;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * HabitCard: nome, frequência da semana ("4/7 nesta semana") e as bolinhas Seg..Dom.
 * Tocar numa bolinha (hoje ou dia passado) marca/desmarca aquele dia.
 * Editar e excluir ficam no menu ⋮.
 */
public class HabitAdapter extends RecyclerView.Adapter<HabitAdapter.Holder> {

    public interface Listener {
        void onEdit(Habit habit);

        void onDelete(Habit habit);

        /** Marcar/desmarcar o hábito num dia (tocando na bolinha). */
        void onDoneChanged(Habit habit, long epochDay, boolean done);
    }

    private final Listener listener;
    private List<Habit> habits = new ArrayList<>();
    private Map<Long, Set<Long>> doneByHabit = new HashMap<>();
    private long weekStart;
    private long today;

    public HabitAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setData(List<Habit> habits, Map<Long, Set<Long>> doneByHabit, long weekStart, long today) {
        this.habits = habits;
        this.doneByHabit = doneByHabit;
        this.weekStart = weekStart;
        this.today = today;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_habit, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Habit habit = habits.get(position);
        Set<Long> done = doneByHabit.getOrDefault(habit.id, Collections.emptySet());

        h.name.setText(habit.name);
        h.icon.setImageResource(iconFor(habit.name));

        // Frequência: dias feitos nesta semana, até hoje
        int count = 0;
        for (long d = weekStart; d <= today; d++) if (done.contains(d)) count++;
        h.frequency.setText(h.itemView.getContext().getString(R.string.habit_frequency, count,
                (int) (today - weekStart + 1)));

        // Tocar numa bolinha (hoje ou dia passado) alterna aquele dia
        WeekDots.render(h.weekDots, weekStart, today, done::contains,
                day -> listener.onDoneChanged(habit, day, !done.contains(day)));

        h.more.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(v.getContext(), v);
            menu.getMenu().add(0, 1, 0, R.string.edit);
            menu.getMenu().add(0, 2, 1, R.string.delete);
            menu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) listener.onEdit(habit);
                else listener.onDelete(habit);
                return true;
            });
            menu.show();
        });
    }

    /** Ícone pelo assunto do hábito (só visual; o hábito em si não muda). */
    static int iconFor(String name) {
        String n = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        if (n.contains("agua") || n.contains("beber")) return R.drawable.ic_water;
        if (n.contains("fruta") || n.contains("verdura") || n.contains("comer") || n.contains("refei")) return R.drawable.ic_diet;
        if (n.contains("corpo") || n.contains("caminh") || n.contains("exerc") || n.contains("trein")) return R.drawable.ic_walk;
        if (n.contains("dormir") || n.contains("sono")) return R.drawable.ic_moon;
        if (n.contains("tela") || n.contains("celular")) return R.drawable.ic_screen_off;
        if (n.contains("estud") || n.contains("ler") || n.contains("leitura")) return R.drawable.ic_study;
        return R.drawable.ic_habits;
    }

    @Override
    public int getItemCount() {
        return habits.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;
        final TextView frequency;
        final ImageButton more;
        final LinearLayout weekDots;

        Holder(View v) {
            super(v);
            icon = v.findViewById(R.id.icon);
            name = v.findViewById(R.id.name);
            frequency = v.findViewById(R.id.frequency);
            more = v.findViewById(R.id.button_more);
            weekDots = v.findViewById(R.id.week_dots);
        }
    }
}
