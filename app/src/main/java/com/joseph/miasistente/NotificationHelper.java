package com.joseph.miasistente;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import java.text.DateFormat;

public final class NotificationHelper {
    private static final String CHANNEL_ID = "assistant_reminders";

    private NotificationHelper() {}

    public static void ensureChannel(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Citas y recordatorios",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Avisos de Lyra");
        channel.enableVibration(true);
        manager.createNotificationChannel(channel);
    }

    public static void show(Context context, ReminderItem item) {
        if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        ensureChannel(context);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                (int) (item.id & 0x7FFFFFFF),
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String when = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(item.eventTime);
        String body = item.notes == null || item.notes.trim().isEmpty()
                ? "Programado para " + when
                : item.notes.trim();

        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(item.kind + ": " + item.title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body + "\n" + when))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .build();

        manager.notify(10_000 + (int) (item.id % 20_000), notification);
    }
}
