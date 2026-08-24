package com.joseph.miasistente;

public class VoiceCommand {
    public enum Action {
        CREATE,
        QUERY_TODAY,
        QUERY_TOMORROW,
        QUERY_NEXT,
        UNKNOWN
    }

    public Action action = Action.UNKNOWN;
    public String kind = "Recordatorio";
    public String title = "";
    public long eventTime = 0;
    public String issue = "";
    public boolean timeWasAmbiguous = false;
}
