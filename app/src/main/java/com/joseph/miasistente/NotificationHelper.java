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
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class NotificationHelper {
    private static final String CHANNEL_ID = "assistant_reminders";

    private NotificationHelper() {}

    public static int notificationId(long id) {
        return 10_000 + (int) (id % 20_000);
    }

    public static void ensureChannel(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Agenda y recordatorios",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Avisos de Lyra");
        channel.enableVibration(true);
        manager.createNotificationChannel(channel);
    }

    public static void show(Context context, ReminderItem item) {
        if (item == null || item.completed || "Nota".equals(item.kind)) return;
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

        PendingIntent done = actionPending(context, item.id, NotificationActionReceiver.ACTION_DONE, 1);
        PendingIntent snooze10 = actionPending(context, item.id, NotificationActionReceiver.ACTION_SNOOZE_10, 2);
        PendingIntent snooze60 = actionPending(context, item.id, NotificationActionReceiver.ACTION_SNOOZE_60, 3);

        Intent editIntent = new Intent(context, EditorActivity.class);
        editIntent.putExtra("reminder_id", item.id);
        editIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent edit = PendingIntent.getActivity(
                context,
                30_000 + (int) (item.id & 0x7FFF),
                editIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String when = item.isScheduled() ? friendlyWhen(item.eventTime) : "Pendiente";
        String notes = item.notes == null ? "" : item.notes.trim();
        String body = notes.isEmpty() ? when : when + " · " + notes;

        Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(item.title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setSubText(item.kind)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .addAction(new Notification.Action.Builder(R.drawable.ic_done, "Hecho", done).build())
                .addAction(new Notification.Action.Builder(R.drawable.ic_snooze, "10 min", snooze10).build())
                .addAction(new Notification.Action.Builder(R.drawable.ic_snooze, "1 hora", snooze60).build())
                .addAction(new Notification.Action.Builder(R.drawable.ic_edit, "Reprogramar", edit).build());

        manager.notify(notificationId(item.id), builder.build());
    }

    private static PendingIntent actionPending(Context context, long id, String action, int offset) {
        Intent intent = new Intent(context, NotificationActionReceiver.class).setAction(action);
        intent.putExtra("reminder_id", id);
        return PendingIntent.getBroadcast(
                context,
                40_000 + (int) ((id * 10 + offset) & 0x7FFFFFFF),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static String friendlyWhen(long millis) {
        Calendar event = Calendar.getInstance();
        event.setTimeInMillis(millis);
        Calendar today = Calendar.getInstance();
        String time = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(millis));
        if (sameDay(event, today)) return "Hoy · " + time;
        Calendar tomorrow = (Calendar) today.clone();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        if (sameDay(event, tomorrow)) return "Mañana · " + time;
        return new SimpleDateFormat("d MMM · h:mm a", new Locale("es", "ES")).format(new Date(millis));
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }
}
