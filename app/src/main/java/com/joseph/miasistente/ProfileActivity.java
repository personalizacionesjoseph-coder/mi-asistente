package com.joseph.miasistente;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class ProfileActivity extends Activity {
    private EditText nameInput;
    private EditText preferredInput;
    private EditText workStartInput;
    private EditText workEndInput;
    private EditText contextInput;
    private Spinner reminderSpinner;
    private Switch voiceRepliesSwitch;

    private final String[] reminderLabels = {
            "A la hora", "10 minutos antes", "30 minutos antes", "1 hora antes", "1 día antes", "Sin aviso"
    };
    private final int[] reminderValues = {0, 10, 30, 60, 1440, -1};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        Ui.configureBars(this);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(14), dp(20), dp(34));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button back = new Button(this);
        back.setText("‹  Configuración");
        back.setAllCaps(false);
        back.setTextColor(Ui.PRIMARY);
        back.setTextSize(14);
        back.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setPadding(0, 0, 0, 0);
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(dp(160), dp(42)));

        TextView eyebrow = Ui.label(this, "PERFIL LOCAL");
        eyebrow.setTextColor(Ui.PRIMARY);
        root.addView(eyebrow);

        TextView heading = text("Lo que Lyra sabe de ti", 29, Ui.TEXT, true);
        heading.setPadding(0, dp(3), 0, 0);
        root.addView(heading);

        TextView intro = text("Estos datos se guardan solo en este teléfono y ayudan a personalizar saludos, avisos y respuestas.", 13, Ui.MUTED, false);
        intro.setPadding(0, dp(5), 0, dp(18));
        root.addView(intro);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(8), dp(18), dp(20));
        Ui.card(card);
        root.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addLabel(card, "TU NOMBRE");
        nameInput = input("Ej. Joseph", false);
        nameInput.setText(AppPrefs.profileName(this));
        card.addView(nameInput, fullWidth(56));

        addLabel(card, "¿CÓMO QUIERES QUE LYRA TE LLAME?");
        preferredInput = input("Ej. Joseph", false);
        preferredInput.setText(AppPrefs.preferredName(this));
        card.addView(preferredInput, fullWidth(56));

        addLabel(card, "HORARIO HABITUAL");
        LinearLayout times = new LinearLayout(this);
        times.setOrientation(LinearLayout.HORIZONTAL);
        workStartInput = input("08:00", false);
        workStartInput.setText(AppPrefs.workStart(this));
        workStartInput.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
        workEndInput = input("18:00", false);
        workEndInput.setText(AppPrefs.workEnd(this));
        workEndInput.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
        times.addView(workStartInput, new LinearLayout.LayoutParams(0, dp(56), 1f));
        LinearLayout.LayoutParams endLp = new LinearLayout.LayoutParams(0, dp(56), 1f);
        endLp.leftMargin = dp(10);
        times.addView(workEndInput, endLp);
        card.addView(times);

        addLabel(card, "AVISO PREDETERMINADO");
        reminderSpinner = new Spinner(this);
        reminderSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, reminderLabels));
        reminderSpinner.setBackground(Ui.roundedStroke(Ui.SURFACE_2, Ui.BORDER, 1, 14, this));
        reminderSpinner.setPadding(dp(10), 0, dp(10), 0);
        int reminder = AppPrefs.defaultReminderMinutes(this);
        for (int i = 0; i < reminderValues.length; i++) if (reminderValues[i] == reminder) reminderSpinner.setSelection(i);
        card.addView(reminderSpinner, fullWidth(56));

        voiceRepliesSwitch = new Switch(this);
        voiceRepliesSwitch.setText("Lyra responde en voz alta");
        voiceRepliesSwitch.setTextColor(Ui.TEXT);
        voiceRepliesSwitch.setTextSize(14);
        voiceRepliesSwitch.setChecked(AppPrefs.voiceRepliesEnabled(this));
        voiceRepliesSwitch.setPadding(0, dp(18), 0, dp(4));
        card.addView(voiceRepliesSwitch);

        addLabel(card, "LO QUE LYRA DEBE SABER DE MÍ  ·  OPCIONAL");
        contextInput = input("Ej. Trabajo de lunes a viernes. Prefiero reuniones por la tarde. Los sábados no trabajo.", true);
        contextInput.setText(AppPrefs.profileContext(this));
        card.addView(contextInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView privacy = text("No guardes aquí contraseñas, datos bancarios ni información que Lyra no necesite para ayudarte.", 12, Ui.MUTED, false);
        privacy.setPadding(dp(3), dp(14), dp(3), 0);
        card.addView(privacy);

        Button save = new Button(this);
        save.setText("Guardar perfil");
        Ui.stylePrimaryButton(save);
        save.setOnClickListener(v -> saveProfile());
        LinearLayout.LayoutParams saveLp = fullWidth(58);
        saveLp.topMargin = dp(18);
        root.addView(save, saveLp);

        Button clear = new Button(this);
        clear.setText("Borrar datos del perfil");
        Ui.styleSecondaryButton(clear);
        clear.setOnClickListener(v -> confirmClear());
        LinearLayout.LayoutParams clearLp = fullWidth(54);
        clearLp.topMargin = dp(10);
        root.addView(clear, clearLp);

        setContentView(scroll);
    }

    private void saveProfile() {
        String start = normalizeTime(workStartInput.getText().toString());
        String end = normalizeTime(workEndInput.getText().toString());
        if (start == null) {
            workStartInput.setError("Usa formato HH:mm, por ejemplo 08:00");
            workStartInput.requestFocus();
            return;
        }
        if (end == null) {
            workEndInput.setError("Usa formato HH:mm, por ejemplo 18:00");
            workEndInput.requestFocus();
            return;
        }

        int reminder = reminderValues[reminderSpinner.getSelectedItemPosition()];
        AppPrefs.saveProfile(this,
                nameInput.getText().toString(),
                preferredInput.getText().toString(),
                start,
                end,
                reminder,
                voiceRepliesSwitch.isChecked(),
                contextInput.getText().toString());
        Toast.makeText(this, "Perfil guardado", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("Borrar perfil")
                .setMessage("Se eliminarán los datos personales guardados en Lyra. Tus citas y recordatorios no se borrarán.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Borrar", (d, w) -> {
                    AppPrefs.clearProfile(this);
                    Toast.makeText(this, "Perfil borrado", Toast.LENGTH_SHORT).show();
                    recreate();
                })
                .show();
    }

    private String normalizeTime(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (!value.matches("^([01]?\\d|2[0-3]):[0-5]\\d$")) return null;
        String[] parts = value.split(":");
        int h = Integer.parseInt(parts[0]);
        int m = Integer.parseInt(parts[1]);
        return String.format(Locale.ROOT, "%02d:%02d", h, m);
    }

    private EditText input(String hint, boolean multiline) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(15);
        e.setTextColor(Ui.TEXT);
        e.setHintTextColor(Ui.MUTED);
        e.setBackground(Ui.roundedStroke(Ui.SURFACE_2, Ui.BORDER, 1, 14, this));
        if (multiline) {
            e.setGravity(Gravity.TOP);
            e.setMinLines(4);
            e.setMaxLines(8);
            e.setPadding(dp(14), dp(12), dp(14), dp(12));
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        } else {
            e.setSingleLine(true);
            e.setPadding(dp(14), 0, dp(14), 0);
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        }
        return e;
    }

    private void addLabel(LinearLayout root, String value) {
        TextView label = Ui.label(this, value);
        label.setPadding(0, dp(16), 0, dp(7));
        root.addView(label);
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
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp));
    }

    private int dp(int value) {
        return Ui.dp(this, value);
    }
}
