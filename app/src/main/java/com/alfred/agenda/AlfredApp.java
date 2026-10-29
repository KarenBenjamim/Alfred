package com.alfred.agenda;

import android.app.Application;

import com.alfred.agenda.notifications.NotificationHelper;
import com.alfred.agenda.util.AppLog;
import com.alfred.agenda.util.ThemePrefs;

/** Executado uma vez quando o processo do app inicia. */
public class AlfredApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppLog.init(this);
        // Aparência escolhida (escuro, claro ou sistema) antes da primeira tela
        ThemePrefs.apply(this);
        // Os canais precisam existir antes da primeira notificação
        NotificationHelper.createChannels(this);
    }
}
