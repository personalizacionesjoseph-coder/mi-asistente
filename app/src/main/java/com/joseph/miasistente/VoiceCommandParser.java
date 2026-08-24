package com.joseph.miasistente;

import java.text.Normalizer;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VoiceCommandParser {
    private static final Pattern NUMERIC_DATE = Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?\\b");
    private static final Pattern MONTH_DATE = Pattern.compile("\\b(?:el\\s+)?(\\d{1,2})\\s+de\\s+(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre)(?:\\s+de\\s+(\\d{4}))?\\b");
    private static final Pattern TIME_AT = Pattern.compile("\\b(?:a\\s+las?|para\\s+las?)\\s+(\\d{1,2})(?::(\\d{2}))?(?:\\s*(am|pm)|\\s+de\\s+la\\s+(manana|tarde|noche))?\\b");
    private static final Pattern TIME_COLON = Pattern.compile("\\b(\\d{1,2}):(\\d{2})\\s*(am|pm)?\\b");
    private static final Pattern TIME_AMPM = Pattern.compile("\\b(\\d{1,2})\\s*(am|pm)\\b");
    private static final Pattern TIME_WORD = Pattern.compile("\\b(?:a\\s+las?|a\\s+la|para\\s+las?|para\\s+la)\\s+(una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce)(?:\\s+y\\s+(media|cuarto))?(?:\\s+de\\s+la\\s+(manana|tarde|noche))?\\b");

    private static final Map<String, Integer> MONTHS = new HashMap<>();
    private static final Map<String, Integer> WEEKDAYS = new HashMap<>();
    private static final Map<String, Integer> WORD_HOURS = new HashMap<>();

    static {
        String[] months = {"enero","febrero","marzo","abril","mayo","junio","julio","agosto","septiembre","octubre","noviembre","diciembre"};
        for (int i = 0; i < months.length; i++) MONTHS.put(months[i], i);
        MONTHS.put("setiembre", Calendar.SEPTEMBER);

        WEEKDAYS.put("domingo", Calendar.SUNDAY);
        WEEKDAYS.put("lunes", Calendar.MONDAY);
        WEEKDAYS.put("martes", Calendar.TUESDAY);
        WEEKDAYS.put("miercoles", Calendar.WEDNESDAY);
        WEEKDAYS.put("jueves", Calendar.THURSDAY);
        WEEKDAYS.put("viernes", Calendar.FRIDAY);
        WEEKDAYS.put("sabado", Calendar.SATURDAY);

        WORD_HOURS.put("una", 1);
        WORD_HOURS.put("dos", 2);
        WORD_HOURS.put("tres", 3);
        WORD_HOURS.put("cuatro", 4);
        WORD_HOURS.put("cinco", 5);
        WORD_HOURS.put("seis", 6);
        WORD_HOURS.put("siete", 7);
        WORD_HOURS.put("ocho", 8);
        WORD_HOURS.put("nueve", 9);
        WORD_HOURS.put("diez", 10);
        WORD_HOURS.put("once", 11);
        WORD_HOURS.put("doce", 12);
    }

    private VoiceCommandParser() {}

    public static VoiceCommand parse(String spoken, long nowMillis) {
        VoiceCommand out = new VoiceCommand();
        if (spoken == null || spoken.trim().isEmpty()) {
            out.issue = "No escuché una instrucción.";
            return out;
        }

        String original = spoken.trim();
        String text = normalize(original);

        if (containsAny(text, "que tengo hoy", "agenda de hoy", "citas de hoy", "recordatorios de hoy", "mi agenda hoy")) {
            out.action = VoiceCommand.Action.QUERY_TODAY;
            return out;
        }
        if (containsAny(text, "que tengo manana", "agenda de manana", "citas de manana", "recordatorios de manana", "mi agenda manana")) {
            out.action = VoiceCommand.Action.QUERY_TOMORROW;
            return out;
        }
        if (containsAny(text, "proxima cita", "proximo recordatorio", "que sigue", "que tengo despues", "siguiente cita")) {
            out.action = VoiceCommand.Action.QUERY_NEXT;
            return out;
        }

        out.action = VoiceCommand.Action.CREATE;
        out.kind = looksLikeAppointment(text) ? "Cita" : "Recordatorio";

        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(nowMillis);
        Calendar target = (Calendar) now.clone();
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        boolean dateFound = applyDate(text, target, now);
        TimeResult time = parseTime(text);
        if (!time.found) {
            out.issue = "Entendí la tarea, pero me falta la hora.";
            out.title = cleanupTitle(original, out.kind);
            return out;
        }

        int hour = time.hour;
        int minute = time.minute;
        out.timeWasAmbiguous = time.ambiguous;
        target.set(Calendar.HOUR_OF_DAY, hour);
        target.set(Calendar.MINUTE, minute);

        if (!dateFound) {
            if (target.getTimeInMillis() <= nowMillis) target.add(Calendar.DAY_OF_YEAR, 1);
        } else if (isWeekdayMention(text) && sameCalendarDay(target, now) && target.getTimeInMillis() <= nowMillis) {
            target.add(Calendar.DAY_OF_YEAR, 7);
        }

        if (target.getTimeInMillis() <= nowMillis) {
            out.issue = "La fecha y hora que entendí ya pasaron.";
            out.title = cleanupTitle(original, out.kind);
            return out;
        }

        out.eventTime = target.getTimeInMillis();
        out.title = cleanupTitle(original, out.kind);
        if (out.title.isEmpty()) out.title = out.kind;
        return out;
    }

    private static boolean applyDate(String text, Calendar target, Calendar now) {
        String dateText = text.replaceAll("\\bde\\s+la\\s+manana\\b", " ");
        if (dateText.contains("pasado manana")) {
            target.add(Calendar.DAY_OF_YEAR, 2);
            return true;
        }
        if (dateText.matches(".*\\bmanana\\b.*")) {
            target.add(Calendar.DAY_OF_YEAR, 1);
            return true;
        }
        if (dateText.matches(".*\\bhoy\\b.*")) return true;

        Matcher numeric = NUMERIC_DATE.matcher(text);
        if (numeric.find()) {
            int day = safeInt(numeric.group(1), -1);
            int month = safeInt(numeric.group(2), -1) - 1;
            int year = numeric.group(3) == null ? now.get(Calendar.YEAR) : safeInt(numeric.group(3), now.get(Calendar.YEAR));
            if (year < 100) year += 2000;
            if (validDate(day, month, year)) {
                target.set(Calendar.YEAR, year);
                target.set(Calendar.MONTH, month);
                target.set(Calendar.DAY_OF_MONTH, day);
                if (numeric.group(3) == null && target.before(now)) target.add(Calendar.YEAR, 1);
                return true;
            }
        }

        Matcher monthDate = MONTH_DATE.matcher(text);
        if (monthDate.find()) {
            int day = safeInt(monthDate.group(1), -1);
            Integer month = MONTHS.get(monthDate.group(2));
            int year = monthDate.group(3) == null ? now.get(Calendar.YEAR) : safeInt(monthDate.group(3), now.get(Calendar.YEAR));
            if (month != null && validDate(day, month, year)) {
                target.set(Calendar.YEAR, year);
                target.set(Calendar.MONTH, month);
                target.set(Calendar.DAY_OF_MONTH, day);
                if (monthDate.group(3) == null && target.before(now)) target.add(Calendar.YEAR, 1);
                return true;
            }
        }

        for (Map.Entry<String, Integer> e : WEEKDAYS.entrySet()) {
            if (text.matches(".*\\b" + e.getKey() + "\\b.*")) {
                int current = now.get(Calendar.DAY_OF_WEEK);
                int diff = (e.getValue() - current + 7) % 7;
                target.add(Calendar.DAY_OF_YEAR, diff);
                return true;
            }
        }
        return false;
    }

    private static TimeResult parseTime(String text) {
        Matcher m = TIME_AT.matcher(text);
        if (m.find()) {
            int hour = safeInt(m.group(1), -1);
            int minute = m.group(2) == null ? 0 : safeInt(m.group(2), 0);
            String ampm = m.group(3);
            String daypart = m.group(4);
            return normalizeTime(hour, minute, ampm, daypart, ampm == null && daypart == null);
        }

        m = TIME_WORD.matcher(text);
        if (m.find()) {
            Integer baseHour = WORD_HOURS.get(m.group(1));
            int minute = "media".equals(m.group(2)) ? 30 : "cuarto".equals(m.group(2)) ? 15 : 0;
            String daypart = m.group(3);
            return normalizeTime(baseHour == null ? -1 : baseHour, minute, null, daypart, daypart == null);
        }

        m = TIME_COLON.matcher(text);
        if (m.find()) {
            return normalizeTime(safeInt(m.group(1), -1), safeInt(m.group(2), -1), m.group(3), null, false);
        }

        m = TIME_AMPM.matcher(text);
        if (m.find()) {
            return normalizeTime(safeInt(m.group(1), -1), 0, m.group(2), null, false);
        }

        return new TimeResult(false, 0, 0, false);
    }

    private static TimeResult normalizeTime(int hour, int minute, String ampm, String daypart, boolean ambiguous) {
        if (hour < 0 || minute < 0 || minute > 59) return new TimeResult(false, 0, 0, false);

        if (ampm != null) {
            if (hour < 1 || hour > 12) return new TimeResult(false, 0, 0, false);
            if ("pm".equals(ampm) && hour != 12) hour += 12;
            if ("am".equals(ampm) && hour == 12) hour = 0;
        } else if (daypart != null) {
            if (hour < 1 || hour > 12) return new TimeResult(false, 0, 0, false);
            if (("tarde".equals(daypart) || "noche".equals(daypart)) && hour != 12) hour += 12;
            if ("manana".equals(daypart) && hour == 12) hour = 0;
        } else {
            if (hour > 23) return new TimeResult(false, 0, 0, false);
            // Para frases como "a las 3", una interpretación práctica es 15:00.
            // La pantalla de confirmación siempre muestra la hora antes de guardar.
            if (ambiguous && hour >= 1 && hour <= 7) hour += 12;
        }
        return new TimeResult(true, hour, minute, ambiguous);
    }

    private static boolean looksLikeAppointment(String text) {
        return containsAny(text, "cita", "reunion", "reunirme", "agenda", "agendame", "consulta", "entrevista");
    }

    private static String cleanupTitle(String original, String kind) {
        String s = original.trim();
        s = s.replaceFirst("(?iu)^\\s*(recu[eé]rdame|recordarme|ponme\\s+un\\s+recordatorio|pon\\s+un\\s+recordatorio|crea\\s+un\\s+recordatorio|crear\\s+un\\s+recordatorio|ag[eé]ndame|agenda|programa)\\s*", "");
        s = s.replaceFirst("(?iu)^\\s*(una?\\s+)?(cita|recordatorio)\\s*", "");

        // Fechas explícitas.
        s = s.replaceAll("(?iu)\\b(?:el\\s+)?\\d{1,2}\\s+de\\s+(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre)(?:\\s+de\\s+\\d{4})?\\b", " ");
        s = s.replaceAll("\\b\\d{1,2}[/-]\\d{1,2}(?:[/-]\\d{2,4})?\\b", " ");

        // Primero elimina horas con "de la mañana/tarde/noche" para no confundir "mañana" con el día siguiente.
        s = s.replaceAll("(?iu)\\b(?:a\\s+las?|para\\s+las?|a\\s+la|para\\s+la)\\s+(?:\\d{1,2}(?::\\d{2})?|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce)(?:\\s+y\\s+(?:media|cuarto))?\\s+de\\s+la\\s+(?:mañana|manana|tarde|noche)\\b", " ");
        s = s.replaceAll("(?iu)\\b(?:a\\s+las?|para\\s+las?|a\\s+la|para\\s+la)\\s+(?:\\d{1,2}(?::\\d{2})?|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce)(?:\\s+y\\s+(?:media|cuarto))?\\s*(?:a\\.?\\s*m\\.?|p\\.?\\s*m\\.?|am|pm)?\\b", " ");
        s = s.replaceAll("(?iu)\\b\\d{1,2}:\\d{2}\\s*(?:am|pm)?\\b", " ");
        s = s.replaceAll("(?iu)\\b\\d{1,2}\\s*(?:am|pm)\\b", " ");

        // Días relativos y días de semana.
        s = s.replaceAll("(?iu)\\b(pasado\\s+mañana|pasado\\s+manana|hoy|mañana|manana|lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)\\b", " ");

        s = s.replaceAll("\\s+", " ").trim();
        s = s.replaceFirst("(?iu)^(el|la|para)\\s+", "").trim();
        s = s.replaceAll("(?iu)\\s+(el|a|para)$", "").trim();

        if ("Cita".equals(kind) && s.toLowerCase(Locale.ROOT).startsWith("con ")) {
            s = "Cita " + s;
        }
        if (!s.isEmpty()) s = Character.toUpperCase(s.charAt(0)) + s.substring(1);
        return s;
    }

    private static String normalize(String input) {
        String n = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace("a. m.", "am")
                .replace("p. m.", "pm")
                .replace("a.m.", "am")
                .replace("p.m.", "pm");
        return n.replaceAll("\\s+", " ").trim();
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }

    private static boolean isWeekdayMention(String text) {
        for (String day : WEEKDAYS.keySet()) if (text.matches(".*\\b" + day + "\\b.*")) return true;
        return false;
    }

    private static boolean sameCalendarDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private static boolean validDate(int day, int month, int year) {
        if (year < 2020 || year > 2200 || month < 0 || month > 11 || day < 1 || day > 31) return false;
        Calendar c = Calendar.getInstance();
        c.setLenient(false);
        c.clear();
        c.set(year, month, day);
        try {
            c.getTime();
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static int safeInt(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (Exception e) { return fallback; }
    }

    private static class TimeResult {
        final boolean found;
        final int hour;
        final int minute;
        final boolean ambiguous;
        TimeResult(boolean found, int hour, int minute, boolean ambiguous) {
            this.found = found;
            this.hour = hour;
            this.minute = minute;
            this.ambiguous = ambiguous;
        }
    }
}
