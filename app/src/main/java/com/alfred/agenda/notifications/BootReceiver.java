package com.alfred.agenda.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.alfred.agenda.account.AccountStore;
import com.alfred.agenda.data.AlfredRepository;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * O Android apaga todos os alarmes ao desligar o celular. Este receiver é chamado
 * depois que o aparelho liga (e também quando o app é atualizado, a hora/fuso muda
 * ou a permissão de alarme exato é concedida) e reagenda todos os lembretes.
 */
public class BootReceiver extends BroadcastReceiver {

    /** Só reagimos aos avisos enviados pelo sistema (ignora intents forjadas por outros apps). */
    private static final Set<String> ACTIONS = new HashSet<>(Arrays.asList(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"));

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTIONS.contains(intent.getAction())) return;
        // Os lembretes são de quem está logado; sem sessão, nada a reagendar
        if (!AccountStore.isLoggedIn(context)) return;
        final Context app = context.getApplicationContext();
        final PendingResult result = goAsync();
        new Thread(() -> {
            try {
                AlfredRepository.get(app).rescheduleAllSync();
                AlfredRepository.get(app).refreshStudyNotification();
            } finally {
                result.finish();
            }
        }).start();
    }
}
