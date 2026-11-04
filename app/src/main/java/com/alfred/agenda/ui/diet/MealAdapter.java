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
import com.alfred.agenda.data.entity.MealLog;
import com.alfred.agenda.data.entity.MealWithItems;
import com.alfred.agenda.util.DateUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lista de refeições (cartões) da aba Dieta. Fechado, o cartão mostra só horário, nome e
 * "feita hoje" (com o resumo do que foi comido). Aberto: cada alimento com a medida, as
 * substituições e o atalho para o grupo; a orientação; "O que comi de verdade"; editar/excluir.
 */
public class MealAdapter extends RecyclerView.Adapter<MealAdapter.Holder> {

    public interface Listener {
        void onEdit(MealWithItems meal);

        void onDelete(MealWithItems meal);

        void onDoneChanged(MealWithItems meal, boolean done);

        /** Toque no atalho "Grupo X" de um alimento. */
        void onGroupClick(MealItem item);

        /** Registrar/editar o que foi comido de verdade hoje (current = texto atual). */
        void onEaten(MealWithItems meal, String current);
    }

    private final Listener listener;
    private List<MealWithItems> meals = new ArrayList<>();
    private Set<Long> doneIds = new HashSet<>();
    /** Refeição -> o que foi comido hoje. */
    private Map<Long, String> eatenToday = new HashMap<>();
    /** Refeições abertas (sobrevive ao redesenho da lista). */
    private final Set<Long> open = new HashSet<>();
    /** Alimentos com as substituições abertas (sobrevive ao redesenho da lista). */
    private final Set<Long> expanded = new HashSet<>();

    public MealAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setMeals(List<MealWithItems> meals) {
        this.meals = meals;
        notifyDataSetChanged();
    }

    /** Registros de hoje: quais refeições foram feitas e o que foi comido nelas. */
    public void setLogs(List<MealLog> logs) {
        doneIds = new HashSet<>();
        eatenToday = new HashMap<>();
        for (MealLog l : logs) {
            doneIds.add(l.mealId);
            if (!l.eaten.isEmpty()) eatenToday.put(l.mealId, l.eaten);
        }
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

        // Abrir/fechar pelo cabeçalho
        boolean isOpen = open.contains(m.meal.id);
        h.body.setVisibility(isOpen ? View.VISIBLE : View.GONE);
        h.chevron.setRotation(isOpen ? 90f : 0f);
        h.header.setContentDescription(ctx.getString(isOpen ? R.string.meal_collapse : R.string.meal_expand, m.meal.name));
        h.header.setOnClickListener(v -> {
            if (!open.remove(m.meal.id)) open.add(m.meal.id);
            notifyItemChanged(h.getBindingAdapterPosition());
        });

        // O que foi comido de verdade hoje (resumo no cabeçalho e texto completo no corpo)
        String eaten = eatenToday.getOrDefault(m.meal.id, "");
        h.eatenSummary.setText(ctx.getString(R.string.meal_eaten_summary, eaten));
        h.eatenSummary.setVisibility(eaten.isEmpty() || isOpen ? View.GONE : View.VISIBLE);
        h.eatenText.setText(eaten.isEmpty() ? ctx.getString(R.string.meal_eaten_none) : eaten);
        h.eatenText.setTextColor(ctx.getColor(eaten.isEmpty() ? R.color.text_secondary : R.color.text_primary));
        h.eatenButton.setText(eaten.isEmpty() ? R.string.meal_eaten_add : R.string.meal_eaten_edit);
        h.eatenButton.setOnClickListener(v -> listener.onEaten(m, eaten));

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
        h.done.setContentDescription(ctx.getString(R.string.meal_done_a11y, m.meal.name));
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
        final View header;
        final View body;
        final View chevron;
        final TextView eatenSummary;
        final TextView eatenText;
        final MaterialButton eatenButton;

        Holder(View v) {
            super(v);
            header = v.findViewById(R.id.header);
            body = v.findViewById(R.id.body);
            chevron = v.findViewById(R.id.chevron);
            eatenSummary = v.findViewById(R.id.eaten_summary);
            eatenText = v.findViewById(R.id.eaten_text);
            eatenButton = v.findViewById(R.id.button_eaten);
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
