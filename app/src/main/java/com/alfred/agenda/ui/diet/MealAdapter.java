package com.alfred.agenda.ui.diet;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
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
import com.alfred.agenda.util.MealGroups;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lista de refeições (cartões) da aba Dieta. "Lanche - Opção 1/2/3" no mesmo horário viram um
 * cartão só ("Lanche"). Fechado, o cartão mostra horário, nome e "feita hoje" (com o resumo do
 * que foi comido). Aberto: as opções (qual foi feita, alimentos, substituições, atalho do grupo,
 * orientação, editar/excluir) e "O que comi de verdade".
 */
public class MealAdapter extends RecyclerView.Adapter<MealAdapter.Holder> {

    public interface Listener {
        void onEdit(MealWithItems meal);

        void onDelete(MealWithItems meal);

        /** Caixa do cabeçalho: com várias opções, quem chama pergunta qual foi feita. */
        void onDoneChanged(MealGroups.Group group, boolean done);

        /** "Fiz esta opção" dentro do cartão (as outras opções do horário são desmarcadas). */
        void onOptionChosen(MealGroups.Group group, MealWithItems option);

        /** Toque no atalho "Grupo X" de um alimento. */
        void onGroupClick(MealItem item);

        /** Registrar/editar o que foi comido hoje, guardado na opção feita (ou na 1ª). */
        void onEaten(MealGroups.Group group, MealWithItems target, String current);
    }

