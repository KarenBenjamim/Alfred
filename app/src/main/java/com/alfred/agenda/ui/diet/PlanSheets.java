package com.alfred.agenda.ui.diet;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.alfred.agenda.R;
import com.alfred.agenda.util.Honorific;
import com.alfred.agenda.account.UserProfile;
import com.alfred.agenda.data.NutritionPlan;
import com.alfred.agenda.util.DateUtils;
import com.alfred.agenda.util.UiUtils;
import com.alfred.agenda.util.WaterGoal;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.Normalizer;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Janelas de consulta do plano alimentar:
 *  - grupo de substituição (painel que sobe de baixo, com busca);
 *  - orientações da nutricionista;
 *  - meta de água (pelo peso do perfil; o consumo é registrado na aba Hábitos).
 */
public final class PlanSheets {

    private PlanSheets() {
    }

    // ---------------- Grupo de substituição ----------------

    /**
     * @param portions      porções que o item do plano vale (ex.: "2,5 porções"); vazio se aberto pelos atalhos
     * @param highlightFood alimento do plano, destacado em amarelo na lista; null se não houver
     */
    public static void showGroup(Context ctx, int number, String portions, @Nullable String highlightFood) {
        NutritionPlan.Group group = NutritionPlan.get(ctx).group(number);
        if (group == null) {
            // Sem planilha importada (ou o grupo não existe nela)
            UiUtils.toast(ctx, ctx.getString(R.string.group_missing, number));
            return;
        }

        BottomSheetDialog sheet = new BottomSheetDialog(ctx);
        View view = LayoutInflater.from(ctx).inflate(R.layout.sheet_group, null);
        TextView title = view.findViewById(R.id.title);
        TextView subtitle = view.findViewById(R.id.subtitle);
        EditText search = view.findViewById(R.id.search);
        RecyclerView list = view.findViewById(R.id.list);

        title.setText(ctx.getString(R.string.group_title, group.number, group.name));
        subtitle.setText(portions.isEmpty() || portions.startsWith("1 ")
                ? ctx.getString(R.string.group_subtitle)
                : Honorific.str(ctx, R.string.group_subtitle_portions, portions));

        FoodAdapter adapter = new FoodAdapter(group.foods, highlightFood);
        list.setAdapter(adapter);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        sheet.setContentView(view);
        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().setSkipCollapsed(true);
        sheet.show();
    }

    /** Lista de alimentos de um grupo, com filtro por texto (ignorando acentos). */
    private static final class FoodAdapter extends RecyclerView.Adapter<FoodAdapter.Holder> {
        private final List<NutritionPlan.Food> all;
        private List<NutritionPlan.Food> shown;
        @Nullable
        private final String highlight;

        FoodAdapter(List<NutritionPlan.Food> foods, @Nullable String highlight) {
            this.all = foods;
            this.shown = foods;
            this.highlight = highlight == null ? null : normalize(highlight);
        }

        void filter(String query) {
            String q = normalize(query.trim());
            if (q.isEmpty()) {
                shown = all;
            } else {
                shown = new ArrayList<>();
                for (NutritionPlan.Food f : all) if (normalize(f.food).contains(q)) shown.add(f);
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group_food, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            NutritionPlan.Food f = shown.get(position);
            Context ctx = h.itemView.getContext();
            h.food.setText(f.food);
            h.amount.setText(f.amount());
            // O alimento que está no plano fica em amarelo
            boolean isPlanFood = highlight != null && normalize(f.food).equals(highlight);
            h.food.setTextColor(ContextCompat.getColor(ctx, isPlanFood ? R.color.highlight : R.color.text_primary));
            h.food.setTypeface(null, isPlanFood ? Typeface.BOLD : Typeface.NORMAL);
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final TextView food;
            final TextView amount;

            Holder(View v) {
                super(v);
                food = v.findViewById(R.id.food);
                amount = v.findViewById(R.id.amount);
            }
        }
    }

    /** "Pão Francês" -> "pao frances" (para a busca não depender de acento nem maiúscula). */
    private static String normalize(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    // ---------------- Orientações ----------------

    public static void showNotes(Context ctx) {
        BottomSheetDialog sheet = new BottomSheetDialog(ctx);
        View view = LayoutInflater.from(ctx).inflate(R.layout.sheet_notes, null);
        LinearLayout container = view.findViewById(R.id.container);
        float density = ctx.getResources().getDisplayMetrics().density;

        for (NutritionPlan.Note note : NutritionPlan.get(ctx).notes) {
            TextView topic = new TextView(ctx);
            topic.setText(note.topic);
            topic.setTextSize(15);
            topic.setTypeface(null, Typeface.BOLD);
            topic.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary));
            topic.setPadding(0, Math.round(14 * density), 0, Math.round(2 * density));
            container.addView(topic);

            TextView text = new TextView(ctx);
            text.setText(note.text);
            text.setTextSize(14);
            text.setLineSpacing(2 * density, 1f);
            text.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
            container.addView(text);
        }

        sheet.setContentView(view);
        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().setSkipCollapsed(true);
        sheet.show();
    }

    // ---------------- Água ----------------

    /** Meta diária de água: peso do perfil × ml por kg (35, ou o valor da planilha importada). */
    public static void showWater(Context ctx) {
        NutritionPlan plan = NutritionPlan.get(ctx);
        View view = LayoutInflater.from(ctx).inflate(R.layout.dialog_water, null);
        TextView result = view.findViewById(R.id.result);
        TextView rule = view.findViewById(R.id.rule);
        TextView tips = view.findViewById(R.id.tips);

        int perKg = WaterGoal.mlPerKg(ctx);
        float kg = UserProfile.current(ctx).weightKg;
        rule.setText(ctx.getString(R.string.water_rule, formatNumber(kg, 1), perKg));
        tips.setText(plan.waterTips.isEmpty() ? "" : ctx.getString(R.string.water_tips, plan.waterTips));
        tips.setVisibility(plan.waterTips.isEmpty() ? View.GONE : View.VISIBLE);

        int ml = WaterGoal.goalMl(kg, perKg);
        if (ml <= 0) {
            result.setText(R.string.water_no_weight);
            rule.setVisibility(View.GONE);
        } else {
            result.setText(ctx.getString(R.string.water_result, formatNumber(ml, 0),
                    formatNumber(ml / 1000f, 1), Math.round(ml / 250f)));
        }

        new MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.diet_water)
                .setView(view)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    private static String formatNumber(double value, int decimals) {
        NumberFormat f = NumberFormat.getNumberInstance(DateUtils.PT_BR);
        f.setMinimumFractionDigits(0);
        f.setMaximumFractionDigits(decimals);
        return f.format(value);
    }
}
