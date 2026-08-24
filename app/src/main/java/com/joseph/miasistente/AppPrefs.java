package com.joseph.miasistente;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class AppPrefs {
    private static final String FILE = "mi_asistente_settings";

    public static final String SECTION_VOICE = "voice";
    public static final String SECTION_QUICK = "quick";
    public static final String SECTION_AGENDA = "agenda";

    private AppPrefs() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String themeMode(Context context) {
        return prefs(context).getString("theme_mode", "system");
    }

    public static void setThemeMode(Context context, String value) {
        prefs(context).edit().putString("theme_mode", value).apply();
    }

    public static String accent(Context context) {
        return prefs(context).getString("accent", "violet");
    }

    public static void setAccent(Context context, String value) {
        prefs(context).edit().putString("accent", value).apply();
    }

    public static boolean isSectionVisible(Context context, String id) {
        return prefs(context).getBoolean("section_" + id, true);
    }

    public static void setSectionVisible(Context context, String id, boolean visible) {
        prefs(context).edit().putBoolean("section_" + id, visible).apply();
    }

    public static List<String> homeOrder(Context context) {
        String raw = prefs(context).getString("home_order", "voice,quick,agenda");
        List<String> result = new ArrayList<>();
        for (String token : raw.split(",")) {
            if (SECTION_VOICE.equals(token) || SECTION_QUICK.equals(token) || SECTION_AGENDA.equals(token)) {
                if (!result.contains(token)) result.add(token);
            }
        }
        for (String id : Arrays.asList(SECTION_VOICE, SECTION_QUICK, SECTION_AGENDA)) {
            if (!result.contains(id)) result.add(id);
        }
        return result;
    }

    public static void setHomeOrder(Context context, List<String> order) {
        prefs(context).edit().putString("home_order", String.join(",", order)).apply();
    }

    public static long calendarId(Context context) {
        return prefs(context).getLong("calendar_id", -1L);
    }

    public static String calendarName(Context context) {
        return prefs(context).getString("calendar_name", "");
    }

    public static String calendarAccount(Context context) {
        return prefs(context).getString("calendar_account", "");
    }

    public static void setCalendar(Context context, long id, String name, String account) {
        prefs(context).edit()
                .putLong("calendar_id", id)
                .putString("calendar_name", name == null ? "" : name)
                .putString("calendar_account", account == null ? "" : account)
                .apply();
    }

    public static boolean calendarSyncEnabled(Context context) {
        return prefs(context).getBoolean("calendar_sync", false);
    }

    public static void setCalendarSyncEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean("calendar_sync", enabled).apply();
    }

    public static String profileName(Context context) {
        return prefs(context).getString("profile_name", "").trim();
    }

    public static String preferredName(Context context) {
        String preferred = prefs(context).getString("preferred_name", "").trim();
        return preferred.isEmpty() ? profileName(context) : preferred;
    }

    public static String profileContext(Context context) {
        return prefs(context).getString("profile_context", "").trim();
    }

    public static String workStart(Context context) {
        return prefs(context).getString("work_start", "08:00");
    }

    public static String workEnd(Context context) {
        return prefs(context).getString("work_end", "18:00");
    }

    public static int defaultReminderMinutes(Context context) {
        return prefs(context).getInt("default_reminder_minutes", 30);
    }

    public static boolean voiceRepliesEnabled(Context context) {
        return prefs(context).getBoolean("voice_replies", true);
    }

    public static void saveProfile(Context context, String name, String preferredName, String workStart,
                                   String workEnd, int defaultReminderMinutes, boolean voiceReplies,
                                   String profileContext) {
        prefs(context).edit()
                .putString("profile_name", safe(name))
                .putString("preferred_name", safe(preferredName))
                .putString("work_start", safe(workStart))
                .putString("work_end", safe(workEnd))
                .putInt("default_reminder_minutes", defaultReminderMinutes)
                .putBoolean("voice_replies", voiceReplies)
                .putString("profile_context", safe(profileContext))
                .apply();
    }

    public static void clearProfile(Context context) {
        prefs(context).edit()
                .remove("profile_name")
                .remove("preferred_name")
                .remove("work_start")
                .remove("work_end")
                .remove("default_reminder_minutes")
                .remove("voice_replies")
                .remove("profile_context")
                .apply();
    }

    public static boolean wakeWordEnabled(Context context) {
        return prefs(context).getBoolean("wake_word_enabled", false);
    }

    public static void setWakeWordEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean("wake_word_enabled", enabled).apply();
    }

    public static String appearanceSignature(Context context) {
        return themeMode(context) + "|" + accent(context) + "|" + homeOrder(context) + "|"
                + isSectionVisible(context, SECTION_VOICE) + "|"
                + isSectionVisible(context, SECTION_QUICK) + "|"
                + isSectionVisible(context, SECTION_AGENDA) + "|"
                + preferredName(context) + "|" + wakeWordEnabled(context);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
