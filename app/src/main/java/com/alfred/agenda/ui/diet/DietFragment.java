package com.alfred.agenda.ui.diet;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.alfred.agenda.MainActivity;
import com.alfred.agenda.R;
import com.alfred.agenda.account.AccountStore;
import com.alfred.agenda.data.NutritionPlan;
import com.alfred.agenda.data.PlanImport;
import com.alfred.agenda.data.entity.MealItem;
import com.alfred.agenda.data.entity.MealLog;
import com.alfred.agenda.data.entity.MealWithItems;
import com.alfred.agenda.util.DateUtils;
import com.alfred.agenda.util.EmptyState;
import com.alfred.agenda.util.Honorific;
import com.alfred.agenda.util.UiUtils;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Aba "Dieta": plano alimentar da nutricionista, importado de uma planilha Excel.
 *  - No primeiro acesso, pede a planilha (formato do template) e oferece baixar o template.
 *  - Antes de salvar, mostra uma prévia e pede confirmação (avisa se sobraram linhas de exemplo).
 *  - Atalhos: Água, Orientações, Importar nova planilha, Baixar template e os grupos de substituição.
 *  - Em cada alimento, um chip "Grupo X" abre direto a lista de substituição daquele grupo.
 */
public class DietFragment extends Fragment {

    private static final String PREFS = "alfred_prefs";

    private DietViewModel viewModel;
    private final ExecutorService files = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private TextView source;
    private View importCard;
    private TextView importText;
    private View importProgress;
    private View planError;
    private View groupsTitle;
    private ChipGroup actions;
    private ChipGroup groups;
    private View mealsEmpty;
    private View mealsTitle;
    private int mealCount;

