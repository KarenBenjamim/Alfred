package com.alfred.agenda.ui.settings;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.alfred.agenda.MainActivity;
import com.alfred.agenda.R;
import com.alfred.agenda.account.AccountStore;
import com.alfred.agenda.data.AlfredRepository;
import com.alfred.agenda.data.LocalBackup;
import com.alfred.agenda.data.NutritionPlan;
import com.alfred.agenda.notifications.AlarmScheduler;
import com.alfred.agenda.notifications.NotificationHelper;
import com.alfred.agenda.util.Honorific;
import com.alfred.agenda.util.ThemePrefs;
import com.alfred.agenda.util.UiUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.time.LocalDate;
import java.util.List;

/**
 * Configurações (abertas pelo Perfil). Só mostra o que existe de verdade no app:
 * permissões do sistema, aparência (escuro, claro ou sistema), dados, sobre e ajuda.
 * As contas são locais e não há sincronização — isso aparece como informação, sem botão falso.
 */
public class SettingsFragment extends Fragment {

    private LinearLayout prefs;

    /** "Abrir" do sistema: a usuária escolhe o arquivo de backup a importar. */
    private final ActivityResultLauncher<String[]> pickBackup =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null && isAdded()) runImport(uri);
            });

    /** Conta antiga cujo backup está sendo salvo (null = a conta logada). */
    @Nullable
    private AccountStore.LegacyAccount backupOfLegacy;

    /** "Salvar como" do sistema: a usuária escolhe onde guardar o backup (sem pedir permissão). */
    private final ActivityResultLauncher<String> saveBackup =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
                AccountStore.LegacyAccount legacy = backupOfLegacy;
                backupOfLegacy = null;
                if (uri == null || !isAdded()) return; // cancelou
                MainActivity activity = (MainActivity) requireActivity();
                Runnable ok = () -> activity.showMessage(Honorific.str(activity, R.string.backup_done));
                Runnable fail = () -> activity.showMessage(Honorific.str(activity, R.string.backup_failed));
                AlfredRepository repo = AlfredRepository.get(requireContext());
                if (legacy == null) repo.backup().exportBackup(uri, appVersion(), ok, fail);
                else repo.backup().exportLegacyBackup(uri, legacy.id, legacy.username, appVersion(), ok, fail);
            });

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
        addRow(data, R.drawable.ic_download, getString(R.string.settings_backup),
                getString(R.string.settings_backup_desc), this::confirmBackup);
        // Conta da nuvem: importar um arquivo de backup (ex.: de antes de reinstalar o app)
        if (AccountStore.currentCloudUid(requireContext()) != null) {
            addRow(data, R.drawable.ic_upload, getString(R.string.settings_import_backup),
                    getString(R.string.settings_import_backup_desc), this::confirmImport);
        }
        // Conta da nuvem com contas locais antigas neste celular: oferece trazer os dados (uma vez)
        if (AccountStore.currentCloudUid(requireContext()) != null
                && !AccountStore.pendingLegacyAccounts(requireContext()).isEmpty()) {
            addRow(data, R.drawable.ic_upload, getString(R.string.settings_migrate),
                    getString(R.string.settings_migrate_desc), this::chooseLegacyAccount);
        }
        addRow(data, R.drawable.ic_storage, getString(R.string.settings_purge),
                getString(R.string.settings_purge_value), null);
        addRow(data, R.drawable.ic_upload, getString(R.string.settings_import_plan),
                getString(R.string.settings_import_plan_desc),
                () -> activity.openTab(MainActivity.TAB_DIET, MainActivity.ACTION_IMPORT_PLAN));

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

    /** Avisa que o arquivo sai sem cifra e abre o "Salvar como". */
    private void confirmBackup() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_backup)
                .setMessage(Honorific.str(requireContext(), R.string.backup_warning))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.backup_choose, (d, w) -> saveBackup.launch(
                        LocalBackup.suggestedFileName(AccountStore.currentUsername(requireContext()),
                                LocalDate.now().toString())))
                .show();
    }

    // ---------------- Migração de conta antiga ----------------

    private void chooseLegacyAccount() {
        List<AccountStore.LegacyAccount> accounts = AccountStore.pendingLegacyAccounts(requireContext());
        if (accounts.isEmpty()) {
            ((MainActivity) requireActivity()).showMessage(Honorific.str(requireContext(), R.string.migration_none));
            return;
        }
        if (accounts.size() == 1) {
            confirmMigration(accounts.get(0));
            return;
        }
        CharSequence[] names = new CharSequence[accounts.size()];
        for (int i = 0; i < names.length; i++) names[i] = accounts.get(i).username;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.migration_choose)
                .setItems(names, (d, which) -> confirmMigration(accounts.get(which)))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Pede a senha da conta antiga (prova que é dela) e oferece o backup antes de enviar. */
    private void confirmMigration(AccountStore.LegacyAccount account) {
        UiUtils.TextField field = UiUtils.textField(requireContext(), R.string.migration_password);
        TextInputLayout layout = field.layout;
        EditText input = field.input;
        layout.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.migration_title, account.username))
                .setMessage(Honorific.str(requireContext(), R.string.migration_msg, account.username))
                .setView(field.view)
                .setNeutralButton(R.string.migration_backup_first, (d, w) -> {
                    backupOfLegacy = account;
                    saveBackup.launch(LocalBackup.suggestedFileName(account.username, LocalDate.now().toString()));
                })
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.migration_send, null) // validado abaixo
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            String password = input.getText() == null ? "" : input.getText().toString(); // sem trim: espaço conta
            if (password.isEmpty()) {
                layout.setError(getString(R.string.auth_error_required));
                return;
            }
            dialog.dismiss();
            runMigration(account, password);
        });
    }

    // ---------------- Importar arquivo de backup ----------------

    private void confirmImport() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_import_backup)
                .setMessage(Honorific.str(requireContext(), R.string.backup_import_msg))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.backup_import_choose, (d, w) ->
                        // Alguns gerenciadores de arquivo não marcam .json como JSON
                        pickBackup.launch(new String[]{"application/json", "application/octet-stream", "text/plain"}))
                .show();
    }

    private void runImport(Uri uri) {
        View progress = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_progress, null);
        AlertDialog wait = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.backup_import_progress)
                .setView(progress)
                .setCancelable(false)
                .show();
        MainActivity activity = (MainActivity) requireActivity();
        AlfredRepository.get(requireContext()).backup().importBackupFile(uri, (result, errorRes) -> {
            wait.dismiss();
            if (result != null) {
                activity.showMessage(Honorific.str(activity, R.string.migration_done, result.tasks,
                        result.meals, result.habits, result.subjects, result.waterDays));
            } else {
                activity.showMessage(Honorific.str(activity, errorRes));
            }
        });
    }

    private void runMigration(AccountStore.LegacyAccount account, String password) {
        View progress = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_progress, null);
        AlertDialog wait = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.migration_progress)
                .setView(progress)
                .setCancelable(false)
                .show();
        MainActivity activity = (MainActivity) requireActivity();
        AlfredRepository.get(requireContext()).backup().migrateLegacyAccount(account.id, password, appVersion(),
                (result, errorRes) -> {
                    wait.dismiss();
                    if (result != null) {
                        activity.showMessage(Honorific.str(activity, R.string.migration_done, result.tasks,
                                result.meals, result.habits, result.subjects, result.waterDays));
                    } else if (errorRes == R.string.auth_error_wrong) {
                        activity.showMessage(getString(R.string.migration_wrong_password));
                        if (isAdded()) confirmMigration(account);
                    } else {
                        activity.showMessage(Honorific.str(activity, errorRes));
                    }
                });
    }

    private String appVersion() {
        try {
            PackageInfo info = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0);
            return info.versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
            return ""; // sem versão, mostra o resto
        }
    }

    private void showAbout() {
        String version = appVersion();
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
            TypedValue tv = new TypedValue();
            requireContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            row.setBackgroundResource(tv.resourceId);
            row.setOnClickListener(v -> {
                try {
                    action.run();
                } catch (ActivityNotFoundException e) {
                    UiUtils.toast(requireContext(), getString(R.string.state_error_title));
                }
            });
        }
        group.addView(row);
    }
}
