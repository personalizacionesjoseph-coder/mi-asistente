package com.joseph.miasistente;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
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
    private TextView agendaSubtitle;
    private TextView voiceStatus;
    private ImageButton micButton;
    private Button todayTab;
    private Button upcomingTab;
    private boolean showToday = true;
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
        refreshAgenda();
        pullCalendarChanges();
        ensureWakeServiceIfEnabled();
        if (autoStartFromAssist) {
            autoStartFromAssist = false;
            mainHandler.postDelayed(this::startVoiceRecognition, 450);
        }
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        if (voiceConfirmDialog != null) voiceConfirmDialog.dismiss();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        if (db != null) db.close();
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(34));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addHeader(root);

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
        title.setText("Lyra");
        title.setTextSize(30);
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

        Button settings = new Button(this);
        settings.setText("⚙");
        settings.setTextSize(21);
        settings.setTextColor(Ui.TEXT);
        settings.setPadding(0, 0, 0, 0);
        settings.setBackground(Ui.roundedStroke(Ui.SURFACE, Ui.BORDER, 1, 18, this));
        settings.setContentDescription("Configuración y apariencia");
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        header.addView(settings, new LinearLayout.LayoutParams(dp(50), dp(50)));
        root.addView(header);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setPadding(0, dp(16), 0, dp(2));
        statusRow.addView(statusChip("●  " + todayCount() + " hoy", Ui.PRIMARY_SOFT, Ui.PRIMARY));
        if (AppPrefs.wakeWordEnabled(this)) {
            TextView wakeChip = statusChip("◉  Di Lyra", Ui.SURFACE, Ui.PRIMARY);
            LinearLayout.LayoutParams wakeLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
            wakeLp.leftMargin = dp(8);
            statusRow.addView(wakeChip, wakeLp);
        }
        if (AppPrefs.calendarSyncEnabled(this) && AppPrefs.calendarId(this) > 0) {
            TextView calendarChip = statusChip("✓  Google Calendar", Ui.SURFACE, Ui.MUTED);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
            lp.leftMargin = dp(8);
            statusRow.addView(calendarChip, lp);
        }
        root.addView(statusRow);
    }

    private int todayCount() {
        Calendar end = Calendar.getInstance();
        end.add(Calendar.DAY_OF_YEAR, 1);
        end.set(Calendar.HOUR_OF_DAY, 0);
        end.set(Calendar.MINUTE, 0);
        end.set(Calendar.SECOND, 0);
        end.set(Calendar.MILLISECOND, 0);
        return db.between(System.currentTimeMillis(), end.getTimeInMillis()).size();
    }

    private TextView statusChip(String text, int bg, int fg) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextSize(12);
        chip.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        chip.setTextColor(fg);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(12), 0, dp(12), 0);
        chip.setBackground(Ui.roundedStroke(bg, Ui.BORDER, 1, 17, this));
        return chip;
    }

    private void addVoiceSection(LinearLayout root) {
        LinearLayout voiceCard = new LinearLayout(this);
        voiceCard.setOrientation(LinearLayout.VERTICAL);
        voiceCard.setGravity(Gravity.CENTER_HORIZONTAL);
        voiceCard.setPadding(dp(22), dp(24), dp(22), dp(22));
        voiceCard.setBackground(Ui.gradient(Ui.PRIMARY, Ui.PRIMARY_DARK, 28, this));
        voiceCard.setElevation(dp(3));
        root.addView(voiceCard, sectionParams());

        TextView badge = new TextView(this);
        badge.setText("ASISTENTE POR VOZ");
        badge.setTextSize(10);
        badge.setLetterSpacing(0.1f);
        badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        badge.setTextColor(Color.WHITE);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(12), dp(6), dp(12), dp(6));
        badge.setBackground(Ui.rounded(Color.argb(42, 255, 255, 255), 14, this));
        voiceCard.addView(badge);

        TextView ask = new TextView(this);
        ask.setText("¿Qué necesitas hoy?");
        ask.setTextSize(23);
        ask.setTextColor(Color.WHITE);
        ask.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        ask.setGravity(Gravity.CENTER);
        ask.setPadding(0, dp(14), 0, dp(5));
        voiceCard.addView(ask);

        TextView helper = new TextView(this);
        helper.setText(AppPrefs.wakeWordEnabled(this)
                ? "Di “Lyra” para activarme o toca el micrófono. Si falta un dato, te lo preguntaré."
                : "Dime una cita o recordatorio. Si falta fecha, hora o nombre, te lo preguntaré.");
        helper.setTextSize(13);
        helper.setTextColor(Color.argb(220, 255, 255, 255));
        helper.setGravity(Gravity.CENTER);
        helper.setPadding(dp(8), 0, dp(8), dp(18));
        voiceCard.addView(helper);

        micButton = new ImageButton(this);
        micButton.setImageResource(R.drawable.ic_mic);
        micButton.setColorFilter(Ui.PRIMARY);
        micButton.setBackground(Ui.rounded(Color.WHITE, 39, this));
        micButton.setPadding(dp(20), dp(20), dp(20), dp(20));
        micButton.setContentDescription("Hablar con Lyra");
        micButton.setOnClickListener(v -> startVoiceRecognition());
        micButton.setElevation(dp(4));
        voiceCard.addView(micButton, new LinearLayout.LayoutParams(dp(78), dp(78)));

        voiceStatus = new TextView(this);
        voiceStatus.setText("Ej.: “Quiero una cita con Yorsh” · Lyra preguntará lo que falte");
        voiceStatus.setTextSize(12);
        voiceStatus.setTextColor(Color.argb(220, 255, 255, 255));
        voiceStatus.setGravity(Gravity.CENTER);
        voiceStatus.setPadding(dp(4), dp(15), dp(4), 0);
        voiceCard.addView(voiceStatus, matchWrap());
    }

    private void addQuickSection(LinearLayout root) {
        TextView label = Ui.label(this, "ACCIONES RÁPIDAS");
        LinearLayout.LayoutParams labelLp = sectionParams();
        labelLp.bottomMargin = dp(9);
        root.addView(label, labelLp);

        LinearLayout quickRow = new LinearLayout(this);
        quickRow.setOrientation(LinearLayout.HORIZONTAL);

        Button newAppointment = new Button(this);
        newAppointment.setText("＋  Nueva cita");
        Ui.styleSecondaryButton(newAppointment);
        newAppointment.setOnClickListener(v -> quickCreate("Cita"));
        quickRow.addView(newAppointment, new LinearLayout.LayoutParams(0, dp(56), 1f));

        Button newReminder = new Button(this);
        newReminder.setText("⏰  Recordatorio");
        Ui.stylePrimaryButton(newReminder);
        newReminder.setOnClickListener(v -> quickCreate("Recordatorio"));
        LinearLayout.LayoutParams reminderLp = new LinearLayout.LayoutParams(0, dp(56), 1f);
        reminderLp.leftMargin = dp(10);
        quickRow.addView(newReminder, reminderLp);
        root.addView(quickRow);
    }

    private void addAgendaSection(LinearLayout root) {
        LinearLayout agendaHeader = new LinearLayout(this);
        agendaHeader.setOrientation(LinearLayout.VERTICAL);
        root.addView(agendaHeader, sectionParams());

        TextView agendaTitle = new TextView(this);
        agendaTitle.setText("Tu agenda");
        agendaTitle.setTextSize(23);
        agendaTitle.setTextColor(Ui.TEXT);
        agendaTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        agendaHeader.addView(agendaTitle);

        agendaSubtitle = new TextView(this);
        agendaSubtitle.setTextSize(13);
        agendaSubtitle.setTextColor(Ui.MUTED);
        agendaSubtitle.setPadding(0, dp(3), 0, dp(12));
        agendaHeader.addView(agendaSubtitle);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);

        todayTab = new Button(this);
        todayTab.setText("Hoy");
        todayTab.setAllCaps(false);
        todayTab.setOnClickListener(v -> {
            showToday = true;
            updateTabs();
            refreshAgenda();
        });
        tabs.addView(todayTab, new LinearLayout.LayoutParams(0, dp(44), 1f));

        upcomingTab = new Button(this);
        upcomingTab.setText("Próximos");
        upcomingTab.setAllCaps(false);
        upcomingTab.setOnClickListener(v -> {
            showToday = false;
            updateTabs();
            refreshAgenda();
        });
        LinearLayout.LayoutParams upcomingLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        upcomingLp.leftMargin = dp(8);
        tabs.addView(upcomingTab, upcomingLp);
        root.addView(tabs);
        updateTabs();

        agendaContainer = new LinearLayout(this);
        agendaContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams containerLp = matchWrap();
        containerLp.topMargin = dp(12);
        root.addView(agendaContainer, containerLp);
        refreshAgenda();
    }

    private void updateTabs() {
        if (todayTab == null || upcomingTab == null) return;
        styleTab(todayTab, showToday);
        styleTab(upcomingTab, !showToday);
    }

    private void styleTab(Button button, boolean selected) {
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(selected ? Color.WHITE : Ui.MUTED);
        button.setBackground(selected
                ? Ui.rounded(Ui.TEXT, 14, this)
                : Ui.roundedStroke(Ui.SURFACE, Ui.BORDER, 1, 14, this));
    }

    private void refreshAgenda() {
        if (agendaContainer == null || agendaSubtitle == null) return;
        long now = System.currentTimeMillis();
        List<ReminderItem> items;
        if (showToday) {
            Calendar end = Calendar.getInstance();
            end.setTimeInMillis(now);
            end.add(Calendar.DAY_OF_YEAR, 1);
            end.set(Calendar.HOUR_OF_DAY, 0);
            end.set(Calendar.MINUTE, 0);
            end.set(Calendar.SECOND, 0);
            end.set(Calendar.MILLISECOND, 0);
            items = db.between(now, end.getTimeInMillis());
            agendaSubtitle.setText(items.isEmpty() ? "Nada pendiente para hoy" : countText(items.size(), "pendiente", "pendientes") + " hoy");
        } else {
            items = db.upcoming(now);
            agendaSubtitle.setText(items.isEmpty() ? "No tienes eventos próximos" : countText(items.size(), "evento próximo", "eventos próximos"));
        }

        agendaContainer.removeAllViews();
        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(showToday
                    ? "Tu día está despejado. Crea algo o pídemelo por voz."
                    : "Cuando agregues una cita o recordatorio aparecerá aquí.");
            empty.setTextSize(14);
            empty.setTextColor(Ui.MUTED);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(24), dp(30), dp(24), dp(30));
            empty.setBackground(Ui.roundedStroke(Ui.SURFACE, Ui.BORDER, 1, 22, this));
            agendaContainer.addView(empty, matchWrap());
            return;
        }

        int limit = Math.min(items.size(), 20);
        for (int i = 0; i < limit; i++) {
            agendaContainer.addView(eventCard(items.get(i)), cardLayoutParams(i > 0));
        }
    }

    private View eventCard(ReminderItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(14), dp(15), dp(14));
        Ui.card(card);
        card.setOnClickListener(v -> edit(item));
        card.setOnLongClickListener(v -> {
            showEventMenu(item);
            return true;
        });

        View accent = new View(this);
        accent.setBackground(Ui.rounded(Ui.PRIMARY, 3, this));
        card.addView(accent, new LinearLayout.LayoutParams(dp(5), dp(60)));

        LinearLayout timeBox = new LinearLayout(this);
        timeBox.setOrientation(LinearLayout.VERTICAL);
        timeBox.setGravity(Gravity.CENTER);
        timeBox.setPadding(dp(8), dp(7), dp(8), dp(7));
        timeBox.setBackground(Ui.rounded(Ui.PRIMARY_SOFT, 16, this));
        LinearLayout.LayoutParams timeBoxLp = new LinearLayout.LayoutParams(dp(76), dp(66));
        timeBoxLp.leftMargin = dp(11);
        card.addView(timeBox, timeBoxLp);

        TextView time = new TextView(this);
        time.setText(new SimpleDateFormat("h:mm", Locale.getDefault()).format(new Date(item.eventTime)));
        time.setTextSize(17);
        time.setTextColor(Ui.PRIMARY);
        time.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        time.setGravity(Gravity.CENTER);
        timeBox.addView(time);

        TextView ampm = new TextView(this);
        ampm.setText(new SimpleDateFormat("a", Locale.getDefault()).format(new Date(item.eventTime)));
        ampm.setTextSize(10);
        ampm.setTextColor(Ui.PRIMARY);
        ampm.setGravity(Gravity.CENTER);
        timeBox.addView(ampm);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(13), 0, 0, 0);

        TextView kind = new TextView(this);
        kind.setText(item.kind.toUpperCase(Locale.getDefault()) + (item.calendarEventId > 0 ? "  ·  GOOGLE" : ""));
        kind.setTextSize(10);
        kind.setLetterSpacing(0.06f);
        kind.setTextColor(Ui.PRIMARY);
        kind.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        info.addView(kind);

        TextView itemTitle = new TextView(this);
        itemTitle.setText(item.title);
        itemTitle.setTextSize(16);
        itemTitle.setTextColor(Ui.TEXT);
        itemTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        itemTitle.setPadding(0, dp(3), 0, dp(4));
        info.addView(itemTitle);

        TextView meta = new TextView(this);
        String dateText = new SimpleDateFormat("EEE d MMM", new Locale("es", "ES")).format(new Date(item.eventTime));
        String alertText = item.remindMinutes < 0 ? "sin aviso" : reminderLabel(item.remindMinutes);
        meta.setText(dateText + "  ·  " + alertText);
        meta.setTextSize(12);
        meta.setTextColor(Ui.MUTED);
        info.addView(meta);

        card.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return card;
    }

    private void showEventMenu(ReminderItem item) {
        new AlertDialog.Builder(this)
                .setTitle(item.title)
                .setItems(new String[]{"Editar", "Eliminar"}, (dialog, which) -> {
                    if (which == 0) edit(item);
                    else confirmDelete(item);
                })
                .show();
    }

    private void confirmDelete(ReminderItem item) {
        String extra = item.calendarEventId > 0 ? " También se eliminará del calendario vinculado." : "";
        new AlertDialog.Builder(this)
                .setTitle("Eliminar")
                .setMessage("¿Eliminar \"" + item.title + "\"?" + extra)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar", (dialog, which) -> {
                    AlarmScheduler.cancel(this, item.id);
                    boolean calendarDeleted = item.calendarEventId <= 0 || CalendarBridge.deleteLinkedEvent(this, item);
                    db.delete(item.id);
                    refreshAgenda();
                    if (!calendarDeleted) {
                        Toast.makeText(this, "Se eliminó de la app, pero no pude borrarlo de Google Calendar.", Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private void edit(ReminderItem item) {
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra("reminder_id", item.id);
        startActivity(intent);
    }

    private void quickCreate(String kind) {
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra("prefill_kind", kind);
        startActivity(intent);
    }

    private void startVoiceRecognition() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        fallbackTried = false;
        startVoiceRecognitionInternal(true);
    }

    private void startVoiceRecognitionInternal(boolean preferOnDevice) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showVoiceMessage("Este teléfono no tiene un servicio de reconocimiento de voz disponible.", true);
            return;
        }

        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }

        usingOnDeviceRecognizer = false;
        if (preferOnDevice && Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            try {
                speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
                usingOnDeviceRecognizer = true;
            } catch (UnsupportedOperationException ignored) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            }
        } else {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        }

        speechRecognizer.setRecognitionListener(new AssistantRecognitionListener());
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "¿Qué necesitas?");

        setListeningUi(true, "Te escucho…");
        speechRecognizer.startListening(intent);
    }

    private void handleVoiceText(String spoken) {
        setListeningUi(false, "Escuché: “" + spoken + "”");
        String normalized = VoiceCommandParser.normalizeForIntent(spoken);

        if (pendingConfirmation != null) {
            if (containsAny(normalized, "si", "guardar", "guardalo", "confirmar", "confirmo", "dale")) {
                VoiceCommand toSave = pendingConfirmation;
                clearVoiceConfirmation();
                saveVoiceCommand(toSave);
            } else if (containsAny(normalized, "no", "cancelar", "cancela", "olvidalo")) {
                clearVoiceConfirmation();
                pendingVoiceText = "";
                pendingVoiceCommand = null;
                speak("De acuerdo. No lo guardé.");
                showVoiceMessage("Cancelado", true);
            } else if (containsAny(normalized, "editar", "cambiar")) {
                VoiceCommand toEdit = pendingConfirmation;
                clearVoiceConfirmation();
                openVoiceCommandInEditor(toEdit);
            } else {
                promptAndListen("Di guardar, editar o cancelar.");
            }
            return;
        }

        if (!pendingVoiceText.isEmpty() && containsAny(normalized, "cancelar", "cancela", "olvidalo", "olvida eso")) {
            pendingVoiceText = "";
            pendingVoiceCommand = null;
            showVoiceMessage("Conversación cancelada", true);
            speak("De acuerdo. Cancelado.");
            return;
        }

        String merged = pendingVoiceText.isEmpty() ? spoken.trim() : pendingVoiceText + " " + spoken.trim();
        long parseNow = System.currentTimeMillis();
        merged = UserContextResolver.enrich(this, merged, parseNow);
        VoiceCommand command = VoiceCommandParser.parse(merged, parseNow);

        switch (command.action) {
            case QUERY_TODAY:
                pendingVoiceText = "";
                pendingVoiceCommand = null;
                speakAgendaForDay(0, "hoy");
                return;
            case QUERY_TOMORROW:
                pendingVoiceText = "";
                pendingVoiceCommand = null;
                speakAgendaForDay(1, "mañana");
                return;
            case QUERY_NEXT:
                pendingVoiceText = "";
                pendingVoiceCommand = null;
                speakNextEvent();
                return;
            case CREATE:
                if (!command.issue.isEmpty()) {
                    pendingVoiceText = "";
                    pendingVoiceCommand = null;
                    showVoiceMessage(command.issue, true);
                    speak(command.issue);
                    return;
                }

                pendingVoiceText = merged;
                pendingVoiceCommand = command;
                if (command.missingTitle) {
                    promptAndListen("¿Qué nombre quieres ponerle?");
                    return;
                }
                if (command.missingDate) {
                    promptAndListen("¿Para qué día?");
                    return;
                }
                if (command.missingTime) {
                    promptAndListen("¿A qué hora?");
                    return;
                }

                pendingVoiceText = "";
                pendingVoiceCommand = null;
                confirmVoiceCommand(command);
                return;
            default:
                showVoiceMessage("No entendí la instrucción. Puedes pedirme una cita, un recordatorio o preguntarme qué tienes hoy.", true);
        }
    }

    private void promptAndListen(String prompt) {
        if (voiceStatus != null) voiceStatus.setText(prompt);
        speak(prompt, true);
    }

    private void confirmVoiceCommand(VoiceCommand command) {
        String when = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT, new Locale("es", "ES"))
                .format(new Date(command.eventTime));
        String ambiguity = command.timeWasAmbiguous
                ? "\n\nInterpreté la hora como de la tarde. Revísala antes de guardar."
                : "";
        String calendar = AppPrefs.calendarSyncEnabled(this) && AppPrefs.calendarId(this) > 0
                ? "\n\nTambién se enviará a Google Calendar." : "";

        pendingConfirmation = command;
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("¿Lo agendo?")
                .setMessage(command.title + "\n" + when + ambiguity + calendar)
                .setNegativeButton("Cancelar", (d, w) -> {
                    clearVoiceConfirmation();
                    speak("Cancelado.");
                })
                .setNeutralButton("Editar", (d, w) -> {
                    VoiceCommand edit = command;
                    clearVoiceConfirmation();
                    openVoiceCommandInEditor(edit);
                })
                .setPositiveButton("Guardar", (d, w) -> {
                    VoiceCommand save = command;
                    clearVoiceConfirmation();
                    saveVoiceCommand(save);
                });
        voiceConfirmDialog = builder.create();
        voiceConfirmDialog.setOnDismissListener(d -> {
            if (voiceConfirmDialog != null && !voiceConfirmDialog.isShowing()) voiceConfirmDialog = null;
        });
        voiceConfirmDialog.show();

        String spokenSummary = command.title + ", " + DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM, DateFormat.SHORT, new Locale("es", "ES")).format(new Date(command.eventTime))
                + ". ¿Lo guardo? Di guardar, editar o cancelar.";
        promptAndListen(spokenSummary);
    }

    private void clearVoiceConfirmation() {
        pendingConfirmation = null;
        if (voiceConfirmDialog != null) {
            AlertDialog dialog = voiceConfirmDialog;
            voiceConfirmDialog = null;
            if (dialog.isShowing()) dialog.dismiss();
        }
        if (speechRecognizer != null) {
            try { speechRecognizer.cancel(); } catch (Exception ignored) {}
        }
    }

    private void openVoiceCommandInEditor(VoiceCommand command) {
        if (command == null) return;
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra("prefill_kind", command.kind);
        intent.putExtra("prefill_title", command.title);
        intent.putExtra("prefill_time", command.eventTime);
        startActivity(intent);
    }

    private void saveVoiceCommand(VoiceCommand command) {
        ReminderItem item = new ReminderItem();
        item.kind = command.kind;
        item.title = command.title;
        item.notes = "";
        item.eventTime = command.eventTime;
        item.remindMinutes = AppPrefs.defaultReminderMinutes(this);
        item.id = db.save(item);
        AlarmScheduler.schedule(this, item);
        boolean calendarSaved = CalendarBridge.saveToSelectedCalendar(this, db, item);
        refreshAgenda();

        String time = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(item.eventTime));
        String confirmation = "Listo. Guardé " + item.title + " para las " + time
                + (calendarSaved ? " y lo añadí a Google Calendar." : ".");
        if (voiceStatus != null) voiceStatus.setText(confirmation);
        speak(confirmation);
        Toast.makeText(this, calendarSaved ? "Guardado y sincronizado" : "Guardado por voz", Toast.LENGTH_SHORT).show();
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
            String message = "No tienes nada pendiente para " + label + ".";
            if (voiceStatus != null) voiceStatus.setText(message);
            speak(message);
            showToday = dayOffset == 0;
            updateTabs();
            refreshAgenda();
            return;
        }

        StringBuilder speech = new StringBuilder("Para ").append(label).append(" tienes ")
                .append(items.size()).append(items.size() == 1 ? " pendiente. " : " pendientes. ");
        int limit = Math.min(items.size(), 3);
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
        if (item == null) {
            String message = "No tienes eventos próximos.";
            if (voiceStatus != null) voiceStatus.setText(message);
            speak(message);
            return;
        }
        String when = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(item.eventTime));
        String message = "Lo próximo es " + item.title + ", " + when + ".";
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
        if (utteranceId != null && utteranceId.startsWith("followup:")) {
            mainHandler.postDelayed(this::startVoiceRecognition, 250);
        }
    }

    private void speak(String text) {
        speak(text, false);
    }

    private void speak(String text, boolean listenAfter) {
        if (!AppPrefs.voiceRepliesEnabled(this)) {
            if (listenAfter) mainHandler.postDelayed(this::startVoiceRecognition, 350);
            return;
        }
        if (ttsReady && textToSpeech != null) {
            String id = (listenAfter ? "followup:" : "assistant:") + System.currentTimeMillis();
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);
        } else if (listenAfter) {
            mainHandler.postDelayed(this::startVoiceRecognition, 650);
        }
    }

    private void setListeningUi(boolean listening, String message) {
        if (voiceStatus != null) voiceStatus.setText(message);
        if (micButton != null) {
            micButton.setBackground(Ui.rounded(Color.WHITE, 39, this));
            micButton.setColorFilter(listening ? Ui.LISTENING : Ui.PRIMARY);
            micButton.setEnabled(!listening);
            micButton.setAlpha(listening ? 0.85f : 1f);
        }
    }

    private void showVoiceMessage(String message, boolean resetMic) {
        if (voiceStatus != null) voiceStatus.setText(message);
        if (resetMic) setListeningUi(false, message);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this)
                    .setTitle("Permitir recordatorios")
                    .setMessage("Lyra necesita permiso para mostrarte avisos cuando llegue una cita o recordatorio.")
                    .setNegativeButton("Ahora no", null)
                    .setPositiveButton("Permitir", (d, w) -> requestPermissions(
                            new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS))
                    .show();
        }
    }

    private void ensureWakeServiceIfEnabled() {
        if (!AppPrefs.wakeWordEnabled(this) || WakeWordService.isRunning()) return;
        if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return;
        try {
            startForegroundService(new Intent(this, WakeWordService.class));
        } catch (RuntimeException ignored) {
        }
    }

    private void pullCalendarChanges() {
        if (!AppPrefs.calendarSyncEnabled(this) || !CalendarBridge.hasPermissions(this)) return;
        new Thread(() -> {
            EventDatabase syncDb = new EventDatabase(getApplicationContext());
            CalendarBridge.SyncResult result = CalendarBridge.pullLinkedChanges(getApplicationContext(), syncDb);
            syncDb.close();
            if (result.pulled + result.removed > 0) runOnUiThread(this::refreshAgenda);
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceRecognition();
            } else {
                showVoiceMessage("Sin permiso de micrófono no puedo recibir instrucciones por voz.", true);
            }
        }
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }

    private String greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) return "Buenos días";
        if (hour < 19) return "Buenas tardes";
        return "Buenas noches";
    }

    private String reminderLabel(int minutes) {
        if (minutes == 0) return "aviso a la hora";
        if (minutes == 60) return "1 h antes";
        if (minutes == 1440) return "1 día antes";
        return minutes + " min antes";
    }

    private String countText(int count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams sectionParams() {
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = dp(22);
        return lp;
    }

    private LinearLayout.LayoutParams cardLayoutParams(boolean withTopMargin) {
        LinearLayout.LayoutParams lp = matchWrap();
        if (withTopMargin) lp.topMargin = dp(10);
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
            if (usingOnDeviceRecognizer && !fallbackTried && Build.VERSION.SDK_INT >= 31 &&
                    (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED || error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)) {
                fallbackTried = true;
                startVoiceRecognitionInternal(false);
                return;
            }
            String message;
            if (error == SpeechRecognizer.ERROR_NO_MATCH) message = "No pude entenderte. Toca el micrófono e inténtalo otra vez.";
            else if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) message = "No escuché nada. Toca el micrófono cuando quieras hablar.";
            else if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) message = "Necesito permiso de micrófono para escucharte.";
            else message = "El reconocimiento de voz no respondió. Inténtalo de nuevo.";
            setListeningUi(false, message);
        }

        @Override
        public void onResults(Bundle results) {
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            setListeningUi(false, "Listo");
            if (matches == null || matches.isEmpty()) {
                showVoiceMessage("No pude entenderte. Inténtalo otra vez.", true);
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
