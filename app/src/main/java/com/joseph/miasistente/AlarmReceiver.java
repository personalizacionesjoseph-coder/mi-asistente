package com.joseph.miasistente;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        long id = intent.getLongExtra("reminder_id", -1);
        if (id <= 0) return;

        EventDatabase db = new EventDatabase(context.getApplicationContext());
        ReminderItem item = db.get(id);
        db.close();
        if (item != null) NotificationHelper.show(context, item);
    }
}
