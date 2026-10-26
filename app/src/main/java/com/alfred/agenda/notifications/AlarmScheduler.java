package com.alfred.agenda.notifications;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.alfred.agenda.data.entity.Meal;
import com.alfred.agenda.data.entity.Task;
import com.alfred.agenda.util.DateUtils;

import java.time.LocalDate;

/**
 * Agenda os lembretes no AlarmManager.
 *
 * Cada alarme dispara uma única vez; quando ele toca, o AlarmReceiver mostra a
 * notificação e chama este agendador de novo para marcar a próxima ocorrência
 * (amanhã, no caso das refeições).
 */
public class AlarmScheduler {

    // Os tipos 2 (treino) e 3 (datas) foram removidos. Não reutilize os números para não confundir alarmes antigos.
    public static final int TYPE_MEAL = 1;
    public static final int TYPE_TASK = 4;

    public static final String EXTRA_TYPE = "alarm_type";
    public static final String EXTRA_ID = "alarm_id";

    private final Context context;
    private final AlarmManager alarmManager;

    public AlarmScheduler(Context context) {
        this.context = context.getApplicationContext();
        this.alarmManager = this.context.getSystemService(AlarmManager.class);
    }

    // ---------------- Agendamentos por tipo ----------------

    public void scheduleMeal(Meal meal) {
        if (!meal.notifyEnabled) {
            cancel(TYPE_MEAL, meal.id);
            return;
        }
        set(TYPE_MEAL, meal.id, nextDaily(meal.timeMinutes));
    }

    /** Lembra no dia da entrega, no início do período da tarefa (ver Task.PERIOD_REMINDER_MINUTES). */
    public void scheduleTask(Task task) {
        long trigger = DateUtils.millisAt(LocalDate.ofEpochDay(task.dueEpochDay),
                Task.PERIOD_REMINDER_MINUTES[task.period]);
        if (task.isDone() || trigger <= System.currentTimeMillis()) {
            cancel(TYPE_TASK, task.id);
            return;
        }
        set(TYPE_TASK, task.id, trigger);
    }

    public void cancel(int type, long id) {
        alarmManager.cancel(pendingIntent(type, id));
    }

    // ---------------- Cálculo do próximo horário ----------------

    /** Hoje no horário indicado, ou amanhã se esse horário já passou. */
    private static long nextDaily(int minutes) {
        LocalDate today = LocalDate.now();
        long trigger = DateUtils.millisAt(today, minutes);
        if (trigger <= System.currentTimeMillis() + 1000) {
            trigger = DateUtils.millisAt(today.plusDays(1), minutes);
        }
        return trigger;
    }

    // ---------------- AlarmManager ----------------

    private void set(int type, long id, long triggerAtMillis) {
        PendingIntent pi = pendingIntent(type, id);
        try {
            if (canScheduleExact()) {
                // Exato e funciona mesmo com o celular em modo de economia (Doze)
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            } else {
                // Sem permissão de alarme exato: o Android pode atrasar alguns minutos
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            }
        } catch (SecurityException e) {
            // A permissão pode ter sido revogada entre a checagem e o agendamento
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
        }
    }

    /** No Android 12+ o usuário precisa permitir "Alarmes e lembretes" para o app. */
    public boolean canScheduleExact() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms();
    }

    /**
     * Cada lembrete tem um PendingIntent próprio, identificado pelo tipo + id.
     * Usar o mesmo identificador ao reagendar substitui o alarme anterior.
     */
    private PendingIntent pendingIntent(int type, long id) {
        Intent intent = new Intent(context, AlarmReceiver.class)
                .setAction("com.alfred.agenda.ALARM_" + type + "_" + id)
                .putExtra(EXTRA_TYPE, type)
                .putExtra(EXTRA_ID, id);
        return PendingIntent.getBroadcast(context, requestCode(type, id), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Identificador numérico único por lembrete (também usado como id da notificação). */
    public static int requestCode(int type, long id) {
        return type * 1_000_000 + (int) (id % 1_000_000);
    }
}
