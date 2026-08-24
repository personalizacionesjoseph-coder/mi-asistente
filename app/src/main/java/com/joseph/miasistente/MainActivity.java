package com.joseph.miasistente;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_NOTIFICATIONS = 40;
    private static final int REQ_AUDIO = 41;

    private EventDatabase db;
    private LinearLayout agendaContainer;
    private LinearLayout attentionContainer;
    private TextView heroNext;
    private TextView heroStats;
    private TextView voiceStatus;
    private ImageButton micButton;
    private String appearanceSignature;

    private SpeechRecognizer speechRecognizer;
    private boolean usingOnDeviceRecognizer = false;
    private boolean fallbackTried = false;
    private TextToSpeech textToSpeech;
    private boolean ttsReady = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String pendingVoiceText = "";
    private VoiceCommand pendingVoiceCommand;
    private VoiceCommand pendingConfirmation;
    private AlertDialog voiceConfirmDialog;
    private boolean autoStartFromAssist = false;
    private boolean manualVoiceSession = false;
    private long voiceFinishDeadline = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        Ui.configureBars(this);
        db = new EventDatabase(this);
        NotificationHelper.ensureChannel(this);
        appearanceSignature = AppPrefs.appearanceSignature(this);
        buildUi();
        initTextToSpeech();
        requestNotificationPermissionIfNeeded();
        autoStartFromAssist = Intent.ACTION_ASSIST.equals(getIntent().getAction());
    }

    @Override
    protected void onResume() {
        super.onResume();
        String currentSignature = AppPrefs.appearanceSignature(this);
        if (appearanceSignature != null && !appearanceSignature.equals(currentSignature)) {
            recreate();
            return;
        }
        refreshDashboard();
        pullCalendarChanges();
        ensureWakeServiceIfEnabled();
        mainHandler.postDelayed(() -> {
            if (voiceStatus != null && pendingVoiceText.isEmpty() && pendingConfirmation == null) {
                voiceStatus.setText(defaultVoiceHint());
            }
        }, 250);
        if (autoStartFromAssist) {
            autoStartFromAssist = false;
            mainHandler.postDelayed(this::startVoiceRecognition, 400);
        }
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        if (voiceConfirmDialog != null) voiceConfirmDialog.dismiss();
        destroySpeechRecognizer();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        if (manualVoiceSession) resumeWakeService();
        if (db != null) db.close();
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(36));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addHeader(root);
        addTodayHero(root);

        List<String> order = AppPrefs.homeOrder(this);
        for (String section : order) {
            if (!AppPrefs.isSectionVisible(this, section)) continue;
            if (AppPrefs.SECTION_VOICE.equals(section)) addVoiceSection(root);
            else if (AppPrefs.SECTION_QUICK.equals(section)) addQuickSection(root);
            else if (AppPrefs.SECTION_AGENDA.equals(section)) addAgendaSection(root);
        }

        setContentView(scroll);
    }

    private void addHeader(LinearLayout root) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);

        TextView eyebrow = new TextView(this);
        String preferred = AppPrefs.preferredName(this);
        String greetingText = greeting() + (preferred.isEmpty() ? "" : ", " + preferred);
        eyebrow.setText(greetingText.toUpperCase(Locale.getDefault()));
        eyebrow.setTextSize(11);
        eyebrow.setLetterSpacing(0.08f);
        eyebrow.setTextColor(Ui.PRIMARY);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        copy.addView(eyebrow);

        TextView title = new TextView(this);
        title.setText("Hoy con Lyra");
        title.setTextSize(29);
        title.setTextColor(Ui.TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        copy.addView(title);

        TextView date = new TextView(this);
        date.setText(new SimpleDateFormat("EEEE, d 'de' MMMM", new Locale("es", "ES")).format(new Date()));
        date.setTextSize(13);
        date.setTextColor(Ui.MUTED);
        date.setPadding(0, dp(2), 0, 0);
        copy.addView(date);
        header.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageButton settings = new ImageButton(this);
        settings.setImageResource(R.drawable.ic_settings);
        settings.setColorFilter(Ui.TEXT);
        settings.setPadding(dp(13), dp(13), dp(13), dp(13));
        settings.setBackground(Ui.roundedStroke(Ui.SURFACE, Ui.BORDER, 1, 18, this));
        settings.setContentDescription("Configuración y apariencia");
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        header.addView(settings, new LinearLayout.LayoutParams(dp(50), dp(50)));
        root.addView(header);
    }

    private void addTodayHero(LinearLayout root) {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(18), dp(17), dp(18), dp(17));
        hero.setBackground(Ui.gradient(Ui.PRIMARY, Ui.PRIMARY_DARK, 24, this));
        LinearLayout.LayoutParams heroLp = matchWrap();
        heroLp.topMargin = dp(18);
        root.addView(hero, heroLp);

        TextView label = new TextView(this);
        label.setText("SIGUIENTE");
        label.setTextSize(10);
        label.setLetterSpacing(0.09f);
        label.setTextColor(Color.argb(220, 255, 255, 255));
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        hero.addView(label);

        heroNext = new TextView(this);
        heroNext.setTextSize(20);
        heroNext.setTextColor(Color.WHITE);
        heroNext.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heroNext.setPadding(0, dp(7), 0, dp(5));
        hero.addView(heroNext);

        heroStats = new TextView(this);
        heroStats.setTextSize(13);
        heroStats.setTextColor(Color.argb(225, 255, 255, 255));
        hero.addView(heroStats);
    }

    private void addVoiceSection(LinearLayout root) {
        LinearLayout voiceCard = new LinearLayout(this);
        voiceCard.setOrientation(LinearLayout.HORIZONTAL);
        voiceCard.setGravity(Gravity.CENTER_VERTICAL);
        voiceCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        voiceCard.setBackground(Ui.roundedStroke(Ui.SURFACE, Ui.BORDER, 1, 22, this));
        root.addView(voiceCard, sectionParams());

        micButton = new ImageButton(this);
        micButton.setImageResource(R.drawable.ic_mic);
        micButton.setColorFilter(Color.WHITE);
        micButton.setPadding(dp(16), dp(16), dp(16), dp(16));
        micButton.setBackground(Ui.rounded(Ui.PRIMARY, 26, this));
        micButton.setContentDescription("Hablar con Lyra");
        micButton.setOnClickListener(v -> startVoiceRecognition());
        voiceCard.addView(micButton, new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(14), 0, 0, 0);

        TextView title = new TextView(this);
        title.setText("¿Qué necesitas?");
        title.setTextSize(17);
        title.setTextColor(Ui.TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        copy.addView(title);

        voiceStatus = new TextView(this);
        voiceStatus.setText(defaultVoiceHint());
        voiceStatus.setTextSize(13);
        voiceStatus.setTextColor(Ui.MUTED);
        voiceStatus.setPadding(0, dp(3), 0, 0);
        copy.addView(voiceStatus);
        voiceCard.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }

    private void addQuickSection(LinearLayout root) {
        TextView section = Ui.label(this, "CAPTURA RÁPIDA");
        section.setPadding(0, dp(22), 0, dp(9));
        root.addView(section);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(row1, matchWrap());
        addQuickButton(row1, "Cita", R.drawable.ic_calendar, "Cita", false);
        addQuickButton(row1, "Recordatorio", R.drawable.ic_reminder, "Recordatorio", true);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams row2Lp = matchWrap();
        row2Lp.topMargin = dp(9);
        root.addView(row2, row2Lp);
        addQuickButton(row2, "Tarea", R.drawable.ic_task, "Tarea", false);
        addQuickButton(row2, "Nota", R.drawable.ic_note, "Nota", true);
    }

    private void addQuickButton(LinearLayout row, String label, int icon, String kind, boolean second) {
        Button button = new Button(this);
        button.setText(label);
        Ui.styleSecondaryButton(button);
        setButtonIcon(button, icon, Ui.PRIMARY);
        button.setGravity(Gravity.CENTER);
        button.setOnClickListener(v -> openNew(kind));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(56), 1f);
        if (second) lp.leftMargin = dp(9);
        row.addView(button, lp);
    }

    private void addAgendaSection(LinearLayout root) {
        TextView section = Ui.label(this, "TU DÍA");
        section.setPadding(0, dp(22), 0, dp(9));
        root.addView(section);

        attentionContainer = new LinearLayout(this);
        attentionContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(attentionContainer, matchWrap());

        agendaContainer = new LinearLayout(this);
        agendaContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams agendaLp = matchWrap();
        agendaLp.topMargin = dp(10);
        root.addView(agendaContainer, agendaLp);

        refreshDashboard();
    }

    private void refreshDashboard() {
        refreshHero();
        refreshAttention();
        refreshAgenda();
    }

    private void refreshHero() {
        if (heroNext == null || heroStats == null) return;
        ReminderItem next = db.nextAfter(System.currentTimeMillis());
        if (next == null) {
            heroNext.setText("Tu agenda está despejada");
        } else {
            heroNext.setText(next.title + " · " + relativeWhen(next.eventTime));
        }

        Calendar end = startOfTomorrow();
        int today = db.between(System.currentTimeMillis(), end.getTimeInMillis()).size();
        int tasks = db.activeTasks().size();
        int unscheduled = db.activeUnscheduled().size();
        String stats = countText(today, "evento hoy", "eventos hoy") + " · "
                + countText(tasks, "tarea pendiente", "tareas pendientes");
        if (unscheduled > 0) stats += " · " + unscheduled + " sin hora";
        heroStats.setText(stats);
    }

    private void refreshAttention() {
        if (attentionContainer == null) return;
        attentionContainer.removeAllViews();
        List<ReminderItem> unscheduled = db.activeUnscheduled();
        List<ReminderItem> attention = new ArrayList<>();
        for (ReminderItem item : unscheduled) {
            if (!"Nota".equals(item.kind)) attention.add(item);
            if (attention.size() >= 3) break;
        }
        if (attention.isEmpty()) return;

        TextView title = new TextView(this);
        title.setText("Necesita atención");
        title.setTextSize(19);
        title.setTextColor(Ui.TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        attentionContainer.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Pendientes sin fecha u hora");
        subtitle.setTextSize(13);
        subtitle.setTextColor(Ui.MUTED);
        subtitle.setPadding(0, dp(2), 0, dp(8));
        attentionContainer.addView(subtitle);

        for (ReminderItem item : attention) attentionContainer.addView(itemCard(item, true), cardLayoutParams(true));
    }

    private void refreshAgenda() {
        if (agendaContainer == null) return;
        agendaContainer.removeAllViews();

        Calendar endToday = startOfTomorrow();
        List<ReminderItem> today = db.between(System.currentTimeMillis(), endToday.getTimeInMillis());
        List<ReminderItem> upcoming = db.upcoming(endToday.getTimeInMillis());
        List<ReminderItem> notes = db.recentNotes(2);

        TextView heading = new TextView(this);
        heading.setText("Agenda de hoy");
        heading.setTextSize(22);
        heading.setTextColor(Ui.TEXT);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        agendaContainer.addView(heading);

        if (today.isEmpty()) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setPadding(dp(18), dp(18), dp(18), dp(18));
            empty.setBackground(Ui.roundedStroke(Ui.SURFACE, Ui.BORDER, 1, 20, this));
            TextView emptyTitle = new TextView(this);
            emptyTitle.setText("Nada pendiente con hora para hoy");
            emptyTitle.setTextSize(15);
            emptyTitle.setTextColor(Ui.TEXT);
            emptyTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            TextView emptyBody = new TextView(this);
            emptyBody.setText("Puedes crear algo o pedírselo a Lyra por voz.");
            emptyBody.setTextSize(13);
            emptyBody.setTextColor(Ui.MUTED);
            emptyBody.setPadding(0, dp(4), 0, 0);
            empty.addView(emptyTitle);
            empty.addView(emptyBody);
            agendaContainer.addView(empty, cardLayoutParams(true));
        } else {
            for (ReminderItem item : today) agendaContainer.addView(itemCard(item, false), cardLayoutParams(true));
        }

        if (!upcoming.isEmpty()) {
            TextView nextTitle = Ui.label(this, "PRÓXIMOS");
            nextTitle.setPadding(0, dp(20), 0, dp(8));
            agendaContainer.addView(nextTitle);
            int limit = Math.min(5, upcoming.size());
            for (int i = 0; i < limit; i++) agendaContainer.addView(itemCard(upcoming.get(i), false), cardLayoutParams(i > 0));
        }

        if (!notes.isEmpty()) {
            TextView notesTitle = Ui.label(this, "NOTAS RECIENTES");
            notesTitle.setPadding(0, dp(20), 0, dp(8));
            agendaContainer.addView(notesTitle);
            for (int i = 0; i < notes.size(); i++) agendaContainer.addView(itemCard(notes.get(i), false), cardLayoutParams(i > 0));
        }
    }

    private View itemCard(ReminderItem item, boolean attention) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(15), dp(13), dp(12), dp(13));
        card.setBackground(Ui.roundedStroke(attention ? Ui.PRIMARY_SOFT : Ui.SURFACE, Ui.BORDER, 1, 19, this));
        card.setOnClickListener(v -> openEdit(item.id));
        card.setOnLongClickListener(v -> {
            confirmDelete(item);
            return true;
        });

        TextView icon = new TextView(this);
        icon.setText(kindGlyph(item.kind));
        icon.setGravity(Gravity.CENTER);
        icon.setTextSize(17);
        icon.setTextColor(Ui.PRIMARY);
        icon.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        icon.setBackground(Ui.rounded(Ui.PRIMARY_SOFT, 18, this));
        card.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, dp(8), 0);
        TextView title = new TextView(this);
        title.setText(item.title);
        title.setTextSize(15);
        title.setTextColor(Ui.TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        copy.addView(title);

        TextView meta = new TextView(this);
        String metaText;
        if (item.isScheduled()) metaText = item.kind + " · " + relativeWhen(item.eventTime);
        else metaText = item.kind + " · sin fecha";
        meta.setText(metaText);
        meta.setTextSize(12);
        meta.setTextColor(Ui.MUTED);
        meta.setPadding(0, dp(3), 0, 0);
        copy.addView(meta);
        card.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if ("Tarea".equals(item.kind)) {
            Button done = new Button(this);
            done.setText("✓");
            done.setContentDescription("Marcar tarea como hecha");
            done.setTextColor(Ui.PRIMARY);
            done.setTextSize(18);
            done.setBackground(Ui.rounded(Ui.PRIMARY_SOFT, 18, this));
            done.setOnClickListener(v -> {
                db.markCompleted(item.id, true);
                AlarmScheduler.cancel(this, item.id);
                refreshDashboard();
            });
            card.addView(done, new LinearLayout.LayoutParams(dp(42), dp(42)));
        }
        return card;
    }

    private String kindGlyph(String kind) {
        if ("Cita".equals(kind)) return "C";
        if ("Tarea".equals(kind)) return "T";
        if ("Nota".equals(kind)) return "N";
        return "R";
    }

    private void openNew(String kind) {
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra("prefill_kind", kind);
        intent.putExtra("prefill_has_time", "Cita".equals(kind) || "Recordatorio".equals(kind));
        startActivity(intent);
    }

    private void openEdit(long id) {
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra("reminder_id", id);
        startActivity(intent);
    }

    private void confirmDelete(ReminderItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar " + item.kind.toLowerCase(Locale.ROOT))
                .setMessage("¿Quieres eliminar “" + item.title + "”?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar", (d, w) -> {
                    AlarmScheduler.cancel(this, item.id);
                    if (item.calendarEventId > 0) CalendarBridge.deleteLinkedEvent(this, item);
                    db.delete(item.id);
                    refreshDashboard();
                })
                .show();
    }

    private void startVoiceRecognition() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showTemporaryVoiceMessage("Este teléfono no tiene un servicio de reconocimiento de voz disponible.");
            return;
        }
        pauseWakeService();
        manualVoiceSession = true;
        fallbackTried = false;
        boolean canUseOnDevice = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this);
        startVoiceRecognitionInternal(canUseOnDevice);
    }

    private void startVoiceRecognitionInternal(boolean onDevice) {
        destroySpeechRecognizer();
        usingOnDeviceRecognizer = onDevice;
        try {
            speechRecognizer = onDevice
                    ? SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
                    : SpeechRecognizer.createSpeechRecognizer(this);
        } catch (RuntimeException e) {
            if (onDevice && !fallbackTried) {
                fallbackTried = true;
                startVoiceRecognitionInternal(false);
                return;
            }
            finishVoiceSession();
            showTemporaryVoiceMessage("No pude iniciar el reconocimiento de voz.");
            return;
        }
        speechRecognizer.setRecognitionListener(new AssistantRecognitionListener());
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognitionLanguage());
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        if (onDevice) intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        try {
            setListeningUi(true, "Te escucho…");
            speechRecognizer.startListening(intent);
        } catch (RuntimeException e) {
            if (onDevice && !fallbackTried) {
                fallbackTried = true;
                startVoiceRecognitionInternal(false);
            } else {
                finishVoiceSession();
                showTemporaryVoiceMessage("El micrófono está ocupado. Inténtalo de nuevo.");
            }
        }
    }

    private void destroySpeechRecognizer() {
        if (speechRecognizer != null) {
            try { speechRecognizer.cancel(); } catch (Exception ignored) {}
            try { speechRecognizer.destroy(); } catch (Exception ignored) {}
            speechRecognizer = null;
        }
    }

    private String recognitionLanguage() {
        Locale locale = Locale.getDefault();
        if ("es".equalsIgnoreCase(locale.getLanguage())) return locale.toLanguageTag();
        return "es-ES";
    }

    private void handleVoiceText(String spoken) {
        if (spoken == null || spoken.trim().isEmpty()) {
            finishVoiceSession();
            showTemporaryVoiceMessage("No escuché una instrucción.");
            return;
        }
        String normalized = VoiceCommandParser.normalizeForIntent(spoken);

        if (pendingConfirmation != null) {
            if (isAffirmative(normalized)) {
                VoiceCommand command = pendingConfirmation;
                clearVoiceConfirmation();
                executeVoiceCommand(command);
            } else if (containsAny(normalized, "editar", "cambiar antes")) {
                VoiceCommand command = pendingConfirmation;
                clearVoiceConfirmation();
                if (command.action == VoiceCommand.Action.CREATE) openVoiceCommandInEditor(command);
                else if (command.targetId > 0) openEdit(command.targetId);
                finishVoiceSession();
            } else if (isNegativeConfirmation(normalized)) {
                clearVoiceConfirmation();
                pendingVoiceText = "";
                pendingVoiceCommand = null;
                showTemporaryVoiceMessage("De acuerdo. Cancelado.");
                speak("De acuerdo. Cancelado.");
                finishVoiceSessionAfterSpeech();
            } else {
                promptAndListen("Di confirmar, editar o cancelar.");
            }
            return;
        }

        if (containsAny(normalized, "cancelar", "cancela", "olvidalo", "olvida eso") && !pendingVoiceText.isEmpty()) {
            pendingVoiceText = "";
            pendingVoiceCommand = null;
            showTemporaryVoiceMessage("De acuerdo. Cancelado.");
            speak("De acuerdo. Cancelado.");
            finishVoiceSessionAfterSpeech();
            return;
        }

        String merged = pendingVoiceText.isEmpty() ? spoken.trim() : pendingVoiceText + " " + spoken.trim();
        long parseNow = System.currentTimeMillis();
        merged = UserContextResolver.enrich(this, merged, parseNow);
        VoiceCommand command = VoiceCommandParser.parse(merged, parseNow);

        if (command.action == VoiceCommand.Action.QUERY_TODAY) {
            pendingVoiceText = "";
            pendingVoiceCommand = null;
            speakAgendaForDay(0, "hoy");
            finishVoiceSessionAfterSpeech();
            return;
        }
        if (command.action == VoiceCommand.Action.QUERY_TOMORROW) {
            pendingVoiceText = "";
            pendingVoiceCommand = null;
            speakAgendaForDay(1, "mañana");
            finishVoiceSessionAfterSpeech();
            return;
        }
        if (command.action == VoiceCommand.Action.QUERY_NEXT) {
            pendingVoiceText = "";
            pendingVoiceCommand = null;
            speakNextEvent();
            finishVoiceSessionAfterSpeech();
            return;
        }
        if (command.action == VoiceCommand.Action.UNKNOWN) {
            promptAndListen("No entendí la instrucción. Inténtalo de otra forma.");
            return;
        }
        if (!command.issue.isEmpty()) {
            pendingVoiceText = "";
            pendingVoiceCommand = null;
            showTemporaryVoiceMessage(command.issue);
            speak(command.issue);
            finishVoiceSessionAfterSpeech();
            return;
        }

        if (command.action == VoiceCommand.Action.REMEMBER) {
            db.addMemory(command.memoryFact);
            String message = "Listo. Recordaré que " + command.memoryFact + ".";
            voiceStatus.setText(message);
            speak(message);
            pendingVoiceText = "";
            pendingVoiceCommand = null;
            finishVoiceSessionAfterSpeech();
            return;
        }

        pendingVoiceText = merged;
        pendingVoiceCommand = command;
        if (command.missingTitle) {
            promptAndListen("¿Qué nombre quieres ponerle?");
            return;
        }
        if (command.missingDate) {
            promptAndListen(command.action == VoiceCommand.Action.RESCHEDULE ? "¿Para qué día quieres moverlo?" : "¿Para qué día?");
            return;
        }
        if (command.missingTime) {
            promptAndListen(command.timeWasAmbiguous ? "¿De la mañana o de la tarde?" : "¿A qué hora?");
            return;
        }

        pendingVoiceText = "";
        pendingVoiceCommand = null;

        if (requiresTarget(command.action)) {
            ReminderItem target = db.findBestActiveMatch(command.targetQuery, System.currentTimeMillis());
            if (target == null) {
                String message = "No encontré un pendiente que coincida con “" + command.targetQuery + "”.";
                showTemporaryVoiceMessage(message);
                speak(message);
                finishVoiceSessionAfterSpeech();
                return;
            }
            command.targetId = target.id;
            if (command.action == VoiceCommand.Action.SNOOZE) {
                executeVoiceCommand(command);
                return;
            }
        }

        showVoiceConfirmation(command);
    }

    private boolean requiresTarget(VoiceCommand.Action action) {
        return action == VoiceCommand.Action.COMPLETE || action == VoiceCommand.Action.CANCEL
                || action == VoiceCommand.Action.RESCHEDULE || action == VoiceCommand.Action.SNOOZE;
    }

    private void showVoiceConfirmation(VoiceCommand command) {
        pendingConfirmation = command;
        String summary = confirmationSummary(command);
        if (voiceStatus != null) voiceStatus.setText(summary);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Lyra entendió")
                .setMessage(summary)
                .setNegativeButton("Cancelar", (d, w) -> {
                    clearVoiceConfirmation();
                    finishVoiceSession();
                })
                .setNeutralButton("Editar", (d, w) -> {
                    VoiceCommand edit = command;
                    clearVoiceConfirmation();
                    if (edit.action == VoiceCommand.Action.CREATE) openVoiceCommandInEditor(edit);
                    else if (edit.targetId > 0) openEdit(edit.targetId);
                    finishVoiceSession();
                })
                .setPositiveButton("Confirmar", (d, w) -> {
                    VoiceCommand execute = command;
                    clearVoiceConfirmation();
                    executeVoiceCommand(execute);
                });
        voiceConfirmDialog = builder.create();
        voiceConfirmDialog.setOnDismissListener(d -> {
            if (voiceConfirmDialog != null && !voiceConfirmDialog.isShowing()) voiceConfirmDialog = null;
        });
        voiceConfirmDialog.show();
        promptAndListen(summary + ". ¿Confirmas? Di confirmar, editar o cancelar.");
    }

    private String confirmationSummary(VoiceCommand command) {
        if (command.action == VoiceCommand.Action.CREATE) {
            if (!command.hasTime) return "Crear " + command.kind.toLowerCase(Locale.ROOT) + ": " + command.title;
            return "Crear " + command.kind.toLowerCase(Locale.ROOT) + ": " + command.title + ", "
                    + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, new Locale("es", "ES"))
                    .format(new Date(command.eventTime));
        }
        ReminderItem target = db.get(command.targetId);
        String targetTitle = target == null ? command.targetQuery : target.title;
        if (command.action == VoiceCommand.Action.COMPLETE) return "Marcar como hecho: " + targetTitle;
        if (command.action == VoiceCommand.Action.CANCEL) return "Eliminar: " + targetTitle;
        if (command.action == VoiceCommand.Action.RESCHEDULE) return "Mover " + targetTitle + " a "
                + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, new Locale("es", "ES"))
                .format(new Date(command.eventTime));
        return targetTitle;
    }

    private void executeVoiceCommand(VoiceCommand command) {
        String message;
        boolean calendarSaved = false;

        if (command.action == VoiceCommand.Action.CREATE) {
            ReminderItem item = new ReminderItem();
            item.kind = command.kind;
            item.title = command.title;
            item.notes = "";
            item.hasTime = command.hasTime;
            item.eventTime = command.hasTime ? command.eventTime : 0;
            item.remindMinutes = command.hasTime ? AppPrefs.defaultReminderMinutes(this) : -1;
            item.id = db.save(item);
            AlarmScheduler.schedule(this, item);
            if (item.canSyncToCalendar()) calendarSaved = CalendarBridge.saveToSelectedCalendar(this, db, item);
            message = "Listo. Guardé " + item.title + (calendarSaved ? " y lo añadí a Google Calendar." : ".");
        } else {
            ReminderItem target = db.get(command.targetId);
            if (target == null) {
                message = "Ese pendiente ya no existe.";
            } else if (command.action == VoiceCommand.Action.COMPLETE) {
                db.markCompleted(target.id, true);
                AlarmScheduler.cancel(this, target.id);
                message = "Hecho. Marqué " + target.title + " como completado.";
            } else if (command.action == VoiceCommand.Action.CANCEL) {
                AlarmScheduler.cancel(this, target.id);
                if (target.calendarEventId > 0) CalendarBridge.deleteLinkedEvent(this, target);
                db.delete(target.id);
                message = "Listo. Eliminé " + target.title + ".";
            } else if (command.action == VoiceCommand.Action.RESCHEDULE) {
                target.hasTime = true;
                target.eventTime = command.eventTime;
                target.completed = false;
                if (target.remindMinutes < 0 && !"Nota".equals(target.kind)) target.remindMinutes = AppPrefs.defaultReminderMinutes(this);
                db.save(target);
                AlarmScheduler.schedule(this, target);
                if (target.canSyncToCalendar()) calendarSaved = CalendarBridge.saveToSelectedCalendar(this, db, target);
                message = "Listo. Moví " + target.title + " a " + relativeWhen(target.eventTime)
                        + (calendarSaved ? " y actualicé Google Calendar." : ".");
            } else if (command.action == VoiceCommand.Action.SNOOZE) {
                AlarmScheduler.snooze(this, target.id, command.snoozeMinutes);
                message = "Listo. Te avisaré de " + target.title + " en " + snoozeText(command.snoozeMinutes) + ".";
            } else {
                message = "No pude ejecutar esa acción.";
            }
        }

        refreshDashboard();
        if (voiceStatus != null) voiceStatus.setText(message);
        speak(message);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        finishVoiceSessionAfterSpeech();
    }

    private String snoozeText(int minutes) {
        if (minutes == 60) return "1 hora";
        if (minutes % 60 == 0) return (minutes / 60) + " horas";
        return minutes + " minutos";
    }

    private void promptAndListen(String prompt) {
        setListeningUi(false, prompt);
        speak(prompt, true);
    }

    private void clearVoiceConfirmation() {
        pendingConfirmation = null;
        if (voiceConfirmDialog != null) {
            AlertDialog dialog = voiceConfirmDialog;
            voiceConfirmDialog = null;
            if (dialog.isShowing()) dialog.dismiss();
        }
        destroySpeechRecognizer();
    }

    private void openVoiceCommandInEditor(VoiceCommand command) {
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra("prefill_kind", command.kind);
        intent.putExtra("prefill_title", command.title);
        intent.putExtra("prefill_time", command.eventTime);
        intent.putExtra("prefill_has_time", command.hasTime);
        startActivity(intent);
    }

    private void speakAgendaForDay(int dayOffset, String label) {
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
            String message = "No tienes eventos con hora para " + label + ".";
            if (dayOffset == 0 && !db.activeTasks().isEmpty()) message += " Sí tienes tareas pendientes.";
            if (voiceStatus != null) voiceStatus.setText(message);
            speak(message);
            return;
        }

        StringBuilder speech = new StringBuilder("Para ").append(label).append(" tienes ")
                .append(items.size()).append(items.size() == 1 ? " pendiente. " : " pendientes. ");
        int limit = Math.min(items.size(), 4);
        for (int i = 0; i < limit; i++) {
            ReminderItem item = items.get(i);
            String at = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(item.eventTime));
            speech.append(item.title).append(" a las ").append(at).append(". ");
        }
        if (items.size() > limit) speech.append("Y ").append(items.size() - limit).append(" más.");
        if (voiceStatus != null) voiceStatus.setText(speech.toString());
        speak(speech.toString());
    }

    private void speakNextEvent() {
        ReminderItem item = db.nextAfter(System.currentTimeMillis());
        String message;
        if (item == null) message = "No tienes eventos próximos con hora.";
        else message = "Lo próximo es " + item.title + ", "
                + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(item.eventTime)) + ".";
        if (voiceStatus != null) voiceStatus.setText(message);
        speak(message);
    }

    private void initTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(new Locale("es", "ES"));
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED;
                textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String utteranceId) {}
                    @Override public void onError(String utteranceId) { handleTtsDone(utteranceId); }
                    @Override public void onDone(String utteranceId) { handleTtsDone(utteranceId); }
                });
            }
        });
    }

    private void handleTtsDone(String utteranceId) {
        if (utteranceId == null) return;
        if (utteranceId.startsWith("followup:")) {
            mainHandler.postDelayed(this::startVoiceRecognitionContinuation, 250);
        } else if (utteranceId.startsWith("finish:")) {
            mainHandler.post(this::finishVoiceSession);
        }
    }

    private void speak(String text) {
        speak(text, false);
    }

    private void speak(String text, boolean listenAfter) {
        if (!AppPrefs.voiceRepliesEnabled(this)) {
            if (listenAfter) mainHandler.postDelayed(this::startVoiceRecognitionContinuation, 300);
            return;
        }
        if (ttsReady && textToSpeech != null) {
            String id = (listenAfter ? "followup:" : "assistant:") + System.currentTimeMillis();
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);
        } else if (listenAfter) {
            mainHandler.postDelayed(this::startVoiceRecognitionContinuation, 600);
        }
    }

    private void finishVoiceSessionAfterSpeech() {
        voiceFinishDeadline = System.currentTimeMillis() + 9000L;
        mainHandler.postDelayed(this::finishAfterSpeechCheck, 120);
    }

    private void finishAfterSpeechCheck() {
        if (!manualVoiceSession) return;
        boolean speaking = AppPrefs.voiceRepliesEnabled(this) && ttsReady && textToSpeech != null && textToSpeech.isSpeaking();
        if (speaking && System.currentTimeMillis() < voiceFinishDeadline) {
            mainHandler.postDelayed(this::finishAfterSpeechCheck, 250);
            return;
        }
        voiceFinishDeadline = 0L;
        finishVoiceSession();
    }

    private void startVoiceRecognitionContinuation() {
        if (!manualVoiceSession) {
            pauseWakeService();
            manualVoiceSession = true;
        }
        fallbackTried = false;
        boolean canUseOnDevice = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this);
        startVoiceRecognitionInternal(canUseOnDevice);
    }

    private void finishVoiceSession() {
        voiceFinishDeadline = 0L;
        destroySpeechRecognizer();
        setListeningUi(false, defaultVoiceHint());
        if (manualVoiceSession) {
            manualVoiceSession = false;
            resumeWakeService();
        }
    }

    private void setListeningUi(boolean listening, String message) {
        if (voiceStatus != null) voiceStatus.setText(message);
        if (micButton != null) {
            micButton.setBackground(Ui.rounded(listening ? Ui.LISTENING : Ui.PRIMARY, 26, this));
            micButton.setColorFilter(Color.WHITE);
            micButton.setEnabled(!listening);
            micButton.setAlpha(listening ? 0.88f : 1f);
        }
    }

    private void showTemporaryVoiceMessage(String message) {
        setListeningUi(false, message);
        mainHandler.postDelayed(() -> {
            if (voiceStatus != null && message.contentEquals(voiceStatus.getText())
                    && pendingVoiceText.isEmpty() && pendingConfirmation == null) {
                voiceStatus.setText(defaultVoiceHint());
            }
        }, 4200);
    }

    private String defaultVoiceHint() {
        if (AppPrefs.wakeWordEnabled(this)) {
            return WakeWordService.isRunning() ? "Lyra activa · di “Lyra” o toca para hablar" : "Activación pendiente · toca para hablar";
        }
        return "Toca para hablar con Lyra";
    }

    private void pauseWakeService() {
        if (!WakeWordService.isRunning()) return;
        try {
            startService(new Intent(this, WakeWordService.class).setAction(WakeWordService.ACTION_PAUSE));
        } catch (RuntimeException ignored) {}
    }

    private void resumeWakeService() {
        if (!AppPrefs.wakeWordEnabled(this) || !WakeWordService.isRunning()) return;
        try {
            startService(new Intent(this, WakeWordService.class).setAction(WakeWordService.ACTION_RESUME));
        } catch (RuntimeException ignored) {}
    }

    private void ensureWakeServiceIfEnabled() {
        if (!AppPrefs.wakeWordEnabled(this) || WakeWordService.isRunning()) return;
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return;
        try {
            startForegroundService(new Intent(this, WakeWordService.class));
        } catch (RuntimeException ignored) {}
    }

    private void pullCalendarChanges() {
        if (!AppPrefs.calendarSyncEnabled(this) || !CalendarBridge.hasPermissions(this)) return;
        new Thread(() -> {
            EventDatabase syncDb = new EventDatabase(getApplicationContext());
            CalendarBridge.SyncResult result = CalendarBridge.pullLinkedChanges(getApplicationContext(), syncDb);
            syncDb.close();
            if (result.pulled + result.removed > 0) runOnUiThread(this::refreshDashboard);
        }).start();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this)
                    .setTitle("Permitir recordatorios")
                    .setMessage("Lyra necesita permiso para mostrar avisos y acciones rápidas cuando llegue un recordatorio.")
                    .setNegativeButton("Ahora no", null)
                    .setPositiveButton("Permitir", (d, w) -> requestPermissions(
                            new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS))
                    .show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startVoiceRecognition();
            else showTemporaryVoiceMessage("Sin permiso de micrófono no puedo recibir instrucciones por voz.");
        }
    }

    private void setButtonIcon(Button button, int drawableRes, int tint) {
        Drawable icon = getDrawable(drawableRes);
        if (icon == null) return;
        icon.setTint(tint);
        int size = dp(19);
        icon.setBounds(0, 0, size, size);
        button.setCompoundDrawables(icon, null, null, null);
        button.setCompoundDrawablePadding(dp(8));
    }

    private Calendar startOfTomorrow() {
        Calendar end = Calendar.getInstance();
        end.add(Calendar.DAY_OF_YEAR, 1);
        end.set(Calendar.HOUR_OF_DAY, 0);
        end.set(Calendar.MINUTE, 0);
        end.set(Calendar.SECOND, 0);
        end.set(Calendar.MILLISECOND, 0);
        return end;
    }

    private String relativeWhen(long millis) {
        Calendar event = Calendar.getInstance();
        event.setTimeInMillis(millis);
        Calendar today = Calendar.getInstance();
        String time = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(millis));
        if (sameDay(event, today)) return "hoy " + time;
        Calendar tomorrow = (Calendar) today.clone();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        if (sameDay(event, tomorrow)) return "mañana " + time;
        return new SimpleDateFormat("d MMM · h:mm a", new Locale("es", "ES")).format(new Date(millis));
    }

    private boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private String greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) return "Buenos días";
        if (hour < 19) return "Buenas tardes";
        return "Buenas noches";
    }

    private String countText(int count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
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

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams sectionParams() {
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = dp(18);
        return lp;
    }

    private LinearLayout.LayoutParams cardLayoutParams(boolean withTopMargin) {
        LinearLayout.LayoutParams lp = matchWrap();
        if (withTopMargin) lp.topMargin = dp(9);
        return lp;
    }

    private int dp(int value) {
        return Ui.dp(this, value);
    }

    private class AssistantRecognitionListener implements RecognitionListener {
        @Override public void onReadyForSpeech(Bundle params) { setListeningUi(true, "Te escucho…"); }
        @Override public void onBeginningOfSpeech() { setListeningUi(true, "Te escucho…"); }
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() { if (voiceStatus != null) voiceStatus.setText("Procesando…"); }

        @Override
        public void onError(int error) {
            if (usingOnDeviceRecognizer && !fallbackTried &&
                    (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED || error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
                            || error == SpeechRecognizer.ERROR_CLIENT)) {
                fallbackTried = true;
                startVoiceRecognitionInternal(false);
                return;
            }
            String message;
            if (error == SpeechRecognizer.ERROR_NO_MATCH) message = "No pude entenderte. Inténtalo otra vez.";
            else if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) message = "No escuché nada.";
            else if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) message = "Necesito permiso de micrófono para escucharte.";
            else if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) message = "El micrófono está ocupado. Espera un momento.";
            else message = "El reconocimiento de voz no respondió.";
            finishVoiceSession();
            showTemporaryVoiceMessage(message);
        }

        @Override
        public void onResults(Bundle results) {
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            setListeningUi(false, "Procesando…");
            if (matches == null || matches.isEmpty()) {
                finishVoiceSession();
                showTemporaryVoiceMessage("No pude entenderte. Inténtalo otra vez.");
                return;
            }
            handleVoiceText(matches.get(0));
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty() && voiceStatus != null) voiceStatus.setText("“" + matches.get(0) + "”");
        }

        @Override public void onEvent(int eventType, Bundle params) {}
    }
}
