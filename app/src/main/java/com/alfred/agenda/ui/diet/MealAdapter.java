package com.alfred.agenda.ui.diet;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.alfred.agenda.R;
import com.alfred.agenda.util.Honorific;
import com.alfred.agenda.data.entity.MealItem;
import com.alfred.agenda.data.entity.MealWithItems;
import com.alfred.agenda.util.DateUtils;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lista de refeições (cartões) da aba Dieta. Cada alimento mostra a medida,
 * as substituições diretas e um atalho para o grupo da lista de substituição.
 */
public class MealAdapter extends RecyclerView.Adapter<MealAdapter.Holder> {

    public interface Listener {
        void onEdit(MealWithItems meal);

        void onDelete(MealWithItems meal);

        void onDoneChanged(MealWithItems meal, boolean done);

        /** Toque no atalho "Grupo X" de um alimento. */
        void onGroupClick(MealItem item);
    }

    private final Listener listener;
    private List<MealWithItems> meals = new ArrayList<>();
    private Set<Long> doneIds = new HashSet<>();
    /** Alimentos com as substituições abertas (sobrevive ao redesenho da lista). */
    private final Set<Long> expanded = new HashSet<>();

    public MealAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setMeals(List<MealWithItems> meals) {
        this.meals = meals;
        notifyDataSetChanged();
    }

    public void setDoneIds(Set<Long> doneIds) {
        this.doneIds = doneIds;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_meal, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        MealWithItems m = meals.get(position);
        Context ctx = h.itemView.getContext();
        h.time.setText(DateUtils.formatTime(m.meal.timeMinutes) + (m.meal.notifyEnabled ? " ⏰" : ""));
        h.name.setText(m.meal.name);

        // Alimentos
        h.items.removeAllViews();
        List<MealItem> items = m.sortedItems();
        if (items.isEmpty()) {
            TextView none = new TextView(ctx);
            none.setText(Honorific.str(none.getContext(), R.string.meal_no_items));
            none.setTextColor(ctx.getColor(R.color.text_secondary));
            h.items.addView(none);
        }
        for (MealItem item : items) h.items.addView(foodRow(ctx, h.items, item));

        h.notes.setText(m.meal.notes);
        h.notes.setVisibility(m.meal.notes.isEmpty() ? View.GONE : View.VISIBLE);

        // Remove o listener antes de marcar, senão o setChecked dispararia um "salvar"
        h.done.setOnCheckedChangeListener(null);
        h.done.setChecked(doneIds.contains(m.meal.id));
        h.done.setOnCheckedChangeListener((b, checked) -> listener.onDoneChanged(m, checked));

        h.edit.setOnClickListener(v -> listener.onEdit(m));
        h.delete.setOnClickListener(v -> listener.onDelete(m));
    }

    /** Monta a linha de um alimento: nome, medida, "ou ..." e o chip do grupo. */
    private View foodRow(Context ctx, ViewGroup parent, MealItem item) {
        View row = LayoutInflater.from(ctx).inflate(R.layout.item_meal_food, parent, false);
        TextView food = row.findViewById(R.id.food);
        TextView amount = row.findViewById(R.id.amount);
        TextView subs = row.findViewById(R.id.substitutions);
        TextView toggle = row.findViewById(R.id.substitutions_toggle);
        Chip group = row.findViewById(R.id.group_chip);

        food.setText(item.description);
        amount.setText(item.quantity);
        amount.setVisibility(item.quantity.isEmpty() ? View.GONE : View.VISIBLE);

        // Substituições diretas: recolhidas por padrão ("Ver 2 substituições"), uma por linha
        if (item.substitutions.isEmpty()) {
            subs.setVisibility(View.GONE);
            toggle.setVisibility(View.GONE);
        } else {
            String[] lines = item.substitutions.split("\n");
            StringBuilder sb = new StringBuilder();
            for (String s : lines) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(ctx.getString(R.string.substitution_prefix, s));
            }
            subs.setText(sb);
            String showText = ctx.getResources().getQuantityString(R.plurals.diet_substitutions_toggle,
                    lines.length, lines.length);
            Runnable sync = () -> {
                boolean open = expanded.contains(item.id);
                subs.setVisibility(open ? View.VISIBLE : View.GONE);
                toggle.setText(open ? ctx.getString(R.string.diet_substitutions_hide) : showText);
            };
            sync.run();
            toggle.setVisibility(View.VISIBLE);
            toggle.setOnClickListener(v -> {
                if (!expanded.remove(item.id)) expanded.add(item.id);
                sync.run();
            });
        }

        if (item.hasGroup()) {
            group.setText(ctx.getString(R.string.group_chip, item.groupNumber,
                    item.groupPortions.isEmpty() ? "1 porção" : item.groupPortions));
            group.setOnClickListener(v -> listener.onGroupClick(item));
            group.setVisibility(View.VISIBLE);
        } else {
            group.setVisibility(View.GONE);
        }
        return row;
    }

    @Override
    public int getItemCount() {
        return meals.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView time;
        final TextView name;
        final LinearLayout items;
        final TextView notes;
        final CheckBox done;
        final ImageButton edit;
        final ImageButton delete;

        Holder(View v) {
            super(v);
            time = v.findViewById(R.id.time);
            name = v.findViewById(R.id.name);
            items = v.findViewById(R.id.items_container);
            notes = v.findViewById(R.id.notes);
            done = v.findViewById(R.id.check_done);
            edit = v.findViewById(R.id.button_edit);
            delete = v.findViewById(R.id.button_delete);
        }
    }
}
