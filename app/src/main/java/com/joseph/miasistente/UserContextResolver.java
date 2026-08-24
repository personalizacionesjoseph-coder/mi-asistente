package com.joseph.miasistente;

import android.content.Context;

import java.util.Calendar;
import java.util.Locale;

public final class UserContextResolver {
    private UserContextResolver() {}

    public static String enrich(Context context, String spoken, long nowMillis) {
        if (spoken == null) return "";
        String normalized = VoiceCommandParser.normalizeForIntent(spoken);
        boolean afterWork = normalized.contains("despues del trabajo")
                || normalized.contains("al terminar el trabajo")
                || normalized.contains("cuando termine de trabajar")
                || normalized.contains("al salir del trabajo");
        boolean beforeWork = normalized.contains("antes del trabajo")
                || normalized.contains("antes de trabajar");
        boolean startWork = normalized.contains("al empezar a trabajar")
                || normalized.contains("cuando empiece a trabajar");
        if (!afterWork && !beforeWork && !startWork) return spoken;

        String hhmm = afterWork ? AppPrefs.workEnd(context) : AppPrefs.workStart(context);
        int[] time = parseTime(hhmm);
        if (time == null) return spoken;

        int hour = time[0];
        int minute = time[1];
        if (afterWork) minute += 30;
        if (beforeWork) minute -= 30;
        while (minute >= 60) { hour++; minute -= 60; }
        while (minute < 0) { hour--; minute += 60; }
        if (hour >= 24) hour -= 24;
        if (hour < 0) hour += 24;

        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(nowMillis);
        Calendar target = (Calendar) now.clone();
        target.set(Calendar.HOUR_OF_DAY, hour);
        target.set(Calendar.MINUTE, minute);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);
        String dayWord = target.getTimeInMillis() > nowMillis ? "hoy" : "mañana";
        String replacement = dayWord + " a las " + String.format(Locale.ROOT, "%02d:%02d", hour, minute);

        String out = spoken;
        out = out.replaceAll("(?iu)despu[eé]s del trabajo", replacement);
        out = out.replaceAll("(?iu)al terminar el trabajo", replacement);
        out = out.replaceAll("(?iu)cuando termine de trabajar", replacement);
        out = out.replaceAll("(?iu)al salir del trabajo", replacement);
        out = out.replaceAll("(?iu)antes del trabajo", replacement);
        out = out.replaceAll("(?iu)antes de trabajar", replacement);
        out = out.replaceAll("(?iu)al empezar a trabajar", replacement);
        out = out.replaceAll("(?iu)cuando empiece a trabajar", replacement);
        return out;
    }

    private static int[] parseTime(String hhmm) {
        if (hhmm == null || !hhmm.matches("^([01]\\d|2[0-3]):[0-5]\\d$")) return null;
        String[] parts = hhmm.split(":");
        try {
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
