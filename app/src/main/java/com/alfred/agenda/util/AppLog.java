package com.alfred.agenda.util;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;

/**
 * Log de erros do app. Só escreve na versão de desenvolvimento (debuggable): na versão
 * final (release) nada vai para o logcat, nem a pilha de chamadas.
 */
public final class AppLog {

    private static final String TAG = "Alfred";
    private static volatile boolean enabled;

    private AppLog() {
    }

    /** Chamado ao iniciar o app (AlfredApp). */
    public static void init(Context ctx) {
        enabled = (ctx.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    public static void e(String message, Throwable error) {
        if (enabled) Log.e(TAG, message, error);
    }
}
