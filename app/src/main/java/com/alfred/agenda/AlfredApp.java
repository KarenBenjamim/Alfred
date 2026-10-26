package com.alfred.agenda;

import android.app.Application;

import com.alfred.agenda.notifications.NotificationHelper;

/** Executado uma vez quando o processo do app inicia. */
public class AlfredApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Os canais precisam existir antes da primeira notificação
        NotificationHelper.createChannels(this);
    }
}
