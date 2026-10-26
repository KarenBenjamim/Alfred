package com.alfred.agenda.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.alfred.agenda.account.AccountStore;
import com.alfred.agenda.data.AlfredRepository;

/** Recebe o toque no botão "Pausar" da notificação do cronômetro de estudo. */
public class StudyTimerReceiver extends BroadcastReceiver {

    public static final String ACTION_STOP = "com.alfred.agenda.STUDY_STOP";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_STOP.equals(intent.getAction())) return;
        if (!AccountStore.isLoggedIn(context)) return;
        final Context app = context.getApplicationContext();
        final PendingResult result = goAsync();
        new Thread(() -> {
            try {
                AlfredRepository.get(app).stopAllStudyTimersSync();
            } finally {
                result.finish();
            }
        }).start();
    }
}
