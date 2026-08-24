package com.joseph.miasistente;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class EventDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "assistant.db";
    private static final int DB_VERSION = 2;

    public EventDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE reminders (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "kind TEXT NOT NULL," +
                "title TEXT NOT NULL," +
                "notes TEXT NOT NULL DEFAULT ''," +
                "event_time INTEGER NOT NULL," +
                "remind_minutes INTEGER NOT NULL DEFAULT 0," +
                "calendar_event_id INTEGER NOT NULL DEFAULT 0," +
                "calendar_id INTEGER NOT NULL DEFAULT 0," +
                "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_reminders_event_time ON reminders(event_time)");
        db.execSQL("CREATE INDEX idx_reminders_calendar_event ON reminders(calendar_event_id)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE reminders ADD COLUMN calendar_event_id INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE reminders ADD COLUMN calendar_id INTEGER NOT NULL DEFAULT 0");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_reminders_calendar_event ON reminders(calendar_event_id)");
        }
    }

    public synchronized long save(ReminderItem item) {
        ContentValues values = new ContentValues();
        values.put("kind", item.kind);
        values.put("title", item.title);
        values.put("notes", item.notes == null ? "" : item.notes);
        values.put("event_time", item.eventTime);
        values.put("remind_minutes", item.remindMinutes);
        values.put("calendar_event_id", item.calendarEventId);
        values.put("calendar_id", item.calendarId);

        SQLiteDatabase db = getWritableDatabase();
        if (item.id > 0) {
            db.update("reminders", values, "id=?", new String[]{String.valueOf(item.id)});
            return item.id;
        }

        values.put("created_at", System.currentTimeMillis());
        return db.insertOrThrow("reminders", null, values);
    }

    public synchronized ReminderItem get(long id) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query("reminders", null, "id=?",
                new String[]{String.valueOf(id)}, null, null, null)) {
            if (c.moveToFirst()) return fromCursor(c);
        }
        return null;
    }

    public synchronized List<ReminderItem> upcoming(long now) {
        return queryList("event_time>=?", new String[]{String.valueOf(now)}, "event_time ASC");
    }

    public synchronized List<ReminderItem> between(long startInclusive, long endExclusive) {
        return queryList("event_time>=? AND event_time<?",
                new String[]{String.valueOf(startInclusive), String.valueOf(endExclusive)}, "event_time ASC");
    }

    public synchronized ReminderItem nextAfter(long now) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query("reminders", null, "event_time>=?",
                new String[]{String.valueOf(now)}, null, null, "event_time ASC", "1")) {
            if (c.moveToFirst()) return fromCursor(c);
        }
        return null;
    }

    public synchronized List<ReminderItem> futureWithAlerts(long now) {
        return queryList("event_time>=? AND remind_minutes>=0",
                new String[]{String.valueOf(now)}, "event_time ASC");
    }

    public synchronized List<ReminderItem> linkedEvents() {
        return queryList("calendar_event_id>0", null, "event_time ASC");
    }

    public synchronized void delete(long id) {
        getWritableDatabase().delete("reminders", "id=?", new String[]{String.valueOf(id)});
    }

    private List<ReminderItem> queryList(String selection, String[] args, String orderBy) {
        List<ReminderItem> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query("reminders", null, selection, args, null, null, orderBy)) {
            while (c.moveToNext()) result.add(fromCursor(c));
        }
        return result;
    }

    private ReminderItem fromCursor(Cursor c) {
        ReminderItem item = new ReminderItem();
        item.id = c.getLong(c.getColumnIndexOrThrow("id"));
        item.kind = c.getString(c.getColumnIndexOrThrow("kind"));
        item.title = c.getString(c.getColumnIndexOrThrow("title"));
        item.notes = c.getString(c.getColumnIndexOrThrow("notes"));
        item.eventTime = c.getLong(c.getColumnIndexOrThrow("event_time"));
        item.remindMinutes = c.getInt(c.getColumnIndexOrThrow("remind_minutes"));
        item.calendarEventId = c.getLong(c.getColumnIndexOrThrow("calendar_event_id"));
        item.calendarId = c.getLong(c.getColumnIndexOrThrow("calendar_id"));
        return item;
    }
}
