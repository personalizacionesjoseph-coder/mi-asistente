package com.joseph.miasistente;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MemoryActivity extends Activity {
    private EventDatabase db;
    private LinearLayout list;
    private EditText input;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        Ui.configureBars(this);
        db = new EventDatabase(this);
        buildUi();
        refresh();
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
        root.setPadding(dp(20), dp(14), dp(20), dp(34));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button back = new Button(this);
        back.setText("‹  Perfil");
        back.setAllCaps(false);
        back.setTextColor(Ui.PRIMARY);
        back.setTextSize(14);
        back.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setPadding(0, 0, 0, 0);
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(dp(130), dp(42)));

        TextView eyebrow = Ui.label(this, "MEMORIA CONTROLABLE");
        eyebrow.setTextColor(Ui.PRIMARY);
        root.addView(eyebrow);

        TextView heading = text("Memoria de Lyra", 29, Ui.TEXT, true);
        heading.setPadding(0, dp(3), 0, 0);
        root.addView(heading);

        TextView intro = text("Aquí ves exactamente lo que Lyra recuerda. Puedes añadir, editar o borrar cualquier dato.", 13, Ui.MUTED, false);
        intro.setPadding(0, dp(5), 0, dp(18));
        root.addView(intro);

        LinearLayout addCard = new LinearLayout(this);
        addCard.setOrientation(LinearLayout.VERTICAL);
        addCard.setPadding(dp(16), dp(14), dp(16), dp(16));
        Ui.card(addCard);
        root.addView(addCard, fullWidthWrap());

        input = new EditText(this);
        input.setHint("Ej. Yorch es mi proveedor");
        input.setTextSize(15);
        input.setTextColor(Ui.TEXT);
        input.setHintTextColor(Ui.MUTED);
        input.setMinLines(2);
        input.setMaxLines(5);
        input.setGravity(Gravity.TOP);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setBackground(Ui.roundedStroke(Ui.SURFACE_2, Ui.BORDER, 1, 14, this));
        addCard.addView(input, fullWidthWrap());

        Button add = new Button(this);
        add.setText("Guardar en memoria");
        Ui.stylePrimaryButton(add);
        add.setOnClickListener(v -> addMemory());
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        addLp.topMargin = dp(10);
        addCard.addView(add, addLp);

        TextView label = Ui.label(this, "LO QUE LYRA RECUERDA");
        label.setPadding(0, dp(22), 0, dp(9));
        root.addView(label);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list, fullWidthWrap());

        Button clear = new Button(this);
        clear.setText("Borrar toda la memoria");
        Ui.styleSecondaryButton(clear);
        clear.setOnClickListener(v -> confirmClear());
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        clearLp.topMargin = dp(18);
        root.addView(clear, clearLp);

        setContentView(scroll);
    }

    private void addMemory() {
        String fact = input.getText().toString().trim();
        if (fact.isEmpty()) {
            input.setError("Escribe algo que Lyra deba recordar");
            return;
        }
        db.addMemory(fact);
        input.setText("");
        Toast.makeText(this, "Lyra lo recordará", Toast.LENGTH_SHORT).show();
        refresh();
    }

    private void refresh() {
        if (list == null) return;
        list.removeAllViews();
        List<MemoryItem> items = db.memories();
        if (items.isEmpty()) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setPadding(dp(18), dp(18), dp(18), dp(18));
            Ui.card(empty);
            TextView title = text("Aún no hay recuerdos guardados", 15, Ui.TEXT, true);
            TextView body = text("Puedes escribirlos aquí o decir: “Lyra, recuerda que Yorch es mi proveedor”.", 13, Ui.MUTED, false);
            body.setPadding(0, dp(5), 0, 0);
            empty.addView(title);
            empty.addView(body);
            list.addView(empty, fullWidthWrap());
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            MemoryItem item = items.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            card.setBackground(Ui.roundedStroke(Ui.SURFACE, Ui.BORDER, 1, 18, this));

            TextView fact = text(item.fact, 15, Ui.TEXT, true);
            card.addView(fact);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setPadding(0, dp(10), 0, 0);

            Button edit = new Button(this);
            edit.setText("Editar");
            Ui.styleSoftButton(edit);
            edit.setOnClickListener(v -> editMemory(item));
            actions.addView(edit, new LinearLayout.LayoutParams(0, dp(44), 1f));

            Button delete = new Button(this);
            delete.setText("Borrar");
            Ui.styleSecondaryButton(delete);
            delete.setOnClickListener(v -> {
                db.deleteMemory(item.id);
                refresh();
            });
            LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
            deleteLp.leftMargin = dp(8);
            actions.addView(delete, deleteLp);
            card.addView(actions);

            LinearLayout.LayoutParams lp = fullWidthWrap();
            if (i > 0) lp.topMargin = dp(9);
            list.addView(card, lp);
        }
    }

    private void editMemory(MemoryItem item) {
        EditText edit = new EditText(this);
        edit.setText(item.fact);
        edit.setSelectAllOnFocus(false);
        edit.setMinLines(2);
        edit.setPadding(dp(16), dp(12), dp(16), dp(12));
        new AlertDialog.Builder(this)
                .setTitle("Editar recuerdo")
                .setView(edit)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", (d, w) -> {
                    String value = edit.getText().toString().trim();
                    if (!value.isEmpty()) {
                        db.updateMemory(item.id, value);
                        refresh();
                    }
                })
                .show();
    }

    private void confirmClear() {
        if (db.memories().isEmpty()) {
            Toast.makeText(this, "No hay memoria para borrar", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Borrar toda la memoria")
                .setMessage("Se eliminará todo lo que Lyra recuerda de forma estructurada. Tu perfil y tu agenda no se borrarán.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Borrar", (d, w) -> {
                    db.clearMemories();
                    refresh();
                })
                .show();
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private LinearLayout.LayoutParams fullWidthWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Ui.dp(this, value);
    }
}
