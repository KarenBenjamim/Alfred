package com.alfred.agenda.cloud;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import androidx.annotation.Nullable;

import com.alfred.agenda.util.AppLog;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.concurrent.TimeUnit;

/**
 * Decide a qual Firebase o app se conecta:
 *  - com app/google-services.json (baixado do console): o projeto real, inicializado
 *    automaticamente pelo plugin google-services;
 *  - versão de desenvolvimento SEM o arquivo: os emuladores locais do Firebase
 *    (Local Emulator Suite, `firebase emulators:start`) com o projeto de demonstração
 *    "demo-alfred", que não existe na nuvem e não custa nada;
 *  - versão final sem o arquivo: nenhum (isAvailable() = false) e o app segue só local.
 *
 * Nenhuma chave real fica no código: o projeto de demonstração usa valores fictícios,
 * aceitos apenas pelos emuladores.
 */
public final class CloudConfig {

    /** Projetos "demo-*" só existem nos emuladores. */
    static final String DEMO_PROJECT = "demo-alfred";
    /** O PC visto de dentro do emulador Android. */
    static final String EMULATOR_HOST = "10.0.2.2";
    static final int AUTH_PORT = 9099;
    static final int FIRESTORE_PORT = 8080;

    private static volatile boolean initialized;
    private static volatile boolean usingEmulator;

    private CloudConfig() {
    }

    /** Chamado ao iniciar o app (AlfredApp). Nunca lança: sem Firebase, o app segue local. */
    public static synchronized void init(Context ctx) {
        if (initialized) return;
        initialized = true;
        try {
            if (!FirebaseApp.getApps(ctx).isEmpty()) return; // google-services.json presente
            if (!isDebuggable(ctx)) return;
            FirebaseApp.initializeApp(ctx, new FirebaseOptions.Builder()
                    .setProjectId(DEMO_PROJECT)
                    .setApplicationId("1:000000000000:android:0000000000000000")
                    .setApiKey("demo-key-somente-emulador")
                    .build());
            FirebaseAuth.getInstance().useEmulator(EMULATOR_HOST, AUTH_PORT);
            FirebaseFirestore.getInstance().useEmulator(EMULATOR_HOST, FIRESTORE_PORT);
            usingEmulator = true;
        } catch (RuntimeException e) {
            AppLog.e("Firebase indisponível; o app segue só com os dados locais", e);
        }
    }

    /**
     * Ao sair da conta: apaga a cópia offline do Firestore neste aparelho, para não deixar os
     * dados da conta anterior no disco. Espera até 5 s as gravações pendentes subirem; se não
     * subirem (sem internet), mantém o cache para não perder nada. done roda na thread principal.
     */
    public static void clearOfflineCache(Context ctx, Runnable done) {
        if (!isAvailable(ctx)) {
            done.run();
            return;
        }
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Tasks.withTimeout(db.waitForPendingWrites(), 5, TimeUnit.SECONDS)
                .continueWithTask(sent -> {
                    if (!sent.isSuccessful()) return Tasks.forResult(false);
                    return db.terminate().continueWithTask(t -> db.clearPersistence())
                            .continueWith(t -> true);
                })
                .addOnCompleteListener(t -> {
                    // Instância nova depois do terminate: volta a apontar para o emulador
                    if (usingEmulator && Boolean.TRUE.equals(t.getResult())) {
                        FirebaseFirestore.getInstance().useEmulator(EMULATOR_HOST, FIRESTORE_PORT);
                    }
                    done.run();
                });
    }

    /** Há um Firebase configurado (real ou emulador)? */
    public static boolean isAvailable(Context ctx) {
        return !FirebaseApp.getApps(ctx).isEmpty();
    }

    /** Conectado aos emuladores locais (só na versão de desenvolvimento). */
    public static boolean isUsingEmulator() {
        return usingEmulator;
    }

    @Nullable
    public static FirebaseAuth auth(Context ctx) {
        return isAvailable(ctx) ? FirebaseAuth.getInstance() : null;
    }

    private static boolean isDebuggable(Context ctx) {
        return (ctx.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }
}
