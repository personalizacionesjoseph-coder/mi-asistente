package com.joseph.miasistente;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EventDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "assistant.db";
    private static final int DB_VERSION = 4;

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
                "event_time INTEGER NOT NULL DEFAULT 0," +
                "remind_minutes INTEGER NOT NULL DEFAULT 0," +
                "calendar_event_id INTEGER NOT NULL DEFAULT 0," +
                "calendar_id INTEGER NOT NULL DEFAULT 0," +
                "completed INTEGER NOT NULL DEFAULT 0," +
                "has_time INTEGER NOT NULL DEFAULT 1," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_reminders_event_time ON reminders(event_time)");
        db.execSQL("CREATE INDEX idx_reminders_calendar_event ON reminders(calendar_event_id)");
        db.execSQL("CREATE INDEX idx_reminders_active ON reminders(completed, has_time, event_time)");
        createMemoryTable(db);
    }

    private void createMemoryTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS memories (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "fact TEXT NOT NULL," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_memories_updated ON memories(updated_at)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE reminders ADD COLUMN calendar_event_id INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE reminders ADD COLUMN calendar_id INTEGER NOT NULL DEFAULT 0");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_reminders_calendar_event ON reminders(calendar_event_id)");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE reminders ADD COLUMN completed INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE reminders ADD COLUMN has_time INTEGER NOT NULL DEFAULT 1");
            db.execSQL("ALTER TABLE reminders ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0");
            db.execSQL("UPDATE reminders SET updated_at=created_at WHERE updated_at=0");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_reminders_active ON reminders(completed, has_time, event_time)");
        }
        if (oldVersion < 4) {
            createMemoryTable(db);
        }
    }

    public synchronized long save(ReminderItem item) {
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("kind", safe(item.kind, "Recordatorio"));
        values.put("title", safe(item.title, ""));
        values.put("notes", safe(item.notes, ""));
        values.put("event_time", item.hasTime ? Math.max(0, item.eventTime) : 0);
        values.put("remind_minutes", item.hasTime ? item.remindMinutes : -1);
        values.put("calendar_event_id", item.calendarEventId);
        values.put("calendar_id", item.calendarId);
        values.put("completed", item.completed ? 1 : 0);
        values.put("has_time", item.hasTime ? 1 : 0);
        values.put("updated_at", now);

        SQLiteDatabase db = getWritableDatabase();
        if (item.id > 0) {
            db.update("reminders", values, "id=?", new String[]{String.valueOf(item.id)});
            item.updatedAt = now;
            return item.id;
        }

        values.put("created_at", now);
        long id = db.insertOrThrow("reminders", null, values);
        item.updatedAt = now;
        return id;
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
        return queryList("completed=0 AND has_time=1 AND event_time>=?",
                new String[]{String.valueOf(now)}, "event_time ASC");
    }

    public synchronized List<ReminderItem> between(long startInclusive, long endExclusive) {
        return queryList("completed=0 AND has_time=1 AND event_time>=? AND event_time<?",
                new String[]{String.valueOf(startInclusive), String.valueOf(endExclusive)}, "event_time ASC");
    }

    public synchronized List<ReminderItem> activeUnscheduled() {
        return queryList("completed=0 AND (has_time=0 OR event_time<=0)", null, "updated_at DESC");
    }

    public synchronized List<ReminderItem> activeTasks() {
        return queryList("completed=0 AND kind='Tarea'", null,
                "CASE WHEN has_time=1 THEN 0 ELSE 1 END, event_time ASC, updated_at DESC");
    }

    public synchronized List<ReminderItem> recentNotes(int limit) {
        List<ReminderItem> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query("reminders", null, "completed=0 AND kind='Nota'",
                null, null, null, "updated_at DESC", String.valueOf(Math.max(1, limit)))) {
            while (c.moveToNext()) result.add(fromCursor(c));
        }
        return result;
    }

    public synchronized ReminderItem nextAfter(long now) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query("reminders", null,
                "completed=0 AND has_time=1 AND event_time>=?",
                new String[]{String.valueOf(now)}, null, null, "event_time ASC", "1")) {
            if (c.moveToFirst()) return fromCursor(c);
        }
        return null;
    }

    public synchronized List<ReminderItem> futureWithAlerts(long now) {
        return queryList("completed=0 AND has_time=1 AND event_time>=? AND remind_minutes>=0",
                new String[]{String.valueOf(now)}, "event_time ASC");
    }

    public synchronized List<ReminderItem> linkedEvents() {
        return queryList("calendar_event_id>0", null, "event_time ASC");
    }

    public synchronized List<ReminderItem> allActive() {
        return queryList("completed=0", null,
                "CASE WHEN has_time=1 THEN 0 ELSE 1 END, event_time ASC, updated_at DESC");
    }

    public synchronized void markCompleted(long id, boolean completed) {
        ContentValues values = new ContentValues();
        values.put("completed", completed ? 1 : 0);
        values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("reminders", values, "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void delete(long id) {
        getWritableDatabase().delete("reminders", "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized ReminderItem findBestActiveMatch(String query, long now) {
        String q = normalize(query);
        if (q.isEmpty()) return null;
        List<ReminderItem> items = allActive();
        ReminderItem best = null;
        int bestScore = 0;
        for (ReminderItem item : items) {
            String haystack = normalize(item.title + " " + item.kind + " " + safe(item.notes, ""));
            int score = tokenScore(q, haystack);
            if (item.hasTime && item.eventTime >= now) score += 1;
            if (score > bestScore) {
                bestScore = score;
                best = item;
            }
        }
        return bestScore >= 2 ? best : null;
    }

    private int tokenScore(String query, String haystack) {
        int score = 0;
        for (String token : query.split("\\s+")) {
            if (token.length() < 3 || isStopWord(token)) continue;
            if (haystack.contains(token)) score += 2;
        }
        if (haystack.contains(query)) score += 4;
        return score;
    }

    private boolean isStopWord(String token) {
        return token.equals("cita") || token.equals("tarea") || token.equals("recordatorio") ||
                token.equals("nota") || token.equals("para") || token.equals("con") ||
                token.equals("del") || token.equals("una") || token.equals("que") || token.equals("mi");
    }

    public synchronized long addMemory(String fact) {
        String clean = fact == null ? "" : fact.trim();
        if (clean.isEmpty()) return -1;
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("fact", clean);
        values.put("created_at", now);
        values.put("updated_at", now);
        return getWritableDatabase().insertOrThrow("memories", null, values);
    }

    public synchronized void updateMemory(long id, String fact) {
        String clean = fact == null ? "" : fact.trim();
        if (clean.isEmpty()) return;
        ContentValues values = new ContentValues();
        values.put("fact", clean);
        values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("memories", values, "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void deleteMemory(long id) {
        getWritableDatabase().delete("memories", "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void clearMemories() {
        getWritableDatabase().delete("memories", null, null);
    }

    public synchronized List<MemoryItem> memories() {
        List<MemoryItem> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query("memories", null, null, null, null, null, "updated_at DESC")) {
            while (c.moveToNext()) {
                MemoryItem item = new MemoryItem();
                item.id = c.getLong(c.getColumnIndexOrThrow("id"));
                item.fact = c.getString(c.getColumnIndexOrThrow("fact"));
                item.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
                item.updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"));
                result.add(item);
            }
        }
        return result;
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
        item.completed = c.getInt(c.getColumnIndexOrThrow("completed")) == 1;
        item.hasTime = c.getInt(c.getColumnIndexOrThrow("has_time")) == 1;
        item.updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"));
        return item;
    }

    private static String normalize(String input) {
        if (input == null) return "";
        return Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9ñ ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String safe(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