    /** Seletor de arquivos do sistema (não precisa de permissão de armazenamento). */
    private final ActivityResultLauncher<String[]> pickSpreadsheet =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null && viewModel != null) viewModel.readSpreadsheet(uri);
            });

    /** Android 8 e 9: o usuário escolhe onde salvar o template (Downloads vem sugerido). */
    private final ActivityResultLauncher<String> createTemplate =
            registerForActivityResult(new ActivityResultContracts.CreateDocument(PlanFiles.XLSX_MIME), uri -> {
                if (uri == null) return;
                Context app = requireContext().getApplicationContext();
                files.execute(() -> {
                    boolean ok;
                    try {
                        PlanFiles.copyTemplate(app, uri);
                        ok = true;
                    } catch (IOException | SecurityException e) {
                        ok = false;
                    }
                    boolean saved = ok;
                    main.post(() -> onTemplateSaved(saved ? uri : null));
                });
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_diet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(DietViewModel.class);

        source = view.findViewById(R.id.plan_source);
        importCard = view.findViewById(R.id.import_card);
        importText = view.findViewById(R.id.import_text);
        importProgress = view.findViewById(R.id.import_progress);
        planError = view.findViewById(R.id.plan_error);
        groupsTitle = view.findViewById(R.id.groups_title);
        actions = view.findViewById(R.id.chips_actions);
        groups = view.findViewById(R.id.chips_groups);
        mealsEmpty = view.findViewById(R.id.meals_empty);
        mealsTitle = view.findViewById(R.id.meals_title);
        view.findViewById(R.id.import_button).setOnClickListener(v -> pickFile());
        view.findViewById(R.id.template_button).setOnClickListener(v -> downloadTemplate());

        RecyclerView list = view.findViewById(R.id.list);
        MealAdapter adapter = new MealAdapter(new MealAdapter.Listener() {
            @Override
            public void onEdit(MealWithItems meal) {
                MealDialog.show(DietFragment.this, meal, viewModel::save);
            }

            @Override
            public void onDelete(MealWithItems meal) {
                UiUtils.confirmDelete(requireContext(), meal.meal.name, () -> viewModel.delete(meal.meal));
            }

            @Override
            public void onDoneChanged(MealWithItems meal, boolean done) {
                viewModel.setDone(meal.meal.id, done, done ? () -> {
                    if (isAdded()) ((MainActivity) requireActivity()).celebrateGoal();
                } : null);
            }

            @Override
            public void onGroupClick(MealItem item) {
                PlanSheets.showGroup(requireContext(), item.groupNumber, item.groupPortions, item.description);
            }
        });
        list.setAdapter(adapter);

        viewModel.meals.observe(getViewLifecycleOwner(), meals -> {
            mealCount = meals.size();
            adapter.setMeals(meals);
            renderPlan(NutritionPlan.get(requireContext()));
        });
        viewModel.logsToday.observe(getViewLifecycleOwner(), logs -> {
            Set<Long> ids = new HashSet<>();
            for (MealLog l : logs) ids.add(l.mealId);
            adapter.setDoneIds(ids);
        });
        viewModel.importing().observe(getViewLifecycleOwner(), busy ->
                importProgress.setVisibility(Boolean.TRUE.equals(busy) ? View.VISIBLE : View.GONE));
        viewModel.importResult().observe(getViewLifecycleOwner(), result -> {
            if (result == null) return;
            viewModel.consumeImportResult();
            if (result.ok()) showPreview(result);
            else showErrors(result.errors);
        });

        view.findViewById(R.id.fab).setOnClickListener(v -> MealDialog.show(this, null, viewModel::save));

        NutritionPlan plan = NutritionPlan.get(requireContext());
        renderPlan(plan);

        // Atalho das Configurações ("Importar planilha da dieta")
        if (MainActivity.ACTION_IMPORT_PLAN.equals(((MainActivity) requireActivity()).consumeAction(MainActivity.TAB_DIET))) {
            pickFile();
        } else if (savedInstanceState == null && !plan.isImported()) {
            askImportOnce();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        files.shutdown();
    }

    // ---------------- Estado do plano ----------------

    /** Mostra o card de importação, o erro ou os atalhos, conforme o plano do usuário. */
    private void renderPlan(NutritionPlan plan) {
        boolean imported = plan.isImported();

        source.setVisibility(imported ? View.VISIBLE : View.GONE);
        if (imported) {
            source.setText(getString(R.string.diet_source, plan.source, DateUtils.formatDate(plan.importedAt)));
        }

        importCard.setVisibility(imported ? View.GONE : View.VISIBLE);
        importText.setText(Honorific.str(requireContext(),
                mealCount > 0 ? R.string.diet_import_text_existing : R.string.diet_import_text));

        if (plan.failedToLoad()) {
            EmptyState.showError(planError, R.string.state_error_title, R.string.diet_plan_error,
                    R.string.diet_import_button, this::pickFile);
        } else {
            EmptyState.hide(planError);
        }

        actions.removeAllViews();
        actions.addView(chip(getString(R.string.diet_water), R.drawable.ic_water,
                v -> PlanSheets.showWater(requireContext())));
        if (imported && !plan.notes.isEmpty()) {
            actions.addView(chip(getString(R.string.diet_notes), R.drawable.ic_notes,
                    v -> PlanSheets.showNotes(requireContext())));
        }
        if (imported) {
            actions.addView(chip(getString(R.string.diet_import_again), R.drawable.ic_upload, v -> pickFile()));
        }
        actions.addView(chip(getString(R.string.diet_template_chip), R.drawable.ic_download, v -> downloadTemplate()));

        groups.removeAllViews();
        for (NutritionPlan.Group g : plan.groups) {
            groups.addView(chip(getString(R.string.group_chip_short, g.number, g.name), 0,
                    v -> PlanSheets.showGroup(requireContext(), g.number, "", null)));
        }
        groupsTitle.setVisibility(plan.groups.isEmpty() ? View.GONE : View.VISIBLE);

        mealsTitle.setVisibility(mealCount == 0 ? View.GONE : View.VISIBLE);
        if (mealCount == 0 && imported) {
            EmptyState.show(mealsEmpty, R.drawable.ic_diet, R.string.diet_empty_title, R.string.diet_empty,
                    R.string.diet_import_again, this::pickFile);
        } else {
            // Sem plano importado, o card de importação já explica o que fazer
            EmptyState.hide(mealsEmpty);
        }
    }

    private Chip chip(String text, int icon, View.OnClickListener onClick) {
        Chip chip = new Chip(requireContext());
        chip.setText(text);
        if (icon != 0) {
            chip.setChipIconResource(icon);
            chip.setChipIconSize(getResources().getDisplayMetrics().density * 18);
        }
        chip.setOnClickListener(onClick);
        return chip;
    }

    // ---------------- Importar ----------------

    /** No primeiro acesso à aba (de cada conta), o Alfred pede a planilha uma vez. */
    private void askImportOnce() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String key = "diet_import_asked_u" + AccountStore.currentUserId(requireContext());
        if (prefs.getBoolean(key, false)) return;
        prefs.edit().putBoolean(key, true).apply();
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.diet_import_title)
                .setMessage(Honorific.str(requireContext(), R.string.diet_import_ask))
                .setNeutralButton(R.string.diet_template_button, (d, w) -> downloadTemplate())
                .setNegativeButton(R.string.later, null)
                .setPositiveButton(R.string.diet_import_button, (d, w) -> pickFile())
                .show();
    }

    private void pickFile() {
        try {
            pickSpreadsheet.launch(PlanFiles.PICK_MIME_TYPES);
        } catch (ActivityNotFoundException e) {
            UiUtils.toast(requireContext(), getString(R.string.diet_no_file_picker));
        }
    }

    /** Problemas que impedem a importação, explicados um a um. */
    private void showErrors(List<String> errors) {
        StringBuilder sb = new StringBuilder(Honorific.str(requireContext(), R.string.diet_import_error_intro));
        for (String e : errors) sb.append("\n\n• ").append(e);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.diet_import_error_title)
                .setMessage(sb.toString())
                .setNeutralButton(R.string.diet_template_button, (d, w) -> downloadTemplate())
                .setPositiveButton(R.string.settings_ok, null)
                .show();
    }

    /**
     * Prévia do que foi lido, antes de salvar. Se a planilha ainda tem linhas iguais às de
     * exemplo do template, oferece importar sem elas (o padrão) ou com tudo.
     */
    private void showPreview(PlanImport.Result result) {
        NutritionPlan all = result.plan;
        if (all == null) return;
        boolean hasExamples = result.exampleRows > 0;
        NutritionPlan clean = result.planWithoutExamples;
        NutritionPlan shown = hasExamples && clean != null ? clean : all;

        Context ctx = requireContext();
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(24);
        box.setPadding(pad, dp(8), pad, 0);

        addLine(box, getString(R.string.preview_file, all.source), R.color.text_secondary, false);
        int foods = 0;
        for (NutritionPlan.PlanMeal m : shown.meals) foods += m.items.size();
        addLine(box, getString(R.string.range_summary, count(R.plurals.preview_meal_count, shown.meals.size()),
                count(R.plurals.preview_food_count, foods)), R.color.text_primary, true);
        for (NutritionPlan.PlanMeal m : shown.meals) {
            addLine(box, getString(R.string.preview_meal_line, DateUtils.formatTime(m.timeMinutes), m.name,
                    getResources().getQuantityString(R.plurals.preview_items, m.items.size(), m.items.size())),
                    R.color.text_secondary, false);
        }
        int groupFoods = 0;
        for (NutritionPlan.Group g : shown.groups) groupFoods += g.foods.size();
        addLine(box, getString(R.string.range_summary, count(R.plurals.preview_group_count, shown.groups.size()),
                count(R.plurals.preview_food_count, groupFoods)), R.color.text_primary, true);
        addLine(box, getResources().getQuantityString(R.plurals.preview_notes, shown.notes.size(), shown.notes.size()),
                R.color.text_primary, true);
        addLine(box, shown.waterMlPerKg > 0 ? getString(R.string.preview_water, shown.waterMlPerKg)
                : getString(R.string.preview_water_default), R.color.text_primary, true);

        if (hasExamples) {
            addLine(box, clean != null
                            ? getResources().getQuantityString(R.plurals.preview_examples, result.exampleRows, result.exampleRows)
                            : getString(R.string.preview_only_examples),
                    R.color.highlight, true);
        }
        for (String w : result.warnings) addLine(box, "• " + w, R.color.cat_diet, false);
        if (mealCount > 0) addLine(box, Honorific.str(ctx, R.string.preview_replace), R.color.text_secondary, false);

        ScrollView scroll = new ScrollView(ctx);
        scroll.addView(box);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.preview_title)
                .setView(scroll)
                .setNegativeButton(R.string.cancel, null);
        if (hasExamples && clean != null) {
            builder.setPositiveButton(R.string.preview_import_clean, (d, w) -> save(clean));
            builder.setNeutralButton(R.string.preview_import_all, (d, w) -> save(all));
        } else {
            builder.setPositiveButton(R.string.preview_import, (d, w) -> save(all));
        }
        builder.show();
    }

    private String count(int pluralRes, int n) {
        return getResources().getQuantityString(pluralRes, n, n);
    }

    private void addLine(LinearLayout box, String text, int colorRes, boolean strong) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(strong ? 14 : 13);
        tv.setTextColor(ContextCompat.getColor(requireContext(), colorRes));
        if (strong) tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        tv.setPadding(0, dp(strong ? 10 : 3), 0, 0);
        box.addView(tv);
    }

    private void save(NutritionPlan plan) {
        Context app = requireContext().getApplicationContext();
        viewModel.confirmImport(plan, () -> {
            if (!isAdded()) return;
            renderPlan(NutritionPlan.get(app));
            ((MainActivity) requireActivity()).showMessage(
                    Honorific.plural(requireContext(), R.plurals.diet_imported, plan.meals.size(), plan.meals.size()));
        }, () -> {
            if (isAdded()) UiUtils.toast(requireContext(), getString(R.string.diet_import_save_failed));
        });
    }

    // ---------------- Template ----------------

    /** Salva uma cópia do template na pasta Downloads do celular. */
    private void downloadTemplate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            try {
                createTemplate.launch(PlanFiles.TEMPLATE_NAME);
            } catch (ActivityNotFoundException e) {
                UiUtils.toast(requireContext(), getString(R.string.diet_no_file_picker));
            }
            return;
        }
        Context app = requireContext().getApplicationContext();
        files.execute(() -> {
            Uri uri;
            try {
                uri = PlanFiles.saveTemplateToDownloads(app);
            } catch (IOException | RuntimeException e) {
                uri = null;
            }
            Uri result = uri;
            main.post(() -> onTemplateSaved(result));
        });
    }

    private void onTemplateSaved(@Nullable Uri uri) {
        if (!isAdded()) return;
        if (uri == null) {
            UiUtils.toast(requireContext(), getString(R.string.diet_template_failed));
            return;
        }
        // Mensagem com atalho para abrir o arquivo (se houver um app de planilhas)
        Snackbar bar = Snackbar.make(requireView(), Honorific.str(requireContext(), R.string.diet_template_saved),
                Snackbar.LENGTH_LONG);
        bar.setAction(R.string.diet_template_open, v -> {
            Intent open = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, PlanFiles.XLSX_MIME)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivity(open);
            } catch (ActivityNotFoundException e) {
                UiUtils.toast(requireContext(), getString(R.string.diet_template_no_app));
            }
        });
        bar.setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.graphite_lighter));
        bar.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        bar.setActionTextColor(ContextCompat.getColor(requireContext(), R.color.highlight));
        bar.show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
