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
    private static final Pattern TIME_DAYPART = Pattern.compile("\\b(\\d{1,2})(?::(\\d{2}))?\\s+de\\s+la\\s+(manana|tarde|noche)\\b");
    private static final Pattern TIME_COLON = Pattern.compile("\\b(\\d{1,2}):(\\d{2})\\s*(am|pm)?\\b");
    private static final Pattern TIME_AMPM = Pattern.compile("\\b(\\d{1,2})\\s*(am|pm)\\b");
    private static final Pattern TIME_WORD = Pattern.compile("\\b(?:a\\s+las?|a\\s+la|para\\s+las?|para\\s+la)\\s+(una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce)(?:\\s+y\\s+(media|cuarto))?(?:\\s+de\\s+la\\s+(manana|tarde|noche))?\\b");
    private static final Pattern SNOOZE_MINUTES = Pattern.compile("\\b(\\d{1,3})\\s*(?:minutos?|min)\\b");
    private static final Pattern SNOOZE_HOURS = Pattern.compile("\\b(\\d{1,2})\\s*(?:horas?|h)\\b");

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
        text = text.replaceFirst("^(oye\\s+)?(lyra|lira)[,.:]?\\s*", "").trim();

        if (containsAny(text, "que tengo hoy", "agenda de hoy", "citas de hoy", "recordatorios de hoy", "mi agenda hoy", "que hay hoy")) {
            out.action = VoiceCommand.Action.QUERY_TODAY;
            return out;
        }
        if (containsAny(text, "que tengo manana", "agenda de manana", "citas de manana", "recordatorios de manana", "mi agenda manana", "que hay manana")) {
            out.action = VoiceCommand.Action.QUERY_TOMORROW;
            return out;
        }
        if (containsAny(text, "proxima cita", "proximo recordatorio", "que sigue", "que tengo despues", "siguiente cita", "siguiente pendiente")) {
            out.action = VoiceCommand.Action.QUERY_NEXT;
            return out;
        }

        if (text.startsWith("recuerda que ") || text.startsWith("memoriza que ") || text.startsWith("guarda que ")) {
            out.action = VoiceCommand.Action.REMEMBER;
            out.memoryFact = original.replaceFirst("(?iu)^\\s*(oye\\s+)?(lyra|lira)[,.:]?\\s*", "")
                    .replaceFirst("(?iu)^\\s*(recuerda|memoriza|guarda)\\s+que\\s+", "").trim();
            if (out.memoryFact.isEmpty()) out.issue = "Dime qué quieres que recuerde.";
            return out;
        }

        if (startsWithAny(text, "marca ", "completa ", "termina ") &&
                (text.contains("como hecho") || text.contains("como hecha") || text.startsWith("completa ") || text.startsWith("termina "))) {
            out.action = VoiceCommand.Action.COMPLETE;
            out.targetQuery = cleanupTarget(original, VoiceCommand.Action.COMPLETE);
            if (out.targetQuery.isEmpty()) out.issue = "Dime qué pendiente quieres marcar como hecho.";
            return out;
        }

        if (startsWithAny(text, "cancela ", "elimina ", "borra ", "quita ")) {
            out.action = VoiceCommand.Action.CANCEL;
            out.targetQuery = cleanupTarget(original, VoiceCommand.Action.CANCEL);
            if (out.targetQuery.isEmpty()) out.issue = "Dime qué quieres cancelar o eliminar.";
            return out;
        }

        if (startsWithAny(text, "pospone ", "aplaza ", "recuerdame de nuevo ")) {
            out.action = VoiceCommand.Action.SNOOZE;
            out.snoozeMinutes = parseSnoozeMinutes(text);
            out.targetQuery = cleanupTarget(original, VoiceCommand.Action.SNOOZE);
            if (out.snoozeMinutes <= 0) out.issue = "Dime cuánto tiempo quieres posponerlo, por ejemplo 10 minutos o 1 hora.";
            else if (out.targetQuery.isEmpty()) out.issue = "Dime qué pendiente quieres posponer.";
            return out;
        }

        if (startsWithAny(text, "mueve ", "reprograma ", "cambia ")) {
            out.action = VoiceCommand.Action.RESCHEDULE;
            out.targetQuery = cleanupTarget(original, VoiceCommand.Action.RESCHEDULE);
            applyRequiredDateTime(out, text, nowMillis);
            if (out.targetQuery.isEmpty()) out.issue = "Dime qué evento quieres reprogramar.";
            return out;
        }

        // A free word after the wake word is not automatically a reminder title.
        // Require a real creation cue (or a sufficiently clear temporal instruction)
        // before entering CREATE mode. Follow-up answers are merged with the original
        // command, so replies such as “York”, “mañana” or “a las siete” still work
        // once Lyra has already understood the user's intent.
        if (!hasCreateIntent(text)) {
            out.action = VoiceCommand.Action.UNKNOWN;
            return out;
        }

        out.action = VoiceCommand.Action.CREATE;
        out.kind = classifyKind(text);
        out.title = cleanupTitle(original, out.kind);
        out.missingTitle = out.title.isEmpty() || out.title.equalsIgnoreCase(out.kind);

        if ("Nota".equals(out.kind)) {
            out.hasTime = false;
            out.missingDate = false;
            out.missingTime = false;
            return out;
        }

        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(nowMillis);
        Calendar target = (Calendar) now.clone();
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        boolean dateFound = applyDate(text, target, now);
        TimeResult time = parseTime(text);
        out.timeWasAmbiguous = time.ambiguous;

        if ("Tarea".equals(out.kind) && !dateFound && !time.found) {
            out.hasTime = false;
            out.missingDate = false;
            out.missingTime = false;
            return out;
        }

        out.hasTime = true;
        out.missingDate = !dateFound;
        out.missingTime = !time.found || time.ambiguous;
        if (out.missingDate || out.missingTime) return out;

        target.set(Calendar.HOUR_OF_DAY, time.hour);
        target.set(Calendar.MINUTE, time.minute);

        if (isWeekdayMention(text) && sameCalendarDay(target, now) && target.getTimeInMillis() <= nowMillis) {
            target.add(Calendar.DAY_OF_YEAR, 7);
        }
        if (target.getTimeInMillis() <= nowMillis) {
            out.issue = "La fecha y hora que entendí ya pasaron.";
            return out;
        }
        out.eventTime = target.getTimeInMillis();
        return out;
    }

    private static void applyRequiredDateTime(VoiceCommand out, String text, long nowMillis) {
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(nowMillis);
        Calendar target = (Calendar) now.clone();
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);
        boolean dateFound = applyDate(text, target, now);
        TimeResult time = parseTime(text);
        out.hasTime = true;
        out.missingDate = !dateFound;
        out.missingTime = !time.found || time.ambiguous;
        out.timeWasAmbiguous = time.ambiguous;
        if (out.missingDate || out.missingTime) return;
        target.set(Calendar.HOUR_OF_DAY, time.hour);
        target.set(Calendar.MINUTE, time.minute);
        if (target.getTimeInMillis() <= nowMillis) {
            out.issue = "La nueva fecha y hora que entendí ya pasaron.";
            return;
        }
        out.eventTime = target.getTimeInMillis();
    }

    private static String classifyKind(String text) {
        if (startsWithAny(text, "anota ", "nota ", "apunta ", "guarda una nota ", "toma nota ")) return "Nota";
        if (containsAny(text, "tarea", "pendiente", "tengo que", "debo ", "por hacer")) return "Tarea";
        if (looksLikeAppointment(text)) return "Cita";
        return "Recordatorio";
    }

    private static boolean hasCreateIntent(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        if (containsAny(text,
                "recuerdame", "recordatorio", "agenda", "agendame", "agendar",
                "agrega", "agregar", "anade", "anadir", "crea", "crear",
                "programa", "programar", "ponme", "pon un", "pon una",
                "cita", "reunion", "consulta", "entrevista",
                "tarea", "pendiente", "tengo que", "debo ",
                "anota", "apunta", "toma nota", "guarda una nota")) {
            return true;
        }

        // Natural shorthand such as “llamar a York mañana a las siete de la noche”
        // is accepted only when it contains both a calendar cue and a time cue.
        boolean dateCue = text.matches(".*\\b(hoy|manana|lunes|martes|miercoles|jueves|viernes|sabado|domingo)\\b.*")
                || NUMERIC_DATE.matcher(text).find() || MONTH_DATE.matcher(text).find();
        boolean timeCue = TIME_AT.matcher(text).find() || TIME_WORD.matcher(text).find()
                || TIME_DAYPART.matcher(text).find() || TIME_COLON.matcher(text).find()
                || TIME_AMPM.matcher(text).find();
        return dateCue && timeCue && text.trim().split("\\s+").length >= 3;
    }

    private static int parseSnoozeMinutes(String text) {
        if (text.contains("media hora")) return 30;
        if (text.contains("una hora")) return 60;
        if (text.contains("dos horas")) return 120;
        Matcher m = SNOOZE_MINUTES.matcher(text);
        if (m.find()) return clamp(safeInt(m.group(1), 0), 1, 1440);
        m = SNOOZE_HOURS.matcher(text);
        if (m.find()) return clamp(safeInt(m.group(1), 0) * 60, 1, 1440);
        return 0;
    }

    private static String cleanupTarget(String original, VoiceCommand.Action action) {
        String s = original == null ? "" : original.trim();
        s = s.replaceFirst("(?iu)^\\s*(oye\\s+)?(lyra|lira)[,.:]?\\s*", "");
        if (action == VoiceCommand.Action.COMPLETE) {
            s = s.replaceFirst("(?iu)^\\s*(marca|completa|termina)\\s+", "");
            s = s.replaceAll("(?iu)\\s+como\\s+hech[oa]\\s*$", "");
        } else if (action == VoiceCommand.Action.CANCEL) {
            s = s.replaceFirst("(?iu)^\\s*(cancela|elimina|borra|quita)\\s+", "");
        } else if (action == VoiceCommand.Action.SNOOZE) {
            s = s.replaceFirst("(?iu)^\\s*(pospone|aplaza|recu[eé]rdame\\s+de\\s+nuevo)\\s+", "");
            s = s.replaceAll("(?iu)\\b(?:por|durante)\\s+(?:\\d{1,3}\\s*(?:minutos?|min|horas?|h)|una\\s+hora|dos\\s+horas|media\\s+hora)\\b", " ");
            s = s.replaceAll("(?iu)\\b(?:\\d{1,3}\\s*(?:minutos?|min|horas?|h)|una\\s+hora|dos\\s+horas|media\\s+hora)\\s*$", " ");
        } else if (action == VoiceCommand.Action.RESCHEDULE) {
            s = s.replaceFirst("(?iu)^\\s*(mueve|reprograma|cambia)\\s+", "");
            s = removeDatesAndTimes(s);
            s = s.replaceAll("(?iu)\\b(pasado\\s+mañana|pasado\\s+manana|hoy|mañana|manana|lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)\\b", " ");
            s = s.replaceAll("(?iu)\\b(para|al|a)\\s*$", " ");
        }
        return s.replaceAll("\\s+", " ").trim();
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
                if (numeric.group(3) == null && isBeforeToday(target, now)) target.add(Calendar.YEAR, 1);
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
                if (monthDate.group(3) == null && isBeforeToday(target, now)) target.add(Calendar.YEAR, 1);
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

        m = TIME_DAYPART.matcher(text);
        if (m.find()) {
            int hour = safeInt(m.group(1), -1);
            int minute = m.group(2) == null ? 0 : safeInt(m.group(2), 0);
            return normalizeTime(hour, minute, null, m.group(3), false);
        }

        m = TIME_COLON.matcher(text);
        if (m.find()) return normalizeTime(safeInt(m.group(1), -1), safeInt(m.group(2), -1), m.group(3), null, false);
        m = TIME_AMPM.matcher(text);
        if (m.find()) return normalizeTime(safeInt(m.group(1), -1), 0, m.group(2), null, false);
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
            // 1–12 without a daypart is genuinely ambiguous. Do not silently decide AM/PM.
            ambiguous = ambiguous && hour >= 1 && hour <= 12;
        }
        return new TimeResult(true, hour, minute, ambiguous);
    }

    private static boolean looksLikeAppointment(String text) {
        return containsAny(text, "cita", "reunion", "reunirme", "agenda", "agendame", "consulta", "entrevista");
    }

    static String cleanupTitle(String original, String kind) {
        String s = original == null ? "" : original.trim();
        if (s.isEmpty()) return "";

        String named = extractExplicitName(s);
        if (!named.isEmpty()) {
            if ("Cita".equals(kind)) return "Cita con " + titleCasePerson(named);
            return capitalizeFirst(named);
        }

        s = s.replaceFirst("(?iu)^\\s*(oye\\s+lyra[,.:]?\\s*|oye\\s+lira[,.:]?\\s*|lyra[,.:]?\\s*|lira[,.:]?\\s*)", "");
        s = s.replaceFirst("(?iu)^\\s*(por\\s+favor\\s+)?(quisiera|quiero|necesito|podr[ií]as?|puedes)\\s+", "");
        s = s.replaceFirst("(?iu)^\\s*(que\\s+)?", "");

        if ("Nota".equals(kind)) {
            s = s.replaceFirst("(?iu)^\\s*(anota|apunta|toma\\s+nota(?:\\s+de)?|guarda\\s+una\\s+nota(?:\\s+que)?|nota)\\s*", "");
            s = s.replaceFirst("(?iu)^\\s*que\\s+", "");
            s = s.replaceAll("\\s+", " ").trim();
            return capitalizeFirst(s);
        }

        if ("Tarea".equals(kind)) {
            s = s.replaceFirst("(?iu)^\\s*(crea(?:r)?\\s+una\\s+tarea|ponme\\s+una\\s+tarea|tarea|pendiente)\\s*", "");
            s = removeDatesAndTimes(s);
            s = removeDayWords(s);
            s = s.replaceFirst("(?iu)^\\s*(tengo\\s+que|debo)\\s+", "");
            s = s.replaceAll("\\s+", " ").trim();
            return capitalizeFirst(s);
        }

        s = s.replaceFirst("(?iu)^\\s*(recu[eé]rdame|recordarme|ponme\\s+un\\s+recordatorio|pon\\s+un\\s+recordatorio|crea(?:r)?\\s+un\\s+recordatorio|agrega(?:r)?|a[nñ]ade(?:r)?|ag[eé]ndame|agendar|agenda|programa(?:r)?|crea(?:r)?)\\s*", "");

        if ("Cita".equals(kind)) {
            s = s.replaceFirst("(?iu)^\\s*(una?\\s+)?cita\\s*", "");
            s = s.replaceFirst("(?iu)^\\s*una\\s+(reuni[oó]n|consulta|entrevista)\\s+", "$1 ");
            s = s.replaceFirst("(?iu)^\\s*para\\s+con\\s+el\\s+nombre\\s+de\\s+", "con ");
            s = s.replaceFirst("(?iu)^\\s*con\\s+el\\s+nombre\\s+de\\s+", "con ");
            s = s.replaceFirst("(?iu)^\\s*para\\s+con\\s+", "con ");
        } else {
            s = s.replaceFirst("(?iu)^\\s*(un\\s+)?recordatorio\\s*", "");
            s = s.replaceFirst("(?iu)^\\s*(?:para\\s+)?con\\s+(?:el\\s+)?nombre\\s+de\\s+", "");
            s = s.replaceFirst("(?iu)^\\s*que\\s+se\\s+llame\\s+", "");
        }

        s = removeDatesAndTimes(s);
        s = removeDayWords(s);
        s = s.replaceAll("\\s+", " ").trim();
        s = s.replaceFirst("(?iu)^(el|la|para|de)\\s+", "").trim();
        s = s.replaceAll("(?iu)\\s+(el|a|para)$", "").trim();
        if (s.isEmpty()) return "";

        if ("Cita".equals(kind)) {
            String lower = s.toLowerCase(Locale.ROOT);
            if (lower.startsWith("con ")) s = "Cita con " + titleCasePerson(s.substring(4));
            else if (lower.startsWith("reuni")) s = capitalizeFirst(s);
            else s = "Cita: " + capitalizeFirst(s);
        } else {
            s = capitalizeFirst(s);
        }
        return s;
    }

    private static String extractExplicitName(String original) {
        if (original == null || original.trim().isEmpty()) return "";
        Matcher m = Pattern.compile("(?iu)\\b(?:con\\s+(?:el\\s+)?nombre\\s+de|con\\s+nombre\\s+de|que\\s+se\\s+llame|llamad[oa]|t[ií]tulo)\\s+(.+)$")
                .matcher(original.trim());
        if (!m.find()) return "";
        String value = m.group(1);
        value = removeDatesAndTimes(value);
        value = removeDayWords(value);
        value = value.replaceAll("(?iu)\\b(?:para|a)\\s*$", " ")
                .replaceAll("[,.!?;:]+$", " ")
                .replaceAll("\\s+", " ").trim();
        return value;
    }

    private static String removeDayWords(String s) {
        return s.replaceAll("(?iu)\\b(pasado\\s+mañana|pasado\\s+manana|hoy|mañana|manana|lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)\\b", " ");
    }

    private static String removeDatesAndTimes(String s) {
        s = s.replaceAll("(?iu)\\b(?:el\\s+)?\\d{1,2}\\s+de\\s+(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre)(?:\\s+de\\s+\\d{4})?\\b", " ");
        s = s.replaceAll("\\b\\d{1,2}[/-]\\d{1,2}(?:[/-]\\d{2,4})?\\b", " ");
        s = s.replaceAll("(?iu)\\b(?:a\\s+las?|para\\s+las?|a\\s+la|para\\s+la)\\s+(?:\\d{1,2}(?::\\d{2})?|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce)(?:\\s+y\\s+(?:media|cuarto))?\\s+de\\s+la\\s+(?:mañana|manana|tarde|noche)\\b", " ");
        s = s.replaceAll("(?iu)\\b(?:a\\s+las?|para\\s+las?|a\\s+la|para\\s+la)\\s+(?:\\d{1,2}(?::\\d{2})?|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce)(?:\\s+y\\s+(?:media|cuarto))?\\s*(?:a\\.?\\s*m\\.?|p\\.?\\s*m\\.?|am|pm)?\\b", " ");
        s = s.replaceAll("(?iu)\\b\\d{1,2}:\\d{2}\\s*(?:am|pm)?\\b", " ");
        s = s.replaceAll("(?iu)\\b\\d{1,2}\\s*(?:am|pm)\\b", " ");
        return s;
    }

    private static String titleCasePerson(String value) {
        String[] parts = value.trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            String lower = p.toLowerCase(Locale.ROOT);
            boolean connector = i > 0 && (lower.equals("de") || lower.equals("del") || lower.equals("la") || lower.equals("las") || lower.equals("los") || lower.equals("y"));
            if (out.length() > 0) out.append(' ');
            out.append(connector ? lower : capitalizeFirst(p));
        }
        return out.toString();
    }

    private static String capitalizeFirst(String value) {
        String s = value == null ? "" : value.trim();
        if (s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static String normalizeForIntent(String input) {
        return normalize(input == null ? "" : input);
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

    private static boolean startsWithAny(String text, String... values) {
        for (String value : values) if (text.startsWith(value)) return true;
        return false;
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

    private static boolean isBeforeToday(Calendar a, Calendar b) {
        Calendar aa = (Calendar) a.clone();
        Calendar bb = (Calendar) b.clone();
        aa.set(Calendar.HOUR_OF_DAY, 0); aa.set(Calendar.MINUTE, 0); aa.set(Calendar.SECOND, 0); aa.set(Calendar.MILLISECOND, 0);
        bb.set(Calendar.HOUR_OF_DAY, 0); bb.set(Calendar.MINUTE, 0); bb.set(Calendar.SECOND, 0); bb.set(Calendar.MILLISECOND, 0);
        return aa.before(bb);
    }

    private static boolean validDate(int day, int month, int year) {
        if (year < 2020 || year > 2200 || month < 0 || month > 11 || day < 1 || day > 31) return false;
        Calendar c = Calendar.getInstance();
        c.setLenient(false);
        c.clear();
        c.set(year, month, day);
        try { c.getTime(); return true; } catch (IllegalArgumentException e) { return false; }
    }

    private static int safeInt(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (Exception e) { return fallback; }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
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
