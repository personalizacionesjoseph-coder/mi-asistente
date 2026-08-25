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

import java.util.ArrayList;
import java.util.Locale;

public class WakeWordService extends Service {
    public static final String ACTION_STOP = "com.joseph.miasistente.STOP_WAKE";
    public static final String ACTION_PAUSE = "com.joseph.miasistente.PAUSE_WAKE";
    public static final String ACTION_RESUME = "com.joseph.miasistente.RESUME_WAKE";

    private static volatile boolean running = false;
    private static final String CHANNEL_ID = "lyra_wake_word";
    private static final int NOTIFICATION_ID = 22001;

    private enum Mode { WAIT_WAKE, WAIT_COMMAND, WAIT_FOLLOWUP, WAIT_CONFIRM }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean destroyed = false;
    private boolean paused = false;
    private boolean stoppedByUser = false;
    private boolean usingOnDevice = false;
    private boolean triedFallback = false;
    private int retryCount = 0;
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
        startAsForeground("Lyra activa", "Di “Lyra” para comenzar");
        initTts();

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            AppPrefs.setWakeWordEnabled(this, false);
            stopSelf();
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            AppPrefs.setWakeWordEnabled(this, false);
            stopSelf();
            return;
        }
        scheduleWakeListening(500);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_STOP.equals(action)) {
                stoppedByUser = true;
                AppPrefs.setWakeWordEnabled(this, false);
                stopSelf();
                return START_NOT_STICKY;
            }
            if (ACTION_PAUSE.equals(action)) {
                paused = true;
                destroyRecognizer();
                updateForeground("Lyra activa", "Pausada mientras usas el micrófono");
                return START_NOT_STICKY;
            }
            if (ACTION_RESUME.equals(action)) {
                if (!AppPrefs.wakeWordEnabled(this)) return START_NOT_STICKY;
                paused = false;
                retryCount = 0;
                updateForeground("Lyra activa", "Di “Lyra” para comenzar");
                scheduleWakeListening(250);
                return START_NOT_STICKY;
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        running = false;
        mainHandler.removeCallbacksAndMessages(null);
        destroyRecognizer();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        if (stoppedByUser) AppPrefs.setWakeWordEnabled(this, false);
        super.onDestroy();
    }

    public static boolean isRunning() {
        return running;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startAsForeground(String title, String text) {
        Notification notification = buildNotification(title, text);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updateForeground(String title, String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification(title, text));
    }

    private Notification buildNotification(String title, String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(
                this, 22002, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stop = new Intent(this, WakeWordService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(
                this, 22003, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(openPending)
                .addAction(new Notification.Action.Builder(R.drawable.ic_stop, "Detener", stopPending).build())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    private void createChannel() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Activación por voz de Lyra", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Mantiene disponible la activación por voz de Lyra.");
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
        if (destroyed || utteranceId == null || paused) return;
        if (utteranceId.startsWith("listen:")) mainHandler.postDelayed(this::startRecognitionForCurrentMode, 220);
        else if (utteranceId.startsWith("wake:")) scheduleWakeListening(300);
    }

    private void speakThenListen(String text) {
        ignoreRecognizerErrorsUntil = System.currentTimeMillis() + 700L;
        destroyRecognizer();
        updateForeground("Lyra te escucha", text);
        if (ttsReady && tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "listen:" + System.currentTimeMillis());
        } else {
            mainHandler.postDelayed(this::startRecognitionForCurrentMode, 650);
        }
    }

    private void speakThenWake(String text) {
        ignoreRecognizerErrorsUntil = System.currentTimeMillis() + 700L;
        destroyRecognizer();
        updateForeground("Lyra", text);
        if (ttsReady && tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "wake:" + System.currentTimeMillis());
        } else {
            scheduleWakeListening(850);
        }
    }

    private void scheduleWakeListening(long delayMs) {
        if (destroyed || paused) return;
        mode = Mode.WAIT_WAKE;
        mainHandler.postDelayed(this::startRecognitionForCurrentMode, delayMs);
    }

    private void startRecognitionForCurrentMode() {
        if (destroyed || paused) return;
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateForeground("Lyra", "Reconocimiento de voz no disponible");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            updateForeground("Lyra", "Falta permiso de micrófono");
            return;
        }

        destroyRecognizer();
        boolean onDeviceAvailable = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this);
        usingOnDevice = onDeviceAvailable && !triedFallback;
        try {
            recognizer = usingOnDevice
                    ? SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
                    : SpeechRecognizer.createSpeechRecognizer(this);
        } catch (RuntimeException e) {
            if (usingOnDevice) {
                triedFallback = true;
                scheduleRetry();
            } else {
                scheduleRetry();
            }
            return;
        }
        recognizer.setRecognitionListener(new Listener());

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognitionLanguage());
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        if (mode != Mode.WAIT_WAKE) {
            // Give the user enough time to dictate a complete natural instruction before
            // Android closes the utterance. Recognizer implementations may tune/ignore
            // these hints, but supported engines use them to avoid cutting long commands.
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 900L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1100L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1700L);
        }
        if (usingOnDevice) intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        try {
            recognizer.startListening(intent);
            if (mode == Mode.WAIT_WAKE) updateForeground("Lyra activa", "Di “Lyra” para comenzar");
        } catch (RuntimeException e) {
            scheduleRetry();
        }
    }

    private void scheduleRetry() {
        if (destroyed || paused) return;
        retryCount = Math.min(retryCount + 1, 6);
        long delay = Math.min(5000L, 350L * retryCount);
        mainHandler.postDelayed(this::startRecognitionForCurrentMode, delay);
    }

    private void destroyRecognizer() {
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Exception ignored) {}
            try { recognizer.destroy(); } catch (Exception ignored) {}
            recognizer = null;
        }
    }

    private boolean containsWakeWord(String text) {
        return VoiceTurnUtils.containsWakeWord(text);
    }

    private boolean anyResultContainsWakeWord(ArrayList<String> matches) {
        if (matches == null) return false;
        for (String match : matches) if (containsWakeWord(match)) return true;
        return false;
    }

    private String bestCommandAfterWakeWord(ArrayList<String> matches) {
        return VoiceTurnUtils.bestCommandAfterWakeWord(matches);
    }

    private String recognitionLanguage() {
        Locale locale = Locale.getDefault();
        if ("es".equalsIgnoreCase(locale.getLanguage())) return locale.toLanguageTag();
        return "es-ES";
    }

    private void activateAssistant() {
        mode = Mode.WAIT_COMMAND;
        pendingVoiceText = "";
        pendingCommand = null;
        ignoreWakeEchoUntil = System.currentTimeMillis() + 1200L;
        retryCount = 0;
        speakThenListen("Te escucho.");
    }

    private void handleCommandText(String spoken) {
        if (spoken == null || spoken.trim().isEmpty()) {
            repeatCurrentPrompt();
            return;
        }
        String normalized = VoiceCommandParser.normalizeForIntent(spoken);
        if (containsAny(normalized, "cancelar", "cancela", "olvidalo", "olvida eso") && mode != Mode.WAIT_CONFIRM) {
            pendingVoiceText = "";
            pendingCommand = null;
            speakThenWake("De acuerdo. Cancelado.");
            return;
        }

        if (mode == Mode.WAIT_CONFIRM) {
            if (isAffirmative(normalized)) {
                executePendingCommand();
            } else if (isNegativeConfirmation(normalized)) {
                pendingCommand = null;
                pendingVoiceText = "";
                speakThenWake("De acuerdo. No hice cambios.");
            } else {
                speakThenListen("Di confirmar o cancelar.");
            }
            return;
        }

        String merged = pendingVoiceText.isEmpty() ? spoken.trim() : pendingVoiceText + " " + spoken.trim();
        long parseNow = System.currentTimeMillis();
        merged = UserContextResolver.enrich(this, merged, parseNow);
        VoiceCommand command = VoiceCommandParser.parse(merged, parseNow);

        EventDatabase db = new EventDatabase(getApplicationContext());
        if (command.action == VoiceCommand.Action.QUERY_TODAY) {
            pendingVoiceText = "";
            String message = VoiceActionExecutor.agendaForDay(db, 0, "hoy");
            db.close();
            speakThenWake(message);
            return;
        }
        if (command.action == VoiceCommand.Action.QUERY_TOMORROW) {
            pendingVoiceText = "";
            String message = VoiceActionExecutor.agendaForDay(db, 1, "mañana");
            db.close();
            speakThenWake(message);
            return;
        }
        if (command.action == VoiceCommand.Action.QUERY_NEXT) {
            pendingVoiceText = "";
            String message = VoiceActionExecutor.nextEventText(db);
            db.close();
            speakThenWake(message);
            return;
        }
        if (command.action == VoiceCommand.Action.REMEMBER && command.issue.isEmpty()) {
            VoiceActionExecutor.Result result = VoiceActionExecutor.execute(this, db, command);
            db.close();
            pendingVoiceText = "";
            speakThenWake(result.message);
            return;
        }
        if (command.action == VoiceCommand.Action.UNKNOWN) {
            db.close();
            speakThenListen("Dime la instrucción completa, por ejemplo: agrega un recordatorio para mañana a las siete con el nombre de York.");
            return;
        }
        if (!command.issue.isEmpty()) {
            db.close();
            pendingVoiceText = "";
            pendingCommand = null;
            speakThenWake(command.issue);
            return;
        }

        pendingVoiceText = merged;
        pendingCommand = command;
        if (command.missingTitle) {
            db.close();
            mode = Mode.WAIT_FOLLOWUP;
            speakThenListen("¿Qué nombre quieres ponerle?");
            return;
        }
        if (command.missingDate) {
            db.close();
            mode = Mode.WAIT_FOLLOWUP;
            speakThenListen(command.action == VoiceCommand.Action.RESCHEDULE ? "¿Para qué día quieres moverlo?" : "¿Para qué día?");
            return;
        }
        if (command.missingTime) {
            db.close();
            mode = Mode.WAIT_FOLLOWUP;
            speakThenListen(command.timeWasAmbiguous ? "¿De la mañana o de la tarde?" : "¿A qué hora?");
            return;
        }

        pendingVoiceText = "";
        if (VoiceActionExecutor.requiresTarget(command.action)) {
            if (!VoiceActionExecutor.resolveTarget(db, command)) {
                db.close();
                pendingCommand = null;
                speakThenWake("No encontré un pendiente que coincida con " + command.targetQuery + ".");
                return;
            }
            if (command.action == VoiceCommand.Action.SNOOZE) {
                VoiceActionExecutor.Result result = VoiceActionExecutor.execute(this, db, command);
                db.close();
                pendingCommand = null;
                speakThenWake(result.message);
                return;
            }
        }

        pendingCommand = command;
        mode = Mode.WAIT_CONFIRM;
        String summary = VoiceActionExecutor.confirmationSummary(db, command);
        db.close();
        speakThenListen(summary + ". ¿Confirmas? Di confirmar o cancelar.");
    }

    private void executePendingCommand() {
        VoiceCommand command = pendingCommand;
        pendingCommand = null;
        pendingVoiceText = "";
        if (command == null) {
            speakThenWake("No tengo ninguna acción pendiente.");
            return;
        }
        EventDatabase db = new EventDatabase(getApplicationContext());
        VoiceActionExecutor.Result result = VoiceActionExecutor.execute(this, db, command);
        db.close();
        speakThenWake(result.message);
    }

    private void repeatCurrentPrompt() {
        if (mode == Mode.WAIT_CONFIRM) speakThenListen("Di confirmar o cancelar.");
        else if (pendingCommand != null && pendingCommand.missingTitle) speakThenListen("¿Qué nombre quieres ponerle?");
        else if (pendingCommand != null && pendingCommand.missingDate) speakThenListen("¿Para qué día?");
        else if (pendingCommand != null && pendingCommand.missingTime)
            speakThenListen(pendingCommand.timeWasAmbiguous ? "¿De la mañana o de la tarde?" : "¿A qué hora?");
        else speakThenListen("No te entendí. ¿Qué necesitas?");
    }

    private boolean isAffirmative(String text) {
        return containsWholeChoice(text, "si", "guardar", "guardalo", "confirmar", "confirmo", "dale", "hazlo");
    }

    private boolean isNegativeConfirmation(String text) {
        return containsWholeChoice(text, "no", "cancelar", "cancela", "olvidalo");
    }

    private boolean containsWholeChoice(String text, String... values) {
        String clean = (text == null ? "" : text).replaceAll("[^a-z0-9ñ ]", " ").replaceAll("\\s+", " ").trim();
        String padded = " " + clean + " ";
        for (String value : values) {
            if (padded.contains(" " + value + " ")) return true;
        }
        return false;
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
            if (destroyed || paused) return;
            if (System.currentTimeMillis() < ignoreRecognizerErrorsUntil) return;

            if (usingOnDevice && !triedFallback &&
                    (error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE || error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED
                            || error == SpeechRecognizer.ERROR_CLIENT)) {
                triedFallback = true;
                scheduleRetry();
                return;
            }

            if (mode == Mode.WAIT_WAKE) {
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    updateForeground("Lyra", "Falta permiso de micrófono");
                    return;
                }
                scheduleRetry();
            } else if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH) {
                repeatCurrentPrompt();
            } else if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                mainHandler.postDelayed(WakeWordService.this::startRecognitionForCurrentMode, 900);
            } else {
                speakThenWake("Tuve un problema con el reconocimiento de voz. Inténtalo de nuevo.");
            }
        }

        @Override
        public void onResults(Bundle results) {
            retryCount = 0;
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches == null || matches.isEmpty()) {
                if (mode == Mode.WAIT_WAKE) scheduleWakeListening(400); else repeatCurrentPrompt();
                return;
            }
            String first = matches.get(0);
            if (mode == Mode.WAIT_WAKE) {
                String embeddedCommand = bestCommandAfterWakeWord(matches);
                if (embeddedCommand == null) {
                    scheduleWakeListening(220);
                } else if (embeddedCommand.isEmpty()) {
                    // “Lyra” by itself opens a fresh command turn.
                    activateAssistant();
                } else {
                    // “Lyra, agrega un recordatorio…” works in one breath. Do not
                    // interrupt the user with “Te escucho” and force them to repeat it.
                    mode = Mode.WAIT_COMMAND;
                    pendingVoiceText = "";
                    pendingCommand = null;
                    retryCount = 0;
                    ignoreWakeEchoUntil = 0L;
                    updateForeground("Lyra", "Procesando tu instrucción…");
                    handleCommandText(embeddedCommand);
                }
            } else {
                if (System.currentTimeMillis() < ignoreWakeEchoUntil && anyResultContainsWakeWord(matches)) {
                    mainHandler.postDelayed(WakeWordService.this::startRecognitionForCurrentMode, 250);
                    return;
                }
                handleCommandText(first);
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            // Do not activate on a partial “Lyra”. The user may still be saying
            // “Lyra, agrega un recordatorio…”. Activating here used to cut the sentence
            // and made the next isolated word look like the reminder title. We wait for
            // the final utterance and then either process the whole command or prompt.
        }

        @Override public void onEvent(int eventType, Bundle params) {}
    }
}
