package com.alfred.agenda.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.alfred.agenda.MainActivity;
import com.alfred.agenda.account.AccountStore;
import com.alfred.agenda.data.AppDatabase;
import com.alfred.agenda.data.entity.Meal;
import com.alfred.agenda.data.entity.Task;
import com.alfred.agenda.util.AlfredPhrases;
import com.alfred.agenda.util.DateUtils;

/**
 * Chamado pelo AlarmManager na hora de um lembrete.
 * Busca o item no banco, mostra a notificação e agenda a próxima ocorrência.
 */
public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        final int type = intent.getIntExtra(AlarmScheduler.EXTRA_TYPE, 0);
        final long id = intent.getLongExtra(AlarmScheduler.EXTRA_ID, 0);
        final Context app = context.getApplicationContext();

        // goAsync() permite acessar o banco fora da thread principal
        // sem que o Android encerre o receiver antes da hora.
        final PendingResult result = goAsync();
        new Thread(() -> {
            try {
                handle(app, type, id);
            } finally {
                result.finish();
            }
        }).start();
    }

    private void handle(Context context, int type, long id) {
        // Sem ninguém logado não há banco para consultar (os alarmes são cancelados ao sair)
        if (!AccountStore.isLoggedIn(context)) return;
        AppDatabase db = AppDatabase.get(context);
        AlarmScheduler scheduler = new AlarmScheduler(context);
        int notificationId = AlarmScheduler.requestCode(type, id);
        long today = DateUtils.today();

        switch (type) {
            case AlarmScheduler.TYPE_MEAL: {
                Meal meal = db.mealDao().getById(id);
                if (meal == null) return; // foi excluída
                // Se já marcou como feita, o Alfred tem a elegância de não insistir
                if (meal.notifyEnabled && !db.mealDao().isDone(id, today)) {
                    NotificationHelper.show(context, NotificationHelper.CHANNEL_DIET, notificationId,
                            AlfredPhrases.mealTitle(meal.name), AlfredPhrases.mealReminder(context, meal.name),
                            MainActivity.TAB_DIET);
                }
                scheduler.scheduleMeal(meal); // amanhã, mesmo horário
                break;
            }
            case AlarmScheduler.TYPE_TASK: {
                Task task = db.taskDao().getById(id);
                if (task == null || task.isDone()) return;
                if (task.isPersonal()) {
                    NotificationHelper.show(context, NotificationHelper.CHANNEL_PERSONAL, notificationId,
                            AlfredPhrases.personalTaskTitle(task.title),
                            AlfredPhrases.personalTaskReminder(context, task.title), MainActivity.TAB_PERSONAL);
                } else {
                    NotificationHelper.show(context, NotificationHelper.CHANNEL_WORK, notificationId,
                            AlfredPhrases.workTaskTitle(task.title),
                            AlfredPhrases.workTaskReminder(context, task.title), MainActivity.TAB_WORK);
                }
                break;
            }
            default:
                // Tipos que não existem mais (ex.: 2 = treino, 3 = datas) são ignorados
                break;
        }
    }
}
