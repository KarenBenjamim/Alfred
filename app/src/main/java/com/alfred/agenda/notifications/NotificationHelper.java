package com.alfred.agenda.notifications;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.alfred.agenda.MainActivity;
import com.alfred.agenda.R;
import com.alfred.agenda.util.Honorific;

/** Cria os canais e exibe as notificações do Alfred. */
public final class NotificationHelper {

    public static final String CHANNEL_DIET = "dieta";
    public static final String CHANNEL_WORK = "trabalho";
    public static final String CHANNEL_PERSONAL = "pessoal";
    public static final String CHANNEL_STUDY = "estudos";

    /** Id fixo da notificação do cronômetro (fora da faixa usada pelos alarmes). */
    private static final int STUDY_NOTIFICATION_ID = 900_000_001;

    private NotificationHelper() {
    }

    /**
     * Canais aparecem em Configurações > Apps > Alfred > Notificações,
     * permitindo silenciar, por exemplo, só as refeições.
     */
    public static void createChannels(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL_DIET,
                context.getString(R.string.channel_diet), NotificationManager.IMPORTANCE_HIGH));
        nm.deleteNotificationChannel("treino"); // canal da antiga aba Treino
        nm.deleteNotificationChannel("datas"); // canal da antiga aba Datas
        nm.createNotificationChannel(new NotificationChannel(CHANNEL_WORK,
                context.getString(R.string.channel_work), NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(new NotificationChannel(CHANNEL_PERSONAL,
                context.getString(R.string.channel_personal), NotificationManager.IMPORTANCE_DEFAULT));
        // Cronômetro: importância baixa = sem som nem vibração, só fica na barra
        nm.createNotificationChannel(new NotificationChannel(CHANNEL_STUDY,
                context.getString(R.string.channel_study), NotificationManager.IMPORTANCE_LOW));
    }

    /**
     * Notificação fixa enquanto um cronômetro de estudo está rodando. O próprio Android
     * mostra o tempo correndo (setUsesChronometer) a partir de "since".
     */
    @SuppressLint("MissingPermission") // a permissão é verificada em hasPermission()
    public static void showStudyTimer(Context context, String subjectName, long since) {
        if (!hasPermission(context)) return;

        Intent open = new Intent(context, MainActivity.class)
                .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_STUDY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, STUDY_NOTIFICATION_ID, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stop = new Intent(context, StudyTimerReceiver.class).setAction(StudyTimerReceiver.ACTION_STOP);
        PendingIntent stopIntent = PendingIntent.getBroadcast(context, STUDY_NOTIFICATION_ID, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_STUDY)
                .setSmallIcon(R.drawable.ic_stat_alfred)
                .setColor(ContextCompat.getColor(context, R.color.highlight))
                .setContentTitle(context.getString(R.string.study_notification_title, subjectName))
                .setContentText(Honorific.str(context, R.string.study_notification_text))
                .setWhen(since)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
                .setContentIntent(contentIntent)
                .addAction(0, context.getString(R.string.study_pause), stopIntent)
                // Tela bloqueada: sem o nome do assunto
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion(context, CHANNEL_STUDY,
                        context.getString(R.string.study_notification_public)));

        NotificationManagerCompat.from(context).notify(STUDY_NOTIFICATION_ID, builder.build());
    }

    public static void cancelStudyTimer(Context context) {
        NotificationManagerCompat.from(context).cancel(STUDY_NOTIFICATION_ID);
    }

    public static boolean hasPermission(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Mostra uma notificação. Ao tocar nela, o app abre direto na aba indicada.
     */
    @SuppressLint("MissingPermission") // a permissão é verificada em hasPermission()
    public static void show(Context context, String channel, int notificationId,
                            String title, String text, int tab) {
        if (!hasPermission(context)) return;

        Intent open = new Intent(context, MainActivity.class)
                .putExtra(MainActivity.EXTRA_TAB, tab)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, notificationId, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_stat_alfred)
                .setColor(ContextCompat.getColor(context, R.color.highlight))
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                // Tela bloqueada: sem título da tarefa nem nome da refeição
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion(context, channel,
                        context.getString(R.string.notification_public_text)));

        NotificationManagerCompat.from(context).notify(notificationId, builder.build());
    }

    /** Versão genérica mostrada com a tela bloqueada (nenhum dado pessoal). */
    private static Notification publicVersion(Context context, String channel, String text) {
        return new NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_stat_alfred)
                .setColor(ContextCompat.getColor(context, R.color.highlight))
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(text)
                .build();
    }
}
