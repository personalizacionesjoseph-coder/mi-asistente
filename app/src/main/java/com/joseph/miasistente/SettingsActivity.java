package com.joseph.miasistente;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SettingsActivity extends Activity {
    private static final int REQ_CALENDAR = 70;
    private static final int REQ_WAKE_AUDIO = 71;
    private static final int REQ_ASSISTANT_ROLE = 72;

    private LinearLayout root;
    private TextView calendarStatus;
    private Switch calendarSyncSwitch;
    private Button syncNowButton;
    private Switch wakeWordSwitch;
    private TextView wakeStatus;
    private Button assistantRoleButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        Ui.configureBars(this);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateWakeStatus();
        updateAssistantRoleStatus();
    }

    private void buildUi() {
        Ui.applyPreferences(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BG);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(14), dp(20), dp(34));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button back = new Button(this);
        back.setText("‹  Inicio");
        back.setAllCaps(false);
        back.setTextColor(Ui.PRIMARY);
        back.setTextSize(14);
        back.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setPadding(0, 0, 0, 0);
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(dp(110), dp(42)));

        TextView title = text("Configura Lyra", 30, Ui.TEXT, true);
        root.addView(title);
        TextView subtitle = text("Tu perfil, voz, apariencia, Inicio y Google Calendar en un solo lugar.", 14, Ui.MUTED, false);
        subtitle.setPadding(0, dp(5), 0, dp(20));
        root.addView(subtitle);

        addProfileCard();
        addVoiceActivationCard();
        addAppearanceCard();
        addHomeCard();
        addCalendarCard();

        TextView note = text("Tu perfil y la memoria de Lyra se guardan localmente. La activación por voz mantiene un servicio visible porque Android exige transparencia cuando una app conserva acceso al micrófono.", 12, Ui.MUTED, false);
        note.setPadding(dp(4), dp(18), dp(4), 0);
        root.addView(note);

        setContentView(scroll);
    }

    private void addProfileCard() {
        LinearLayout card = sectionCard("MI PERFIL", "Dale contexto a Lyra sin enviar tus datos a un servidor.");

        TextView summary = text(profileSummary(), 14, Ui.TEXT, false);
        summary.setPadding(0, dp(15), 0, dp(10));
        card.addView(summary);

        Button edit = new Button(this);
        edit.setText(AppPrefs.profileName(this).isEmpty() ? "Crear mi perfil" : "Editar mi perfil");
        Ui.stylePrimaryButton(edit);
        edit.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        card.addView(edit, fullWidth(52));

        Button memory = new Button(this);
        memory.setText("Memoria de Lyra");
        Ui.styleSoftButton(memory);
        memory.setOnClickListener(v -> startActivity(new Intent(this, MemoryActivity.class)));
        LinearLayout.LayoutParams memoryLp = fullWidth(50);
        memoryLp.topMargin = dp(9);
        card.addView(memory, memoryLp);
        addCardToRoot(card);
    }

    private String profileSummary() {
        String preferred = AppPrefs.preferredName(this);
        if (preferred.isEmpty()) {
            return "Todavía no has configurado un perfil. Lyra puede usar tu nombre, horario habitual y recordatorio predeterminado.";
        }
        return "Lyra te llama “" + preferred + "” · horario " + AppPrefs.workStart(this) + "–" + AppPrefs.workEnd(this)
                + " · aviso predeterminado: " + reminderLabel(AppPrefs.defaultReminderMinutes(this)) + ".";
    }

    private void addVoiceActivationCard() {
        LinearLayout card = sectionCard("VOZ Y ACTIVACIÓN", "Habla con Lyra sin tener que buscar el botón del micrófono.");

        wakeStatus = text("", 13, Ui.MUTED, false);
        wakeStatus.setPadding(0, dp(14), 0, dp(6));
        card.addView(wakeStatus);

        wakeWordSwitch = new Switch(this);
        wakeWordSwitch.setText("Activar al decir “Lyra”");
        wakeWordSwitch.setTextColor(Ui.TEXT);
        wakeWordSwitch.setTextSize(15);
        wakeWordSwitch.setPadding(0, dp(8), 0, dp(8));
        wakeWordSwitch.setChecked(AppPrefs.wakeWordEnabled(this));
        wakeWordSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            if (checked) enableWakeWord();
            else disableWakeWord();
        });
        card.addView(wakeWordSwitch);

        TextView warning = text("La activación usa el reconocimiento disponible en Android. Si tu teléfono ofrece reconocimiento local, Lyra lo prioriza. Android exige una notificación visible mientras el micrófono permanece disponible.", 12, Ui.MUTED, false);
        warning.setPadding(0, dp(2), 0, dp(12));
        card.addView(warning);

        assistantRoleButton = new Button(this);
        assistantRoleButton.setText("Usar Lyra como asistente del sistema");
        Ui.styleSecondaryButton(assistantRoleButton);
        assistantRoleButton.setOnClickListener(v -> requestAssistantRole());
        card.addView(assistantRoleButton, fullWidth(52));

        TextView systemHint = text("Esto permite abrir Lyra con el gesto o botón de asistente que admita tu teléfono. No sustituye por sí solo la palabra de activación.", 12, Ui.MUTED, false);
        systemHint.setPadding(0, dp(9), 0, 0);
        card.addView(systemHint);

        updateWakeStatus();
        updateAssistantRoleStatus();
        addCardToRoot(card);
    }

    private void enableWakeWord() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            setWakeSwitchSilently(false);
            new AlertDialog.Builder(this)
                    .setTitle("Reconocimiento no disponible")
                    .setMessage("Android no tiene un servicio de reconocimiento de voz disponible en este teléfono. Puedes seguir usando el resto de Lyra sin esta función.")
                    .setPositiveButton("Entendido", null)
                    .show();
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            setWakeSwitchSilently(false);
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_WAKE_AUDIO);
            return;
        }
        AppPrefs.setWakeWordEnabled(this, true);
        Intent service = new Intent(this, WakeWordService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
        updateWakeStatus();
    }

    private void disableWakeWord() {
        AppPrefs.setWakeWordEnabled(this, false);
        stopService(new Intent(this, WakeWordService.class));
        updateWakeStatus();
    }

    private void setWakeSwitchSilently(boolean checked) {
        if (wakeWordSwitch == null) return;
        wakeWordSwitch.setOnCheckedChangeListener(null);
        wakeWordSwitch.setChecked(checked);
        wakeWordSwitch.setOnCheckedChangeListener((buttonView, enabled) -> {
            if (enabled) enableWakeWord(); else disableWakeWord();
        });
    }

    private void updateWakeStatus() {
        if (wakeStatus == null || wakeWordSwitch == null) return;
        boolean enabled = AppPrefs.wakeWordEnabled(this);
        setWakeSwitchSilently(enabled);
        if (enabled && WakeWordService.isRunning()) {
            wakeStatus.setText("● Escucha activa · di “Lyra” y luego tu instrucción.");
            wakeStatus.setTextColor(Ui.PRIMARY);
        } else if (enabled) {
            wakeStatus.setText("Activado · iniciando escucha. Si no aparece la notificación en unos segundos, apaga y vuelve a encender esta opción.");
            wakeStatus.setTextColor(Ui.MUTED);
        } else if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            wakeStatus.setText("Este teléfono no ofrece un servicio de reconocimiento de voz compatible.");
            wakeStatus.setTextColor(Ui.MUTED);
        } else if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            wakeStatus.setText("Desactivado · reconocimiento local disponible en este teléfono.");
            wakeStatus.setTextColor(Ui.MUTED);
        } else {
            wakeStatus.setText("Desactivado · usará el servicio de reconocimiento configurado en Android.");
            wakeStatus.setTextColor(Ui.MUTED);
        }
    }

    private void requestAssistantRole() {
        if (Build.VERSION.SDK_INT < 29) {
            Toast.makeText(this, "El rol de asistente requiere Android 10 o superior.", Toast.LENGTH_LONG).show();
            return;
        }
        RoleManager roleManager = (RoleManager) getSystemService(ROLE_SERVICE);
        if (roleManager == null || !roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
            Toast.makeText(this, "Tu versión de Android no ofrece un rol de asistente seleccionable.", Toast.LENGTH_LONG).show();
            return;
        }
        if (roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
            Toast.makeText(this, "Lyra ya es tu asistente del sistema.", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT), REQ_ASSISTANT_ROLE);
    }

    private void updateAssistantRoleStatus() {
        if (assistantRoleButton == null) return;
        if (Build.VERSION.SDK_INT < 29) {
            assistantRoleButton.setEnabled(false);
            assistantRoleButton.setText("Asistente del sistema · no disponible");
            return;
        }
        RoleManager roleManager = (RoleManager) getSystemService(ROLE_SERVICE);
        boolean held = roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)
                && roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT);
        assistantRoleButton.setText(held ? "✓ Lyra es el asistente del sistema" : "Usar Lyra como asistente del sistema");
    }

    private void addAppearanceCard() {
        LinearLayout card = sectionCard("APARIENCIA", "Moderna, personalizable y sin convertir Inicio en un rompecabezas.");

        TextView themeLabel = Ui.label(this, "TEMA");
        themeLabel.setPadding(0, dp(16), 0, dp(8));
        card.addView(themeLabel);

        LinearLayout themeRow = new LinearLayout(this);
        themeRow.setOrientation(LinearLayout.HORIZONTAL);
        String currentTheme = AppPrefs.themeMode(this);
        addChoiceButton(themeRow, "Sistema", "system".equals(currentTheme), () -> setTheme("system"));
        addChoiceButton(themeRow, "Claro", "light".equals(currentTheme), () -> setTheme("light"));
        addChoiceButton(themeRow, "Oscuro", "dark".equals(currentTheme), () -> setTheme("dark"));
        card.addView(themeRow);

        TextView accentLabel = Ui.label(this, "COLOR PRINCIPAL");
        accentLabel.setPadding(0, dp(18), 0, dp(10));
        card.addView(accentLabel);

        LinearLayout colors = new LinearLayout(this);
        colors.setOrientation(LinearLayout.HORIZONTAL);
        String currentAccent = AppPrefs.accent(this);
        String[] keys = {"violet", "blue", "teal", "coral", "rose"};
        for (String key : keys) {
            int color = Ui.accentColors(key)[0];
            Button dot = new Button(this);
            dot.setText(key.equals(currentAccent) ? "✓" : "");
            dot.setTextColor(Color.WHITE);
            dot.setTextSize(17);
            dot.setPadding(0, 0, 0, 0);
            dot.setBackground(Ui.rounded(color, 22, this));
            dot.setContentDescription("Color " + key);
            dot.setOnClickListener(v -> {
                AppPrefs.setAccent(this, key);
                recreate();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
            if (colors.getChildCount() > 0) lp.leftMargin = dp(12);
            colors.addView(dot, lp);
        }
        card.addView(colors);
        addCardToRoot(card);
    }

    private void setTheme(String mode) {
        AppPrefs.setThemeMode(this, mode);
        recreate();
    }

    private void addHomeCard() {
        LinearLayout card = sectionCard("INICIO", "Elige qué aparece y en qué orden.");
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(10), 0, 0);
        card.addView(list);
        rebuildHomeRows(list);
        addCardToRoot(card);
    }

    private void rebuildHomeRows(LinearLayout list) {
        list.removeAllViews();
        List<String> order = AppPrefs.homeOrder(this);
        for (int i = 0; i < order.size(); i++) {
            final int index = i;
            final String id = order.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            TextView name = text(sectionName(id), 15, Ui.TEXT, true);
            TextView desc = text(sectionDescription(id), 12, Ui.MUTED, false);
            info.addView(name);
            info.addView(desc);
            row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            Button up = smallArrow("↑");
            up.setEnabled(index > 0);
            up.setAlpha(index > 0 ? 1f : 0.35f);
            up.setOnClickListener(v -> {
                List<String> mutable = new ArrayList<>(AppPrefs.homeOrder(this));
                String moving = mutable.remove(index);
                mutable.add(index - 1, moving);
                AppPrefs.setHomeOrder(this, mutable);
                rebuildHomeRows(list);
            });
            row.addView(up, new LinearLayout.LayoutParams(dp(42), dp(42)));

            Button down = smallArrow("↓");
            down.setEnabled(index < order.size() - 1);
            down.setAlpha(index < order.size() - 1 ? 1f : 0.35f);
            down.setOnClickListener(v -> {
                List<String> mutable = new ArrayList<>(AppPrefs.homeOrder(this));
                String moving = mutable.remove(index);
                mutable.add(index + 1, moving);
                AppPrefs.setHomeOrder(this, mutable);
                rebuildHomeRows(list);
            });
            LinearLayout.LayoutParams downLp = new LinearLayout.LayoutParams(dp(42), dp(42));
            downLp.leftMargin = dp(4);
            row.addView(down, downLp);

            Switch visible = new Switch(this);
            visible.setChecked(AppPrefs.isSectionVisible(this, id));
            visible.setContentDescription("Mostrar " + sectionName(id));
            visible.setOnCheckedChangeListener((buttonView, checked) -> {
                if (!checked && visibleSectionCount() <= 1) {
                    buttonView.setChecked(true);
                    Toast.makeText(this, "Deja al menos una sección visible.", Toast.LENGTH_SHORT).show();
                    return;
                }
                AppPrefs.setSectionVisible(this, id, checked);
            });
            LinearLayout.LayoutParams switchLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            switchLp.leftMargin = dp(8);
            row.addView(visible, switchLp);

            list.addView(row);
            if (i < order.size() - 1) list.addView(divider());
        }
    }

    private int visibleSectionCount() {
        int count = 0;
        for (String id : Arrays.asList(AppPrefs.SECTION_VOICE, AppPrefs.SECTION_QUICK, AppPrefs.SECTION_AGENDA)) {
            if (AppPrefs.isSectionVisible(this, id)) count++;
        }
        return count;
    }

    private void addCalendarCard() {
        LinearLayout card = sectionCard("GOOGLE CALENDAR", "Guarda tus citas también en el calendario de tu cuenta Google.");

        calendarStatus = text("", 14, Ui.TEXT, false);
        calendarStatus.setPadding(0, dp(15), 0, dp(12));
        card.addView(calendarStatus);

        Button choose = new Button(this);
        choose.setText(CalendarBridge.hasPermissions(this) ? "Elegir calendario" : "Conectar calendario");
        Ui.styleSecondaryButton(choose);
        choose.setOnClickListener(v -> connectCalendar());
        card.addView(choose, fullWidth(52));

        calendarSyncSwitch = new Switch(this);
        calendarSyncSwitch.setText("Sincronizar nuevas citas y cambios");
        calendarSyncSwitch.setTextColor(Ui.TEXT);
        calendarSyncSwitch.setTextSize(14);
        calendarSyncSwitch.setPadding(0, dp(16), 0, dp(8));
        calendarSyncSwitch.setChecked(AppPrefs.calendarSyncEnabled(this));
        calendarSyncSwitch.setEnabled(CalendarBridge.hasPermissions(this) && AppPrefs.calendarId(this) > 0);
        calendarSyncSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            if (checked && (!CalendarBridge.hasPermissions(this) || AppPrefs.calendarId(this) <= 0)) {
                buttonView.setChecked(false);
                connectCalendar();
                return;
            }
            AppPrefs.setCalendarSyncEnabled(this, checked);
            updateCalendarStatus();
            if (checked) syncCalendar(false);
        });
        card.addView(calendarSyncSwitch);

        syncNowButton = new Button(this);
        syncNowButton.setText("Sincronizar ahora");
        Ui.styleSoftButton(syncNowButton);
        syncNowButton.setOnClickListener(v -> syncCalendar(true));
        card.addView(syncNowButton, fullWidth(50));

        updateCalendarStatus();
        addCardToRoot(card);
    }

    private void connectCalendar() {
        if (!CalendarBridge.hasPermissions(this)) {
            requestPermissions(new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR}, REQ_CALENDAR);
            return;
        }
        showCalendarChooser();
    }

    private void showCalendarChooser() {
        List<CalendarBridge.CalendarOption> options = CalendarBridge.writableCalendars(this);
        if (options.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("No encontré calendarios")
                    .setMessage("Añade o sincroniza una cuenta de Google en Android y vuelve a intentarlo.")
                    .setPositiveButton("Entendido", null)
                    .show();
            return;
        }

        String[] labels = new String[options.size()];
        for (int i = 0; i < options.size(); i++) labels[i] = options.get(i).label();
        new AlertDialog.Builder(this)
                .setTitle("¿Qué calendario usar?")
                .setItems(labels, (dialog, which) -> {
                    CalendarBridge.CalendarOption option = options.get(which);
                    AppPrefs.setCalendar(this, option.id, option.name, option.account);
                    AppPrefs.setCalendarSyncEnabled(this, true);
                    calendarSyncSwitch.setEnabled(true);
                    if (!calendarSyncSwitch.isChecked()) {
                        calendarSyncSwitch.setChecked(true);
                    } else {
                        updateCalendarStatus();
                        syncCalendar(false);
                    }
                })
                .show();
    }

    private void syncCalendar(boolean showDetails) {
        if (!CalendarBridge.hasPermissions(this) || AppPrefs.calendarId(this) <= 0 || !AppPrefs.calendarSyncEnabled(this)) {
            if (showDetails) Toast.makeText(this, "Conecta un calendario primero.", Toast.LENGTH_SHORT).show();
            return;
        }
        syncNowButton.setEnabled(false);
        syncNowButton.setText("Sincronizando…");
        new Thread(() -> {
            EventDatabase syncDb = new EventDatabase(getApplicationContext());
            CalendarBridge.SyncResult result = CalendarBridge.syncNow(getApplicationContext(), syncDb);
            syncDb.close();
            runOnUiThread(() -> {
                syncNowButton.setEnabled(true);
                syncNowButton.setText("Sincronizar ahora");
                updateCalendarStatus();
                if (showDetails || result.pushed + result.pulled + result.removed + result.failed > 0) {
                    String message = "Listo: " + result.pushed + " enviados, " + result.pulled + " actualizados";
                    if (result.removed > 0) message += ", " + result.removed + " eliminados";
                    if (result.failed > 0) message += ", " + result.failed + " con error";
                    Toast.makeText(this, message + ".", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void updateCalendarStatus() {
        if (calendarStatus == null) return;
        boolean permissions = CalendarBridge.hasPermissions(this);
        long id = AppPrefs.calendarId(this);
        boolean enabled = AppPrefs.calendarSyncEnabled(this);
        if (!permissions) {
            calendarStatus.setText("No conectado. Necesito permiso de calendario para leer y escribir eventos.");
        } else if (id <= 0) {
            calendarStatus.setText("Permiso concedido. Elige el calendario de Google que quieres usar.");
        } else {
            String name = AppPrefs.calendarName(this);
            String account = AppPrefs.calendarAccount(this);
            calendarStatus.setText((enabled ? "● Sincronización activa\n" : "○ Sincronización pausada\n")
                    + name + (account.isEmpty() ? "" : " · " + account));
        }
        if (syncNowButton != null) syncNowButton.setEnabled(permissions && id > 0 && enabled);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CALENDAR) {
            boolean granted = CalendarBridge.hasPermissions(this);
            if (granted) showCalendarChooser();
            else Toast.makeText(this, "Sin esos permisos no puedo sincronizar con Google Calendar.", Toast.LENGTH_LONG).show();
            updateCalendarStatus();
        } else if (requestCode == REQ_WAKE_AUDIO) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                setWakeSwitchSilently(true);
                enableWakeWord();
            } else {
                setWakeSwitchSilently(false);
                Toast.makeText(this, "Sin permiso de micrófono no puedo detectar la palabra Lyra.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ASSISTANT_ROLE) updateAssistantRoleStatus();
    }

    private LinearLayout sectionCard(String label, String subtitle) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        Ui.card(card);
        TextView section = Ui.label(this, label);
        section.setTextColor(Ui.PRIMARY);
        card.addView(section);
        TextView sub = text(subtitle, 13, Ui.MUTED, false);
        sub.setPadding(0, dp(4), 0, 0);
        card.addView(sub);
        return card;
    }

    private void addCardToRoot(LinearLayout card) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (root.getChildCount() > 3) lp.topMargin = dp(14);
        root.addView(card, lp);
    }

    private void addChoiceButton(LinearLayout row, String label, boolean selected, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(selected ? Color.WHITE : Ui.TEXT);
        b.setBackground(selected ? Ui.rounded(Ui.PRIMARY, 14, this)
                : Ui.roundedStroke(Ui.SURFACE_2, Ui.BORDER, 1, 14, this));
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        if (row.getChildCount() > 0) lp.leftMargin = dp(8);
        row.addView(b, lp);
    }

    private Button smallArrow(String symbol) {
        Button b = new Button(this);
        b.setText(symbol);
        b.setTextSize(18);
        b.setTextColor(Ui.TEXT);
        b.setPadding(0, 0, 0, 0);
        b.setBackground(Ui.rounded(Ui.SURFACE_2, 12, this));
        return b;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(Ui.BORDER);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        return v;
    }

    private String sectionName(String id) {
        if (AppPrefs.SECTION_VOICE.equals(id)) return "Asistente por voz";
        if (AppPrefs.SECTION_QUICK.equals(id)) return "Acciones rápidas";
        return "Agenda";
    }

    private String sectionDescription(String id) {
        if (AppPrefs.SECTION_VOICE.equals(id)) return "Micrófono y respuestas habladas";
        if (AppPrefs.SECTION_QUICK.equals(id)) return "Crear cita o recordatorio";
        return "Hoy y próximos eventos";
    }

    private String reminderLabel(int minutes) {
        if (minutes < 0) return "sin aviso";
        if (minutes == 0) return "a la hora";
        if (minutes == 60) return "1 hora antes";
        if (minutes == 1440) return "1 día antes";
        return minutes + " min antes";
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private LinearLayout.LayoutParams fullWidth(int heightDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp));
        lp.topMargin = dp(8);
        return lp;
    }

    private int dp(int value) {
        return Ui.dp(this, value);
    }
}