    private final Listener listener;
    private List<MealGroups.Group> groups = new ArrayList<>();
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
        this.groups = MealGroups.group(meals);
        notifyDataSetChanged();
    }

    /** O que foi comido hoje na refeição: o texto da opção feita (ou de qualquer opção). */
    String eatenOf(MealGroups.Group g) {
        MealWithItems done = g.doneOption(doneIds);
        if (done != null && eatenToday.containsKey(done.meal.id)) return eatenToday.get(done.meal.id);
        for (MealWithItems o : g.options) if (eatenToday.containsKey(o.meal.id)) return eatenToday.get(o.meal.id);
        return "";
    }

    /** Onde guardar o "o que comi": a opção feita, a que já tem texto ou a 1ª. */
    MealWithItems eatenTarget(MealGroups.Group g) {
        MealWithItems done = g.doneOption(doneIds);
        if (done != null) return done;
        for (MealWithItems o : g.options) if (eatenToday.containsKey(o.meal.id)) return o;
        return g.options.get(0);
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
        MealGroups.Group g = groups.get(position);
        MealWithItems first = g.options.get(0);
        boolean single = g.options.size() == 1;
        Context ctx = h.itemView.getContext();
        String title = single ? first.meal.name : g.name;
        boolean notify = false;
        for (MealWithItems o : g.options) notify |= o.meal.notifyEnabled;
        h.time.setText(DateUtils.formatTime(g.timeMinutes) + (notify ? " ⏰" : ""));
        h.name.setText(title);

        // Abrir/fechar pelo cabeçalho (a refeição é identificada pela 1ª opção)
        long key = first.meal.id;
        boolean isOpen = open.contains(key);
        h.body.setVisibility(isOpen ? View.VISIBLE : View.GONE);
        h.chevron.setRotation(isOpen ? 90f : 0f);
        h.header.setContentDescription(ctx.getString(isOpen ? R.string.meal_collapse : R.string.meal_expand, title));
        h.header.setOnClickListener(v -> {
            if (!open.remove(key)) open.add(key);
            notifyItemChanged(h.getBindingAdapterPosition());
        });

        // Resumo no cabeçalho: o que foi comido ou, com várias opções, qual foi feita / quantas há
        MealWithItems doneOption = g.doneOption(doneIds);
        String eaten = eatenOf(g);
        String summary;
        if (!eaten.isEmpty()) {
            summary = ctx.getString(R.string.meal_eaten_summary, eaten);
        } else if (single) {
            summary = "";
        } else if (doneOption != null) {
            summary = ctx.getString(R.string.meal_option_done, MealGroups.optionLabel(doneOption.meal.name));
        } else {
            summary = ctx.getResources().getQuantityString(R.plurals.meal_option_count, g.options.size(), g.options.size());
        }
        h.eatenSummary.setText(summary);
        h.eatenSummary.setVisibility(summary.isEmpty() || isOpen ? View.GONE : View.VISIBLE);
        h.eatenText.setText(eaten.isEmpty() ? ctx.getString(R.string.meal_eaten_none) : eaten);
        h.eatenText.setTextColor(ctx.getColor(eaten.isEmpty() ? R.color.text_secondary : R.color.text_primary));
        h.eatenButton.setText(eaten.isEmpty() ? R.string.meal_eaten_add : R.string.meal_eaten_edit);
        h.eatenButton.setOnClickListener(v -> listener.onEaten(g, eatenTarget(g), eaten));

        h.items.removeAllViews();
        if (single) {
            // Uma opção só: alimentos direto no cartão, editar/excluir embaixo
            addFoods(ctx, h.items, first);
            h.notes.setText(first.meal.notes);
            h.notes.setVisibility(first.meal.notes.isEmpty() ? View.GONE : View.VISIBLE);
            h.edit.setVisibility(View.VISIBLE);
            h.delete.setVisibility(View.VISIBLE);
            h.edit.setOnClickListener(v -> listener.onEdit(first));
            h.delete.setOnClickListener(v -> listener.onDelete(first));
        } else {
            // Várias opções: um bloco por opção, cada um com "fiz esta", editar e excluir
            h.notes.setVisibility(View.GONE);
            h.edit.setVisibility(View.GONE);
            h.delete.setVisibility(View.GONE);
            for (int i = 0; i < g.options.size(); i++) {
                MealWithItems o = g.options.get(i);
                h.items.addView(optionBlock(ctx, h.items, g, o, i == 0, o == doneOption));
            }
        }

        // Remove o listener antes de marcar, senão o setChecked dispararia um "salvar"
        h.done.setOnCheckedChangeListener(null);
        h.done.setChecked(doneOption != null);
        h.done.setContentDescription(ctx.getString(R.string.meal_done_a11y, title));
        h.done.setOnCheckedChangeListener((b, checked) -> listener.onDoneChanged(g, checked));
    }

    private void addFoods(Context ctx, LinearLayout box, MealWithItems m) {
        List<MealItem> items = m.sortedItems();
        if (items.isEmpty()) {
            TextView none = new TextView(ctx);
            none.setText(Honorific.str(none.getContext(), R.string.meal_no_items));
            none.setTextColor(ctx.getColor(R.color.text_secondary));
            box.addView(none);
        }
        for (MealItem item : items) box.addView(foodRow(ctx, box, item));
    }

    /** Bloco de uma opção: "Opção 2" (marcar como a feita), editar, excluir, alimentos e orientação. */
    private View optionBlock(Context ctx, ViewGroup parent, MealGroups.Group g, MealWithItems o,
                             boolean isFirst, boolean isDone) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_meal_option, parent, false);
        v.findViewById(R.id.divider).setVisibility(isFirst ? View.GONE : View.VISIBLE);
        String label = MealGroups.optionLabel(o.meal.name);
        CompoundButton done = v.findViewById(R.id.option_done);
        done.setText(label);
        done.setChecked(isDone);
        done.setContentDescription(ctx.getString(R.string.meal_option_choose, label));
        // Tocar na opção já feita a desmarca; tocar em outra troca a escolha
        done.setOnClickListener(x -> {
            if (isDone) listener.onDoneChanged(g, false);
            else listener.onOptionChosen(g, o);
        });
        View edit = v.findViewById(R.id.option_edit);
        View delete = v.findViewById(R.id.option_delete);
        edit.setContentDescription(ctx.getString(R.string.meal_option_edit, label));
        delete.setContentDescription(ctx.getString(R.string.meal_option_delete, label));
        edit.setOnClickListener(x -> listener.onEdit(o));
        delete.setOnClickListener(x -> listener.onDelete(o));
        addFoods(ctx, v.findViewById(R.id.option_items), o);
        TextView notes = v.findViewById(R.id.option_notes);
        notes.setText(o.meal.notes);
        notes.setVisibility(o.meal.notes.isEmpty() ? View.GONE : View.VISIBLE);
        return v;
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
        return groups.size();
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
