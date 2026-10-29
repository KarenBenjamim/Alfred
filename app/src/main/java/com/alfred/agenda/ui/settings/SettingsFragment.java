package com.alfred.agenda.ui.settings;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.alfred.agenda.MainActivity;
import com.alfred.agenda.R;
import com.alfred.agenda.util.Honorific;
import com.alfred.agenda.data.AlfredRepository;
import com.alfred.agenda.data.NutritionPlan;
import com.alfred.agenda.notifications.AlarmScheduler;
import com.alfred.agenda.notifications.NotificationHelper;
import com.alfred.agenda.util.ThemePrefs;
import com.alfred.agenda.util.UiUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Configurações (abertas pelo Perfil). Só mostra o que existe de verdade no app:
 * permissões do sistema, aparência (escuro, claro ou sistema), dados, sobre e ajuda.
 * As contas são locais e não há sincronização — isso aparece como informação, sem botão falso.
 */
public class SettingsFragment extends Fragment {

    private LinearLayout prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        prefs = view.findViewById(R.id.group_prefs);
        LinearLayout data = view.findViewById(R.id.group_data);
        LinearLayout about = view.findViewById(R.id.group_about);
        MainActivity activity = (MainActivity) requireActivity();
        AlfredRepository repo = AlfredRepository.get(requireContext());

        // Dados
        addRow(data, R.drawable.ic_storage, getString(R.string.settings_purge),
                getString(R.string.settings_purge_value), null);
        addRow(data, R.drawable.ic_upload, getString(R.string.settings_import_plan),
                getString(R.string.settings_import_plan_desc),
                () -> activity.openTab(MainActivity.TAB_DIET, MainActivity.ACTION_IMPORT_PLAN));
        if (activity.isDebugBuild()) {
            addRow(data, R.drawable.ic_task, getString(R.string.settings_test_create), null,
                    () -> repo.insertTestTasks(() -> {
                        if (isAdded()) activity.showMessage(Honorific.str(requireContext(), R.string.test_created));
                    }));
            addRow(data, R.drawable.ic_delete, getString(R.string.settings_test_remove), null,
                    () -> repo.removeTestTasks(count -> {
                        if (isAdded()) activity.showMessage(
                                getResources().getQuantityString(R.plurals.test_removed, count, count));
                    }));
        }

        // Sobre
        addRow(about, R.drawable.ic_info, getString(R.string.settings_about), null, this::showAbout);
        addRow(about, R.drawable.ic_help, getString(R.string.settings_help), null, () ->
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.settings_help)
                        .setMessage(Honorific.str(requireContext(), R.string.settings_help_msg))
                        .setPositiveButton(R.string.settings_ok, null)
                        .show());
    }

    @Override
    public void onResume() {
        super.onResume();
        // As permissões podem ter mudado nas configurações do sistema
        renderPrefs();
    }

    private void renderPrefs() {
        prefs.removeAllViews();
        boolean notifications = NotificationHelper.hasPermission(requireContext());
        addRow(prefs, R.drawable.ic_bell, getString(R.string.settings_notifications),
                getString(notifications ? R.string.settings_on : R.string.settings_off), () ->
                        startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName())));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean exact = new AlarmScheduler(requireContext()).canScheduleExact();
            addRow(prefs, R.drawable.ic_alarm, getString(R.string.settings_exact_alarms),
                    getString(exact ? R.string.settings_allowed : R.string.settings_not_allowed), () ->
                            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse("package:" + requireContext().getPackageName()))));
        }

        addRow(prefs, R.drawable.ic_battery, getString(R.string.settings_battery),
                getString(R.string.settings_battery_desc), () ->
                        startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
        String[] themes = getResources().getStringArray(R.array.theme_options);
        addRow(prefs, R.drawable.ic_palette, getString(R.string.settings_theme),
                themes[ThemePrefs.get(requireContext())], this::chooseTheme);
        addRow(prefs, R.drawable.ic_sync, getString(R.string.settings_sync),
                getString(R.string.settings_sync_value), null);
    }

    /** Escuro, claro ou seguir o sistema; a tela é recriada com o tema escolhido. */
    private void chooseTheme() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_theme)
                .setSingleChoiceItems(R.array.theme_options, ThemePrefs.get(requireContext()), (d, which) -> {
                    d.dismiss();
                    ThemePrefs.set(requireContext(), which);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showAbout() {
        String version = "";
        try {
            PackageInfo info = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0);
            version = info.versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
            // sem versão, mostra o resto
        }
        String plan = NutritionPlan.get(requireContext()).source;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_about)
                .setMessage(getString(R.string.settings_about_msg, version, plan.isEmpty() ? "—" : plan))
                .setPositiveButton(R.string.settings_ok, null)
                .show();
    }

    /**
     * Adiciona uma linha ao grupo. Com action = null, a linha é só informativa:
     * sem seta e sem toque (nada de botão que não faz nada).
     */
    private void addRow(LinearLayout group, int icon, String title, @Nullable String value,
                        @Nullable Runnable action) {
        if (group.getChildCount() > 0) {
            View divider = new View(requireContext());
            divider.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.outline));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
            lp.setMarginStart(Math.round(64 * getResources().getDisplayMetrics().density));
            group.addView(divider, lp);
        }
        View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_settings_row, group, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView valueView = row.findViewById(R.id.value);
        valueView.setText(value);
        valueView.setVisibility(value == null ? View.GONE : View.VISIBLE);
        row.findViewById(R.id.chevron).setVisibility(action == null ? View.GONE : View.VISIBLE);
        if (action != null) {
            android.util.TypedValue tv = new android.util.TypedValue();
            requireContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            row.setBackgroundResource(tv.resourceId);
            row.setOnClickListener(v -> {
                try {
                    action.run();
                } catch (android.content.ActivityNotFoundException e) {
                    UiUtils.toast(requireContext(), getString(R.string.state_error_title));
                }
            });
        }
        group.addView(row);
    }
}
