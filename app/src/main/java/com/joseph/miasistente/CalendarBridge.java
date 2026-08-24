package com.joseph.miasistente;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TimeZone;

public final class CalendarBridge {
    private CalendarBridge() {}

    public static class CalendarOption {
        public final long id;
        public final String name;
        public final String account;
        public final String accountType;
        public final boolean primary;

        CalendarOption(long id, String name, String account, String accountType, boolean primary) {
            this.id = id;
            this.name = name == null || name.trim().isEmpty() ? "Calendario" : name;
            this.account = account == null ? "" : account;
            this.accountType = accountType == null ? "" : accountType;
            this.primary = primary;
        }

        public boolean isGoogle() {
            return "com.google".equals(accountType);
        }

        public String label() {
            String source = isGoogle() ? "Google" : "Calendario";
            if (!account.isEmpty()) return name + "\n" + account + " · " + source;
            return name + " · " + source;
        }
    }

    public static class SyncResult {
        public int pushed;
        public int pulled;
        public int removed;
        public int failed;
    }

    public static boolean hasPermissions(Context context) {
        return context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                && context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    public static List<CalendarOption> writableCalendars(Context context) {
        List<CalendarOption> result = new ArrayList<>();
        if (!hasPermissions(context)) return result;

        String[] projection = new String[]{
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.VISIBLE,
                CalendarContract.Calendars.SYNC_EVENTS,
                CalendarContract.Calendars.IS_PRIMARY
        };

        try (Cursor c = context.getContentResolver().query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + ">=?",
                new String[]{String.valueOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)},
                null)) {
            if (c != null) {
                while (c.moveToNext()) {
                    int visible = c.getInt(5);
                    int syncEvents = c.getInt(6);
                    String accountType = c.getString(3);
                    if (visible == 0 || syncEvents == 0 || !"com.google".equals(accountType)) continue;
                    result.add(new CalendarOption(
                            c.getLong(0),
                            c.getString(1),
                            c.getString(2),
                            accountType,
                            !c.isNull(7) && c.getInt(7) == 1));
                }
            }
        } catch (SecurityException ignored) {
            return new ArrayList<>();
        }

        Collections.sort(result, new Comparator<CalendarOption>() {
            @Override
            public int compare(CalendarOption a, CalendarOption b) {
                if (a.isGoogle() != b.isGoogle()) return a.isGoogle() ? -1 : 1;
                if (a.primary != b.primary) return a.primary ? -1 : 1;
                return a.name.compareToIgnoreCase(b.name);
            }
        });
        return result;
    }

    public static boolean saveToSelectedCalendar(Context context, EventDatabase db, ReminderItem item) {
        if (item == null || item.completed || !item.canSyncToCalendar()) return false;
        if (!AppPrefs.calendarSyncEnabled(context) || !hasPermissions(context)) return false;
        long selectedCalendar = AppPrefs.calendarId(context);
        if (selectedCalendar <= 0 && item.calendarEventId <= 0) return false;

        try {
            ContentResolver resolver = context.getContentResolver();
            ContentValues values = eventValues(item, item.calendarEventId <= 0 ? selectedCalendar : 0);
            long eventId = item.calendarEventId;

            if (eventId > 0) {
                Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
                int updated = resolver.update(uri, values, null, null);
                if (updated == 0) eventId = 0;
            }

            if (eventId <= 0) {
                ContentValues insertValues = eventValues(item, selectedCalendar);
                Uri uri = resolver.insert(CalendarContract.Events.CONTENT_URI, insertValues);
                if (uri == null) return false;
                eventId = ContentUris.parseId(uri);
                item.calendarId = selectedCalendar;
            }

            item.calendarEventId = eventId;
            if (item.calendarId <= 0) item.calendarId = selectedCalendar;
            db.save(item);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean deleteLinkedEvent(Context context, ReminderItem item) {
        if (item == null || item.calendarEventId <= 0 || !hasPermissions(context)) return false;
        try {
            Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, item.calendarEventId);
            context.getContentResolver().delete(uri, null, null);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static SyncResult syncNow(Context context, EventDatabase db) {
        SyncResult result = new SyncResult();
        if (!hasPermissions(context) || !AppPrefs.calendarSyncEnabled(context) || AppPrefs.calendarId(context) <= 0) {
            return result;
        }

        pullLinked(context, db, result);

        List<ReminderItem> future = db.upcoming(System.currentTimeMillis());
        for (ReminderItem item : future) {
            if (!item.canSyncToCalendar() || item.calendarEventId > 0) continue;
            if (saveToSelectedCalendar(context, db, item)) result.pushed++;
            else result.failed++;
        }
        return result;
    }

    public static SyncResult pullLinkedChanges(Context context, EventDatabase db) {
        SyncResult result = new SyncResult();
        if (!hasPermissions(context) || !AppPrefs.calendarSyncEnabled(context)) return result;
        pullLinked(context, db, result);
        return result;
    }

    private static void pullLinked(Context context, EventDatabase db, SyncResult result) {
        ContentResolver resolver = context.getContentResolver();
        List<ReminderItem> linked = db.linkedEvents();
        String[] projection = new String[]{
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.CALENDAR_ID,
                CalendarContract.Events.DELETED
        };

        for (ReminderItem item : linked) {
            Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, item.calendarEventId);
            boolean found = false;
            try (Cursor c = resolver.query(uri, projection, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    found = true;
                    boolean deleted = c.getInt(5) == 1;
                    if (deleted) {
                        removeLocal(context, db, item);
                        result.removed++;
                        continue;
                    }

                    String title = c.getString(1);
                    String description = c.getString(2);
                    long start = c.getLong(3);
                    long calendarId = c.getLong(4);
                    boolean changed = false;

                    if (title != null && !title.equals(item.title)) {
                        item.title = title;
                        changed = true;
                    }
                    String normalizedNotes = description == null ? "" : description;
                    if (!normalizedNotes.equals(item.notes)) {
                        item.notes = normalizedNotes;
                        changed = true;
                    }
                    if (start > 0 && start != item.eventTime) {
                        item.eventTime = start;
                        changed = true;
                    }
                    if (calendarId > 0 && calendarId != item.calendarId) {
                        item.calendarId = calendarId;
                        changed = true;
                    }

                    if (changed) {
                        db.save(item);
                        AlarmScheduler.schedule(context, item);
                        result.pulled++;
                    }
                }
            } catch (Exception ignored) {
                result.failed++;
                continue;
            }

            if (!found) {
                removeLocal(context, db, item);
                result.removed++;
            }
        }
    }

    private static void removeLocal(Context context, EventDatabase db, ReminderItem item) {
        AlarmScheduler.cancel(context, item.id);
        db.delete(item.id);
    }

    private static ContentValues eventValues(ReminderItem item, long calendarIdForInsert) {
        ContentValues values = new ContentValues();
        if (calendarIdForInsert > 0) values.put(CalendarContract.Events.CALENDAR_ID, calendarIdForInsert);
        values.put(CalendarContract.Events.TITLE, item.title);
        values.put(CalendarContract.Events.DESCRIPTION, item.notes == null ? "" : item.notes);
        values.put(CalendarContract.Events.DTSTART, item.eventTime);
        values.put(CalendarContract.Events.DTEND, item.eventTime + 60L * 60L * 1000L);
        values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
        return values;
    }
}
