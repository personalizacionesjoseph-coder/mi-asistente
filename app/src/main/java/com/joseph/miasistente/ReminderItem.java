package com.joseph.miasistente;

public class ReminderItem {
    public long id;
    public String kind;
    public String title;
    public String notes;
    public long eventTime;
    public int remindMinutes;
    public long calendarEventId;
    public long calendarId;
    public boolean completed;
    public boolean hasTime;
    public long updatedAt;

    public ReminderItem() {
        id = 0;
        kind = "Recordatorio";
        title = "";
        notes = "";
        eventTime = 0;
        remindMinutes = 0;
        calendarEventId = 0;
        calendarId = 0;
        completed = false;
        hasTime = true;
        updatedAt = System.currentTimeMillis();
    }

    public boolean isScheduled() {
        return hasTime && eventTime > 0;
    }

    public boolean canSyncToCalendar() {
        return isScheduled() && ("Cita".equals(kind) || "Recordatorio".equals(kind));
    }
}
