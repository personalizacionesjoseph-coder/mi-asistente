package com.joseph.miasistente;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.graphics.Color;
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
        super.onCreate(savedInstanceState);
        db = new EventDatabase(this);

        long id = getIntent().getLongExtra("reminder_id", 0);
        item = id > 0 ? db.get(id) : new ReminderItem();
        if (item == null) item = new ReminderItem();

        if (item.eventTime > 0) {
            selected.setTimeInMillis(item.eventTime);
        } else {
            selected.add(Calendar.HOUR_OF_DAY, 1);
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
        scroll.setBackgroundColor(Color.rgb(247, 249, 252));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(24));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView heading = new TextView(this);
        heading.setText(item.id > 0 ? "Editar" : "Nuevo");
        heading.setTextSize(28);
        heading.setTextColor(Color.rgb(23, 32, 51));
        root.addView(heading);

        TextView hint = new TextView(this);
        hint.setText("Guarda algo importante y deja que el teléfono se encargue de avisarte.");
        hint.setTextSize(14);
        hint.setTextColor(Color.rgb(95, 107, 122));
        hint.setPadding(0, dp(4), 0, dp(20));
        root.addView(hint);

        label(root, "TIPO");
        kindSpinner = new Spinner(this);
        kindSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Cita", "Recordatorio"}));
        root.addView(kindSpinner, fullWidth(52));

        label(root, "TÍTULO");
        titleInput = new EditText(this);
        titleInput.setHint("Ej. Reunión con Carlos");
        titleInput.setSingleLine(true);
        titleInput.setTextSize(17);
        root.addView(titleInput, fullWidth(56));

        label(root, "NOTAS (OPCIONAL)");
        notesInput = new EditText(this);
        notesInput.setHint("Dirección, teléfono, detalles...");
        notesInput.setGravity(Gravity.TOP);
        notesInput.setMinLines(3);
        notesInput.setMaxLines(5);
        notesInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        root.addView(notesInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(root, "FECHA Y HORA");
        LinearLayout dateRow = new LinearLayout(this);
        dateRow.setOrientation(LinearLayout.HORIZONTAL);
        dateButton = new Button(this);
        dateButton.setAllCaps(false);
        dateButton.setOnClickListener(v -> pickDate());
        timeButton = new Button(this);
        timeButton.setAllCaps(false);
        timeButton.setOnClickListener(v -> pickTime());
        dateRow.addView(dateButton, new LinearLayout.LayoutParams(0, dp(54), 1f));
        LinearLayout.LayoutParams timeLp = new LinearLayout.LayoutParams(0, dp(54), 1f);
        timeLp.leftMargin = dp(10);
        dateRow.addView(timeButton, timeLp);
        root.addView(dateRow);

        label(root, "AVISO");
        alertSpinner = new Spinner(this);
        alertSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, alertLabels));
        root.addView(alertSpinner, fullWidth(52));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(24), 0, 0);

        Button cancel = new Button(this);
        cancel.setText("Cancelar");
        cancel.setAllCaps(false);
        cancel.setOnClickListener(v -> finish());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(54), 1f));

        Button save = new Button(this);
        save.setText("Guardar");
        save.setAllCaps(false);
        save.setTextColor(Color.WHITE);
        save.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(24, 90, 188)));
        save.setOnClickListener(v -> save());
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(0, dp(54), 1f);
        saveLp.leftMargin = dp(10);
        actions.addView(save, saveLp);
        root.addView(actions);

        setContentView(scroll);
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
        Toast.makeText(this, "Guardado", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void label(LinearLayout root, String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(12);
        label.setTextColor(Color.rgb(95, 107, 122));
        label.setPadding(0, dp(16), 0, dp(5));
        root.addView(label);
    }

    private LinearLayout.LayoutParams fullWidth(int heightDp) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
