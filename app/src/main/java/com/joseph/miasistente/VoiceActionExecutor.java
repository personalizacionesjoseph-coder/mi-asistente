package com.joseph.miasistente;

import android.content.Context;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class VoiceActionExecutor {
    private VoiceActionExecutor() {}

    public static class Result {
        public final boolean success;
        public final String message;
        Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    public static boolean resolveTarget(EventDatabase db, VoiceCommand command) {
        if (command == null || db == null) return false;
        if (!requiresTarget(command.action)) return true;
        ReminderItem target = db.findBestActiveMatch(command.targetQuery, System.currentTimeMillis());
        if (target == null) return false;
        command.targetId = target.id;
        return true;
    }

    public static boolean requiresTarget(VoiceCommand.Action action) {
        return action == VoiceCommand.Action.COMPLETE || action == VoiceCommand.Action.CANCEL
                || action == VoiceCommand.Action.RESCHEDULE || action == VoiceCommand.Action.SNOOZE;
    }

    public static String confirmationSummary(EventDatabase db, VoiceCommand command) {
        if (command == null) return "No entendí la acción.";
        if (command.action == VoiceCommand.Action.CREATE) {
            if (!command.hasTime) return "Crear " + command.kind.toLowerCase(Locale.ROOT) + ": " + command.title;
            return "Crear " + command.kind.toLowerCase(Locale.ROOT) + ": " + command.title + ", "
                    + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, new Locale("es", "ES"))
                    .format(new Date(command.eventTime));
        }
        ReminderItem target = db == null ? null : db.get(command.targetId);
        String title = target == null ? command.targetQuery : target.title;
        if (command.action == VoiceCommand.Action.COMPLETE) return "Marcar como hecho: " + title;
        if (command.action == VoiceCommand.Action.CANCEL) return "Eliminar: " + title;
        if (command.action == VoiceCommand.Action.RESCHEDULE) return "Mover " + title + " a "
                + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, new Locale("es", "ES"))
                .format(new Date(command.eventTime));
        if (command.action == VoiceCommand.Action.SNOOZE) return "Posponer " + title + " " + snoozeText(command.snoozeMinutes);
        return title;
    }

    public static Result execute(Context context, EventDatabase db, VoiceCommand command) {
        if (context == null || db == null || command == null) return new Result(false, "No pude ejecutar esa acción.");

        if (command.action == VoiceCommand.Action.REMEMBER) {
            if (command.memoryFact == null || command.memoryFact.trim().isEmpty()) return new Result(false, "No recibí nada para recordar.");
            db.addMemory(command.memoryFact);
            return new Result(true, "Listo. Recordaré que " + command.memoryFact + ".");
        }

        if (command.action == VoiceCommand.Action.CREATE) {
            ReminderItem item = new ReminderItem();
            item.kind = command.kind;
            item.title = command.title;
            item.notes = "";
            item.hasTime = command.hasTime;
            item.eventTime = command.hasTime ? command.eventTime : 0;
            item.remindMinutes = command.hasTime ? AppPrefs.defaultReminderMinutes(context) : -1;
            item.id = db.save(item);
            AlarmScheduler.schedule(context, item);
            boolean calendarSaved = item.canSyncToCalendar() && CalendarBridge.saveToSelectedCalendar(context, db, item);
            return new Result(true, "Listo. Guardé " + item.title + (calendarSaved ? " y lo añadí a Google Calendar." : "."));
        }

        ReminderItem target = db.get(command.targetId);
        if (target == null) return new Result(false, "Ese pendiente ya no existe.");

        if (command.action == VoiceCommand.Action.COMPLETE) {
            db.markCompleted(target.id, true);
            AlarmScheduler.cancel(context, target.id);
            return new Result(true, "Hecho. Marqué " + target.title + " como completado.");
        }
        if (command.action == VoiceCommand.Action.CANCEL) {
            AlarmScheduler.cancel(context, target.id);
            if (target.calendarEventId > 0) CalendarBridge.deleteLinkedEvent(context, target);
            db.delete(target.id);
            return new Result(true, "Listo. Eliminé " + target.title + ".");
        }
        if (command.action == VoiceCommand.Action.RESCHEDULE) {
            target.hasTime = true;
            target.eventTime = command.eventTime;
            target.completed = false;
            if (target.remindMinutes < 0 && !"Nota".equals(target.kind)) target.remindMinutes = AppPrefs.defaultReminderMinutes(context);
            db.save(target);
            AlarmScheduler.schedule(context, target);
            boolean calendarSaved = target.canSyncToCalendar() && CalendarBridge.saveToSelectedCalendar(context, db, target);
            String when = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, new Locale("es", "ES"))
                    .format(new Date(target.eventTime));
            return new Result(true, "Listo. Moví " + target.title + " a " + when + (calendarSaved ? " y actualicé Google Calendar." : "."));
        }
        if (command.action == VoiceCommand.Action.SNOOZE) {
            AlarmScheduler.snooze(context, target.id, command.snoozeMinutes);
            return new Result(true, "Listo. Te avisaré de " + target.title + " en " + snoozeText(command.snoozeMinutes) + ".");
        }
        return new Result(false, "No pude ejecutar esa acción.");
    }

    public static String agendaForDay(EventDatabase db, int dayOffset, String label) {
        Calendar start = Calendar.getInstance();
        start.add(Calendar.DAY_OF_YEAR, dayOffset);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_YEAR, 1);
        long from = dayOffset == 0 ? Math.max(System.currentTimeMillis(), start.getTimeInMillis()) : start.getTimeInMillis();
        List<ReminderItem> items = db.between(from, end.getTimeInMillis());
        if (items.isEmpty()) {
            if (dayOffset == 0 && !db.activeTasks().isEmpty()) return "No tienes eventos con hora para hoy, pero sí tienes tareas pendientes.";
            return "No tienes eventos con hora para " + label + ".";
        }
        StringBuilder text = new StringBuilder("Para ").append(label).append(" tienes ")
                .append(items.size()).append(items.size() == 1 ? " pendiente. " : " pendientes. ");
        int limit = Math.min(items.size(), 4);
        for (int i = 0; i < limit; i++) {
            ReminderItem item = items.get(i);
            text.append(item.title).append(" a las ")
                    .append(DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(item.eventTime))).append(". ");
        }
        if (items.size() > limit) text.append("Y ").append(items.size() - limit).append(" más.");
        return text.toString();
    }

    public static String nextEventText(EventDatabase db) {
        ReminderItem item = db.nextAfter(System.currentTimeMillis());
        if (item == null) return "No tienes eventos próximos con hora.";
        String when = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(item.eventTime));
        return "Lo próximo es " + item.title + ", " + when + ".";
    }

    private static String snoozeText(int minutes) {
        if (minutes == 60) return "1 hora";
        if (minutes % 60 == 0) return (minutes / 60) + " horas";
        return minutes + " minutos";
    }
}
