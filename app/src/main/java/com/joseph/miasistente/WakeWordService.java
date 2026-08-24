package com.joseph.miasistente;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class WakeWordService extends Service {
    private static volatile boolean running = false;
    public static final String ACTION_STOP = "com.joseph.miasistente.STOP_WAKE";
    private static final String CHANNEL_ID = "lyra_wake_word";
    private static final int NOTIFICATION_ID = 22001;

    private enum Mode { WAIT_WAKE, WAIT_COMMAND, WAIT_FOLLOWUP, WAIT_CONFIRM }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean destroyed = false;
    private Mode mode = Mode.WAIT_WAKE;
    private String pendingVoiceText = "";
    private VoiceCommand pendingCommand;
    private long ignoreWakeEchoUntil = 0L;
    private long ignoreRecognizerErrorsUntil = 0L;

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        createChannel();
        startAsForeground();
        AppPrefs.setWakeWordEnabled(this, true);
        initTts();

        if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            AppPrefs.setWakeWordEnabled(this, false);
            stopSelf();
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            AppPrefs.setWakeWordEnabled(this, false);
            stopSelf();
            return;
        }
        scheduleWakeListening(600);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            AppPrefs.setWakeWordEnabled(this, false);
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        running = false;
        AppPrefs.setWakeWordEnabled(this, false);
        mainHandler.removeCallbacksAndMessages(null);
        destroyRecognizer();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        super.onDestroy();
    }

    public static boolean isRunning() {
        return running;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startAsForeground() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(
                this, 22002, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stop = new Intent(this, WakeWordService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(
                this, 22003, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_mic)
                .setContentTitle("Lyra está escuchando")
                .setContentText("Di “Lyra” para activar el asistente · modo experimental")
                .setContentIntent(openPending)
                .addAction(new Notification.Action.Builder(R.drawable.ic_mic, "Detener", stopPending).build())
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void createChannel() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Activación por voz de Lyra", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Mantiene activo el modo experimental para detectar la palabra Lyra.");
        channel.setSound(null, null);
        manager.createNotificationChannel(channel);
    }

    private void initTts() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(new Locale("es", "ES"));
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED;
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String utteranceId) {}
                    @Override public void onError(String utteranceId) { onSpeechDone(utteranceId); }
                    @Override public void onDone(String utteranceId) { onSpeechDone(utteranceId); }
                });
            }
        });
    }

    private void onSpeechDone(String utteranceId) {
        if (destroyed || utteranceId == null) return;
        if (utteranceId.startsWith("listen:")) {
            mainHandler.postDelayed(this::startRecognitionForCurrentMode, 250);
        } else if (utteranceId.startsWith("wake:")) {
            scheduleWakeListening(350);
        }
    }

    private void speakThenListen(String text) {
        ignoreRecognizerErrorsUntil = System.currentTimeMillis() + 600L;
        destroyRecognizer();
        if (ttsReady && tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "listen:" + System.currentTimeMillis());
        } else {
            mainHandler.postDelayed(this::startRecognitionForCurrentMode, 700);
        }
    }

    private void speakThenWake(String text) {
        ignoreRecognizerErrorsUntil = System.currentTimeMillis() + 600L;
        destroyRecognizer();
        if (ttsReady && tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "wake:" + System.currentTimeMillis());
        } else {
            scheduleWakeListening(900);
        }
    }

    private void scheduleWakeListening(long delayMs) {
        mode = Mode.WAIT_WAKE;
        mainHandler.postDelayed(this::startRecognitionForCurrentMode, delayMs);
    }

    private void startRecognitionForCurrentMode() {
        if (destroyed) return;
        if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            AppPrefs.setWakeWordEnabled(this, false);
            stopSelf();
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            AppPrefs.setWakeWordEnabled(this, false);
            stopSelf();
            return;
        }

        destroyRecognizer();
        try {
            recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
        } catch (RuntimeException e) {
            scheduleWakeListening(1200);
            return;
        }
        recognizer.setRecognitionListener(new Listener());

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        try {
            recognizer.startListening(intent);
        } catch (RuntimeException e) {
            scheduleWakeListening(900);
        }
    }

    private void destroyRecognizer() {
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Exception ignored) {}
            try { recognizer.destroy(); } catch (Exception ignored) {}
            recognizer = null;
        }
    }

    private boolean containsWakeWord(String text) {
        String n = VoiceCommandParser.normalizeForIntent(text);
        return n.matches(".*\\blyra\\b.*");
    }

    private void activateAssistant() {
        mode = Mode.WAIT_COMMAND;
        pendingVoiceText = "";
        pendingCommand = null;
        ignoreWakeEchoUntil = System.currentTimeMillis() + 1200L;
        speakThenListen("Te escucho.");
    }

    private void handleCommandText(String spoken) {
        if (spoken == null || spoken.trim().isEmpty()) {
            repeatCurrentPrompt();
            return;
        }
        String normalized = VoiceCommandParser.normalizeForIntent(spoken);
        if (containsAny(normalized, "cancelar", "cancela", "olvidalo", "olvida eso")) {
            pendingVoiceText = "";
            pendingCommand = null;
            speakThenWake("De acuerdo. Cancelado.");
            return;
        }

        if (mode == Mode.WAIT_CONFIRM) {
            if (containsAny(normalized, "si", "guardar", "guardalo", "confirmar", "confirmo", "dale")) {
                savePendingCommand();
            } else if (containsAny(normalized, "no", "cancelar", "cancela")) {
                pendingCommand = null;
                pendingVoiceText = "";
                speakThenWake("De acuerdo. No lo guardé.");
            } else {
                speakThenListen("Di guardar o cancelar.");
            }
            return;
        }

        String merged = pendingVoiceText.isEmpty() ? spoken.trim() : pendingVoiceText + " " + spoken.trim();
        long parseNow = System.currentTimeMillis();
        merged = UserContextResolver.enrich(this, merged, parseNow);
        VoiceCommand command = VoiceCommandParser.parse(merged, parseNow);

        if (command.action == VoiceCommand.Action.QUERY_TODAY) {
            pendingVoiceText = "";
            speakThenWake(agendaForDay(0, "hoy"));
            return;
        }
        if (command.action == VoiceCommand.Action.QUERY_TOMORROW) {
            pendingVoiceText = "";
            speakThenWake(agendaForDay(1, "mañana"));
            return;
        }
        if (command.action == VoiceCommand.Action.QUERY_NEXT) {
            pendingVoiceText = "";
            speakThenWake(nextEventText());
            return;
        }
        if (command.action != VoiceCommand.Action.CREATE) {
            speakThenListen("No entendí la instrucción. Inténtalo otra vez.");
            return;
        }
        if (!command.issue.isEmpty()) {
            pendingVoiceText = "";
            pendingCommand = null;
            speakThenWake(command.issue);
            return;
        }

        pendingVoiceText = merged;
        pendingCommand = command;
        if (command.missingTitle) {
            mode = Mode.WAIT_FOLLOWUP;
            speakThenListen("¿Qué nombre quieres ponerle?");
            return;
        }
        if (command.missingDate) {
            mode = Mode.WAIT_FOLLOWUP;
            speakThenListen("¿Para qué día?");
            return;
        }
        if (command.missingTime) {
            mode = Mode.WAIT_FOLLOWUP;
            speakThenListen("¿A qué hora?");
            return;
        }

        mode = Mode.WAIT_CONFIRM;
        String summary = command.title + ", " + DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM, DateFormat.SHORT, new Locale("es", "ES")).format(new Date(command.eventTime));
        speakThenListen(summary + ". ¿Lo guardo? Di guardar o cancelar.");
    }

    private void repeatCurrentPrompt() {
        if (mode == Mode.WAIT_CONFIRM) speakThenListen("Di guardar o cancelar.");
        else if (pendingCommand != null && pendingCommand.missingTitle) speakThenListen("¿Qué nombre quieres ponerle?");
        else if (pendingCommand != null && pendingCommand.missingDate) speakThenListen("¿Para qué día?");
        else if (pendingCommand != null && pendingCommand.missingTime) speakThenListen("¿A qué hora?");
        else speakThenListen("No te entendí. ¿Qué necesitas?");
    }

    private void savePendingCommand() {
        VoiceCommand command = pendingCommand;
        pendingCommand = null;
        pendingVoiceText = "";
        if (command == null || command.eventTime <= System.currentTimeMillis()) {
            speakThenWake("No pude guardar ese evento. Vuelve a intentarlo.");
            return;
        }

        EventDatabase db = new EventDatabase(getApplicationContext());
        ReminderItem item = new ReminderItem();
        item.kind = command.kind;
        item.title = command.title;
        item.notes = "";
        item.eventTime = command.eventTime;
        item.remindMinutes = AppPrefs.defaultReminderMinutes(this);
        item.id = db.save(item);
        AlarmScheduler.schedule(this, item);
        boolean calendarSaved = CalendarBridge.saveToSelectedCalendar(this, db, item);
        db.close();

        String confirmation = "Listo. Guardé " + item.title;
        if (calendarSaved) confirmation += " y lo añadí a Google Calendar";
        confirmation += ".";
        speakThenWake(confirmation);
    }

    private String agendaForDay(int dayOffset, String label) {
        EventDatabase db = new EventDatabase(getApplicationContext());
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
        db.close();
        if (items.isEmpty()) return "No tienes nada pendiente para " + label + ".";

        StringBuilder text = new StringBuilder("Para ").append(label).append(" tienes ")
                .append(items.size()).append(items.size() == 1 ? " pendiente. " : " pendientes. ");
        int limit = Math.min(items.size(), 3);
        for (int i = 0; i < limit; i++) {
            ReminderItem item = items.get(i);
            text.append(item.title).append(" a las ")
                    .append(DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(item.eventTime))).append(". ");
        }
        return text.toString();
    }

    private String nextEventText() {
        EventDatabase db = new EventDatabase(getApplicationContext());
        ReminderItem item = db.nextAfter(System.currentTimeMillis());
        db.close();
        if (item == null) return "No tienes eventos próximos.";
        String when = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(item.eventTime));
        return "Lo próximo es " + item.title + ", " + when + ".";
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }

    private class Listener implements RecognitionListener {
        @Override public void onReadyForSpeech(Bundle params) {}
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() {}

        @Override
        public void onError(int error) {
            if (destroyed) return;
            if (System.currentTimeMillis() < ignoreRecognizerErrorsUntil) return;
            if (mode == Mode.WAIT_WAKE) {
                scheduleWakeListening(error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ? 1200 : 650);
            } else if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH) {
                repeatCurrentPrompt();
            } else {
                speakThenWake("Tuve un problema con el reconocimiento de voz. Inténtalo de nuevo en unos segundos.");
            }
        }

        @Override
        public void onResults(Bundle results) {
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches == null || matches.isEmpty()) {
                if (mode == Mode.WAIT_WAKE) scheduleWakeListening(500); else repeatCurrentPrompt();
                return;
            }
            String first = matches.get(0);
            if (mode == Mode.WAIT_WAKE) {
                if (containsWakeWord(first)) activateAssistant();
                else scheduleWakeListening(300);
            } else {
                if (System.currentTimeMillis() < ignoreWakeEchoUntil && containsWakeWord(first)) return;
                handleCommandText(first);
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            if (mode != Mode.WAIT_WAKE) return;
            ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty() && containsWakeWord(matches.get(0))) {
                activateAssistant();
            }
        }

        @Override public void onEvent(int eventType, Bundle params) {}
    }
}
