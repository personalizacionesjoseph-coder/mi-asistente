package com.joseph.miasistente;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class AlarmScheduler {
    private AlarmScheduler() {}

    public static void schedule(Context context, ReminderItem item) {
        if (item == null) return;
        cancel(context, item.id);
        if (item.id <= 0 || item.completed || !item.isScheduled() || item.remindMinutes < 0) return;

        long triggerAt = item.eventTime - (item.remindMinutes * 60_000L);
        if (triggerAt <= System.currentTimeMillis()) return;
        scheduleAt(context, item.id, triggerAt, 0);
    }

    public static void snooze(Context context, long id, int minutes) {
        long triggerAt = System.currentTimeMillis() + Math.max(1, minutes) * 60_000L;
        // A reminder can only have one active snooze. Use a stable request code so it can be replaced/cancelled.
        scheduleAt(context, id, triggerAt, 1);
    }

    public static void scheduleAt(Context context, long id, long triggerAt, int requestOffset) {
        if (id <= 0 || triggerAt <= System.currentTimeMillis()) return;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;

        PendingIntent pendingIntent = pendingIntent(context, id, requestOffset);
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
        if (manager == null || id <= 0) return;
        manager.cancel(pendingIntent(context, id, 0));
        manager.cancel(pendingIntent(context, id, 1));
    }

    public static void rescheduleAll(Context context) {
        EventDatabase db = new EventDatabase(context.getApplicationContext());
        for (ReminderItem item : db.futureWithAlerts(System.currentTimeMillis())) {
            schedule(context, item);
        }
        db.close();
    }

    private static PendingIntent pendingIntent(Context context, long id, int requestOffset) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra("reminder_id", id);
        int base = (int) (id & 0x1FFFFF);
        int requestCode = Math.abs(base * 100 + requestOffset);
        return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
