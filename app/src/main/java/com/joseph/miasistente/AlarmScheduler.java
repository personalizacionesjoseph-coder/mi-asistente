package com.joseph.miasistente;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class AlarmScheduler {
    private AlarmScheduler() {}

    public static void schedule(Context context, ReminderItem item) {
        cancel(context, item.id);
        if (item.remindMinutes < 0) return;

        long triggerAt = item.eventTime - (item.remindMinutes * 60_000L);
        if (triggerAt <= System.currentTimeMillis()) return;

        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;

        PendingIntent pendingIntent = pendingIntent(context, item.id);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            } else {
                manager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            }
        } catch (SecurityException ignored) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            } else {
                manager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            }
        }
    }

    public static void cancel(Context context, long id) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) manager.cancel(pendingIntent(context, id));
    }

    public static void rescheduleAll(Context context) {
        EventDatabase db = new EventDatabase(context.getApplicationContext());
        for (ReminderItem item : db.futureWithAlerts(System.currentTimeMillis())) {
            schedule(context, item);
        }
        db.close();
    }

    private static PendingIntent pendingIntent(Context context, long id) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra("reminder_id", id);
        return PendingIntent.getBroadcast(
                context,
                (int) (id & 0x7FFFFFFF),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
