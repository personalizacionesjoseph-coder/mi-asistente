package com.joseph.miasistente;

public class VoiceCommand {
    public enum Action {
        CREATE,
        QUERY_TODAY,
        QUERY_TOMORROW,
        QUERY_NEXT,
        REMEMBER,
        COMPLETE,
        CANCEL,
        RESCHEDULE,
        SNOOZE,
        UNKNOWN
    }

    public Action action = Action.UNKNOWN;
    public String kind = "Recordatorio";
    public String title = "";
    public long eventTime = 0;
    public boolean hasTime = true;
    public String issue = "";
    public boolean timeWasAmbiguous = false;
    public boolean missingTitle = false;
    public boolean missingDate = false;
    public boolean missingTime = false;
    public String targetQuery = "";
    public long targetId = 0;
    public String memoryFact = "";
    public int snoozeMinutes = 0;
}
