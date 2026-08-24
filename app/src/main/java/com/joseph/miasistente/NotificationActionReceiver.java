package com.joseph.miasistente;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class NotificationActionReceiver extends BroadcastReceiver {
    public static final String ACTION_DONE = "com.joseph.miasistente.NOTIFICATION_DONE";
    public static final String ACTION_SNOOZE_10 = "com.joseph.miasistente.NOTIFICATION_SNOOZE_10";
    public static final String ACTION_SNOOZE_60 = "com.joseph.miasistente.NOTIFICATION_SNOOZE_60";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        long id = intent.getLongExtra("reminder_id", -1);
        if (id <= 0) return;

        EventDatabase db = new EventDatabase(context.getApplicationContext());
        ReminderItem item = db.get(id);
        if (item == null) {
            db.close();
            dismiss(context, id);
            return;
        }

        String action = intent.getAction();
        if (ACTION_DONE.equals(action)) {
            db.markCompleted(id, true);
            AlarmScheduler.cancel(context, id);
            dismiss(context, id);
            Toast.makeText(context, "Marcado como hecho", Toast.LENGTH_SHORT).show();
        } else if (ACTION_SNOOZE_10.equals(action)) {
            AlarmScheduler.snooze(context, id, 10);
            dismiss(context, id);
            Toast.makeText(context, "Lyra te avisará en 10 minutos", Toast.LENGTH_SHORT).show();
        } else if (ACTION_SNOOZE_60.equals(action)) {
            AlarmScheduler.snooze(context, id, 60);
            dismiss(context, id);
            Toast.makeText(context, "Lyra te avisará en 1 hora", Toast.LENGTH_SHORT).show();
        }
        db.close();
    }

    private void dismiss(Context context, long id) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(NotificationHelper.notificationId(id));
    }
}
