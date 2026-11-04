package com.alfred.agenda.data;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.alfred.agenda.R;
import com.alfred.agenda.account.AccountStore;
import com.alfred.agenda.account.UserProfile;
import com.alfred.agenda.cloud.CloudSchema;
import com.alfred.agenda.data.entity.Habit;
import com.alfred.agenda.data.entity.WaterLog;
import com.alfred.agenda.util.AppLog;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;

/**
 * Backup e migração da conta logada: exportar o backup (.json), exportar o de uma conta local
 * antiga, trazer uma conta antiga para a nuvem e importar um arquivo de backup.
 * Os callbacks rodam na thread principal. Obtido por AlfredRepository.backup().
 */
public final class AccountBackup {

    private final Context app;
    private final AppDatabase db;
    /** Conta da nuvem com sincronização ativa (migrar e importar só valem nela). */
    private final boolean cloud;
    /** Fila de gravações do repositório (os exports esperam as gravações anteriores). */
    private final Executor io;
    private final Handler main = new Handler(Looper.getMainLooper());

    AccountBackup(Context app, AppDatabase db, boolean cloud, Executor io) {
        this.app = app;
        this.db = db;
        this.cloud = cloud;
        this.io = io;
    }

    // ===================== Backup local =====================

    /**
     * Grava o backup completo da conta (LocalBackup) no arquivo escolhido pela usuária.
     * onSuccess/onError rodam na thread principal.
     */
    public void exportBackup(Uri target, @Nullable String appVersion, Runnable onSuccess, Runnable onError) {
        io.execute(() -> {
            try (OutputStream out = app.getContentResolver().openOutputStream(target, "wt")) {
                if (out == null) throw new IOException("Destino indisponível");
                byte[] json = LocalBackup.build(app, db, appVersion).toString(2).getBytes(StandardCharsets.UTF_8);
                out.write(json);
            } catch (IOException | JSONException | RuntimeException e) {
                AppLog.e("Falha ao exportar o backup", e);
                main.post(onError);
                return;
            }
            main.post(onSuccess);
        });
    }

    /** Backup de uma conta local antiga deste aparelho (antes de migrá-la para a nuvem). */
    public void exportLegacyBackup(Uri target, int legacyId, String username, @Nullable String appVersion,
                                   Runnable onSuccess, Runnable onError) {
        io.execute(() -> {
            AppDatabase legacyDb = AppDatabase.openForAccount(app, legacyId);
            try (OutputStream out = app.getContentResolver().openOutputStream(target, "wt")) {
                if (out == null) throw new IOException("Destino indisponível");
                JSONObject backup = LocalBackup.build(legacyDb, legacyId, username, UserProfile.forAccount(app, legacyId),
                        NutritionPlan.forAccount(app, legacyId), appVersion);
                out.write(backup.toString(2).getBytes(StandardCharsets.UTF_8));
            } catch (IOException | JSONException | RuntimeException e) {
                AppLog.e("Falha ao exportar o backup da conta antiga", e);
                main.post(onError);
                return;
            } finally {
                legacyDb.close();
            }
            main.post(onSuccess);
        });
    }

    // ===================== Migração de conta antiga para a nuvem =====================

    /** Resultado da migração: result != null deu certo; senão errorRes diz o motivo. */
    public interface MigrationCallback {
        void done(@Nullable LegacyMigration.Result result, int errorRes);
    }

    /**
     * Confere a senha da conta local antiga e envia os dados dela para a conta da nuvem logada.
     * Roda numa thread própria: espera a confirmação do servidor (até 1 min por lote) sem
     * travar a fila de gravações nem a sincronização.
     */
    public void migrateLegacyAccount(int legacyId, String password, @Nullable String appVersion,
                                     MigrationCallback cb) {
        String uid = AccountStore.currentCloudUid(app);
        if (uid == null || !cloud) {
            cb.done(null, R.string.migration_error_not_cloud);
            return;
        }
        new Thread(() -> {
            LegacyMigration.Result result = null;
            int error = 0;
            try {
                if (!AccountStore.checkLegacyPassword(app, legacyId, password)) {
                    error = R.string.auth_error_wrong;
                } else {
                    result = LegacyMigration.run(app, legacyId, uid, existingInCloud(), appVersion);
                }
            } catch (TimeoutException e) {
                error = R.string.migration_error_timeout;
            } catch (Exception e) {
                AppLog.e("Falha na migração", e);
                error = R.string.migration_error;
            }
            LegacyMigration.Result r = result;
            int err = error;
            main.post(() -> cb.done(r, err));
        }, "alfred-migracao").start();
    }

    /** O que a conta da nuvem já tem, lido do espelho local (a migração soma em vez de sobrescrever). */
    private LegacyMigration.Existing existingInCloud() {
        LegacyMigration.Existing e = new LegacyMigration.Existing();
        for (Habit h : db.habitDao().getAllSync()) e.habits.put(LegacyMigration.normalize(h.name), h.id);
        for (WaterLog w : db.syncDao().allWater()) e.waterByDay.put(CloudSchema.day(w.epochDay), w.ml);
        e.profile = UserProfile.current(app).onboarded;
        e.plan = NutritionPlan.get(app).isImported();
        return e;
    }

    /** Arquivos de backup maiores que isto são recusados (protege a memória do celular). */
    private static final long MAX_BACKUP_BYTES = 20L * 1024 * 1024;

    /**
     * Importa um arquivo de backup (.json exportado pelo app) para a conta da nuvem logada.
     * Roda numa thread própria e espera a confirmação do servidor, como a migração.
     */
    public void importBackupFile(Uri source, MigrationCallback cb) {
        String uid = AccountStore.currentCloudUid(app);
        if (uid == null || !cloud) {
            cb.done(null, R.string.migration_error_not_cloud);
            return;
        }
        new Thread(() -> {
            LegacyMigration.Result result = null;
            int error = 0;
            try {
                JSONObject backup = new JSONObject(readText(source));
                LegacyMigration.validate(backup);
                result = LegacyMigration.importBackup(backup, uid, existingInCloud());
            } catch (LegacyMigration.InvalidBackup | JSONException | IOException e) {
                AppLog.e("Backup inválido", e);
                error = R.string.backup_import_invalid;
            } catch (TimeoutException e) {
                error = R.string.migration_error_timeout;
            } catch (Exception e) {
                AppLog.e("Falha ao importar o backup", e);
                error = R.string.migration_error;
            }
            LegacyMigration.Result r = result;
            int err = error;
            main.post(() -> cb.done(r, err));
        }, "alfred-importar-backup").start();
    }

    /** Lê o arquivo escolhido como texto UTF-8, com limite de tamanho. */
    private String readText(Uri source) throws IOException {
        try (InputStream in = app.getContentResolver().openInputStream(source)) {
            if (in == null) throw new IOException("Arquivo indisponível");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            long total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_BACKUP_BYTES) throw new IOException("Arquivo grande demais");
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
