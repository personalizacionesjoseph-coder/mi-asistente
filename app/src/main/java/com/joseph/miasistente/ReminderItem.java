package com.joseph.miasistente;

public class ReminderItem {
    public long id;
    public String kind;
    public String title;
    public String notes;
    public long eventTime;
    public int remindMinutes;

    public ReminderItem() {
        id = 0;
        kind = "Recordatorio";
        title = "";
        notes = "";
        eventTime = 0;
        remindMinutes = 0;
    }
}
