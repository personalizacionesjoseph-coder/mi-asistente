package com.joseph.miasistente;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Calendar;

public class EditorActivity extends Activity {
    private EventDatabase db;
    private ReminderItem item;
    private Spinner kindSpinner;
    private EditText titleInput;
    private EditText notesInput;
    private Button dateButton;
    private Button timeButton;
    private Spinner alertSpinner;
    private final Calendar selected = Calendar.getInstance();

    private final String[] alertLabels = {
            "A la hora",
            "10 minutos antes",
            "30 minutos antes",
            "1 hora antes",
            "1 día antes",
            "Sin aviso"
    };
    private final int[] alertValues = {0, 10, 30, 60, 1440, -1};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        Ui.configureBars(this);
        db = new EventDatabase(this);

        long id = getIntent().getLongExtra("reminder_id", 0);
        item = id > 0 ? db.get(id) : new ReminderItem();
        if (item == null) item = new ReminderItem();

        if (item.id == 0) {
            String prefillKind = getIntent().getStringExtra("prefill_kind");
            String prefillTitle = getIntent().getStringExtra("prefill_title");
            long prefillTime = getIntent().getLongExtra("prefill_time", 0);
            if (prefillKind != null && !prefillKind.trim().isEmpty()) item.kind = prefillKind;
            if (prefillTitle != null) item.title = prefillTitle;
            if (prefillTime > System.currentTimeMillis()) item.eventTime = prefillTime;
        }

        if (item.eventTime > 0) {
            selected.setTimeInMillis(item.eventTime);
        } else {
            selected.add(Calendar.HOUR_OF_DAY, 1);
            selected.set(Calendar.MINUTE, 0);
            selected.set(Calendar.SECOND, 0);
            selected.set(Calendar.MILLISECOND, 0);
        }

