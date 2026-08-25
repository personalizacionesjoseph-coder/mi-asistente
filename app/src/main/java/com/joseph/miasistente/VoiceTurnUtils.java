package com.joseph.miasistente;

import java.util.List;

/**
 * Pure-Java helpers for wake-word turns. Kept Android-free so the behavior can
 * be tested without an Android SDK.
 */
public final class VoiceTurnUtils {
    private VoiceTurnUtils() {}

    public static boolean containsWakeWord(String text) {
        String n = VoiceCommandParser.normalizeForIntent(text);
        return !n.isEmpty() && n.matches(".*\\b(lyra|lira)\\b.*");
    }

    public static String commandAfterWakeWord(String text) {
        if (text == null) return "";
        return text.replaceFirst("(?iu)^.*?\\b(?:lyra|lira)\\b[\\s,.:;!?-]*", "").trim();
    }

    /**
     * Returns null when no hypothesis contains the wake word, an empty string
     * when the user said only the wake word, or the longest trailing command.
     */
    public static String bestCommandAfterWakeWord(List<String> matches) {
        if (matches == null) return null;
        String best = null;
        for (String match : matches) {
            if (!containsWakeWord(match)) continue;
            String after = commandAfterWakeWord(match);
            if (best == null || after.length() > best.length()) best = after;
        }
        return best;
    }
}