        buildUi();
        fillFields();
    }

    @Override
    protected void onDestroy() {
        if (db != null) db.close();
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(14), dp(20), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button back = new Button(this);
        back.setText("‹  Agenda");
        back.setAllCaps(false);
        back.setTextColor(Ui.PRIMARY);
        back.setTextSize(14);
        back.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setPadding(0, 0, 0, 0);
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(dp(120), dp(42)));

        TextView eyebrow = Ui.label(this, item.id > 0 ? "EDITAR" : "NUEVO");
        eyebrow.setTextColor(Ui.PRIMARY);
        root.addView(eyebrow);

        TextView heading = new TextView(this);
        heading.setText(item.id > 0 ? "Actualiza tu evento" : "Añade algo a tu agenda");
        heading.setTextSize(29);
        heading.setTextColor(Ui.TEXT);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setPadding(0, dp(3), 0, 0);
        root.addView(heading);

        TextView hint = new TextView(this);
        hint.setText(calendarHint());
        hint.setTextSize(13);
        hint.setTextColor(Ui.MUTED);
        hint.setPadding(0, dp(5), 0, dp(18));
        root.addView(hint);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), dp(20));
        Ui.card(form);
        root.addView(form, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addLabel(form, "TIPO");
        kindSpinner = new Spinner(this);
        kindSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Cita", "Recordatorio"}));
        styleInput(kindSpinner);
        form.addView(kindSpinner, fullWidth(54));

        addLabel(form, "TÍTULO");
        titleInput = new EditText(this);
        titleInput.setHint("Ej. Reunión con Carlos");
        titleInput.setSingleLine(true);
        titleInput.setTextSize(16);
        titleInput.setTextColor(Ui.TEXT);
        titleInput.setHintTextColor(Ui.MUTED);
        titleInput.setPadding(dp(14), 0, dp(14), 0);
        titleInput.setBackground(Ui.roundedStroke(Ui.SURFACE_2, Ui.BORDER, 1, 14, this));
        form.addView(titleInput, fullWidth(56));

        addLabel(form, "NOTAS  ·  OPCIONAL");
        notesInput = new EditText(this);
        notesInput.setHint("Dirección, teléfono, detalles…");
        notesInput.setGravity(Gravity.TOP);
        notesInput.setMinLines(3);
        notesInput.setMaxLines(6);
        notesInput.setTextSize(15);
        notesInput.setTextColor(Ui.TEXT);
        notesInput.setHintTextColor(Ui.MUTED);
        notesInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        notesInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        notesInput.setBackground(Ui.roundedStroke(Ui.SURFACE_2, Ui.BORDER, 1, 14, this));
        form.addView(notesInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addLabel(form, "FECHA Y HORA");
        LinearLayout dateRow = new LinearLayout(this);
        dateRow.setOrientation(LinearLayout.HORIZONTAL);
        dateButton = new Button(this);
        dateButton.setAllCaps(false);
        Ui.styleSecondaryButton(dateButton);
        dateButton.setOnClickListener(v -> pickDate());
        timeButton = new Button(this);
        timeButton.setAllCaps(false);
        Ui.styleSecondaryButton(timeButton);
        timeButton.setOnClickListener(v -> pickTime());
        dateRow.addView(dateButton, new LinearLayout.LayoutParams(0, dp(54), 1f));
        LinearLayout.LayoutParams timeLp = new LinearLayout.LayoutParams(0, dp(54), 1f);
        timeLp.leftMargin = dp(10);
        dateRow.addView(timeButton, timeLp);
        form.addView(dateRow);

        addLabel(form, "AVISO");
        alertSpinner = new Spinner(this);
        alertSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, alertLabels));
        styleInput(alertSpinner);
        form.addView(alertSpinner, fullWidth(54));

        if (AppPrefs.calendarSyncEnabled(this) && AppPrefs.calendarId(this) > 0) {
            LinearLayout calendarBadge = new LinearLayout(this);
            calendarBadge.setOrientation(LinearLayout.HORIZONTAL);
            calendarBadge.setGravity(Gravity.CENTER_VERTICAL);
            calendarBadge.setPadding(dp(13), dp(11), dp(13), dp(11));
            calendarBadge.setBackground(Ui.rounded(Ui.PRIMARY_SOFT, 15, this));
            TextView badgeText = new TextView(this);
            badgeText.setText("✓  Google Calendar · " + AppPrefs.calendarName(this));
            badgeText.setTextColor(Ui.PRIMARY);
            badgeText.setTextSize(12);
            badgeText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            calendarBadge.addView(badgeText);
            LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            badgeLp.topMargin = dp(18);
            form.addView(calendarBadge, badgeLp);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = dp(18);
        root.addView(actions, actionsLp);

        Button cancel = new Button(this);
        cancel.setText("Cancelar");
        Ui.styleSecondaryButton(cancel);
        cancel.setOnClickListener(v -> finish());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(58), 1f));

        Button save = new Button(this);
        save.setText(item.id > 0 ? "Guardar cambios" : "Guardar");
        Ui.stylePrimaryButton(save);
        save.setOnClickListener(v -> save());
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(0, dp(58), 1.25f);
        saveLp.leftMargin = dp(10);
        actions.addView(save, saveLp);

        setContentView(scroll);
    }

    private String calendarHint() {
        if (AppPrefs.calendarSyncEnabled(this) && AppPrefs.calendarId(this) > 0) {
            return "Se guardará en este teléfono y también en " + AppPrefs.calendarName(this) + ".";
        }
        if (item.calendarEventId > 0) return "Este evento está vinculado a Google Calendar. La sincronización está pausada.";
        return "Se guardará en este teléfono. Puedes conectar Google Calendar desde Configuración.";
    }

    private void styleInput(android.view.View view) {
        view.setBackground(Ui.roundedStroke(Ui.SURFACE_2, Ui.BORDER, 1, 14, this));
        view.setPadding(dp(10), 0, dp(10), 0);
    }

    private void fillFields() {
        kindSpinner.setSelection("Cita".equals(item.kind) ? 0 : 1);
        titleInput.setText(item.title);
        notesInput.setText(item.notes);
        updateDateTimeButtons();

        int selectedIndex = 0;
        for (int i = 0; i < alertValues.length; i++) {
            if (alertValues[i] == item.remindMinutes) {
                selectedIndex = i;
                break;
            }
        }
        alertSpinner.setSelection(selectedIndex);
    }

    private void pickDate() {
        new DatePickerDialog(this, (view, year, month, day) -> {
            selected.set(Calendar.YEAR, year);
            selected.set(Calendar.MONTH, month);
            selected.set(Calendar.DAY_OF_MONTH, day);
            updateDateTimeButtons();
        }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH), selected.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void pickTime() {
        new TimePickerDialog(this, (view, hour, minute) -> {
            selected.set(Calendar.HOUR_OF_DAY, hour);
            selected.set(Calendar.MINUTE, minute);
            selected.set(Calendar.SECOND, 0);
            selected.set(Calendar.MILLISECOND, 0);
            updateDateTimeButtons();
        }, selected.get(Calendar.HOUR_OF_DAY), selected.get(Calendar.MINUTE), false).show();
    }

    private void updateDateTimeButtons() {
        dateButton.setText(DateFormat.getDateInstance(DateFormat.MEDIUM).format(selected.getTime()));
        timeButton.setText(DateFormat.getTimeInstance(DateFormat.SHORT).format(selected.getTime()));
    }

    private void save() {
        String title = titleInput.getText().toString().trim();
        if (title.isEmpty()) {
            titleInput.setError("Escribe un título");
            titleInput.requestFocus();
            return;
        }

        long eventTime = selected.getTimeInMillis();
        if (eventTime <= System.currentTimeMillis()) {
            Toast.makeText(this, "La fecha y hora deben estar en el futuro.", Toast.LENGTH_LONG).show();
            return;
        }

        int remindMinutes = alertValues[alertSpinner.getSelectedItemPosition()];
        long alertTime = remindMinutes < 0 ? eventTime : eventTime - (remindMinutes * 60_000L);
        if (remindMinutes >= 0 && alertTime <= System.currentTimeMillis()) {
            new AlertDialog.Builder(this)
                    .setTitle("Ese aviso ya pasó")
                    .setMessage("Elige un aviso más cercano a la hora del evento o usa \"Sin aviso\".")
                    .setPositiveButton("Entendido", null)
                    .show();
            return;
        }

        item.kind = (String) kindSpinner.getSelectedItem();
        item.title = title;
        item.notes = notesInput.getText().toString().trim();
        item.eventTime = eventTime;
        item.remindMinutes = remindMinutes;
        item.id = db.save(item);
        AlarmScheduler.schedule(this, item);

        boolean syncRequested = AppPrefs.calendarSyncEnabled(this) && AppPrefs.calendarId(this) > 0;
        boolean calendarSaved = syncRequested && CalendarBridge.saveToSelectedCalendar(this, db, item);
        if (syncRequested && !calendarSaved) {
            Toast.makeText(this, "Guardado en Lyra, pero Google Calendar no respondió.", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, calendarSaved ? "Guardado y sincronizado" : "Guardado", Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    private void addLabel(LinearLayout root, String text) {
        TextView label = Ui.label(this, text);
        label.setPadding(0, dp(16), 0, dp(7));
        root.addView(label);
    }

    private LinearLayout.LayoutParams fullWidth(int heightDp) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp));
    }

    private int dp(int value) {
        return Ui.dp(this, value);
    }
}
