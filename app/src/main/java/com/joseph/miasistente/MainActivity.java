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
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.List;

public class MainActivity extends Activity {
    private EventDatabase db;
    private ListView listView;
    private TextView emptyView;
    private TextView nextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new EventDatabase(this);
        NotificationHelper.ensureChannel(this);
        buildUi();
        requestNotificationPermissionIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onDestroy() {
        if (db != null) db.close();
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247, 249, 252));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(22), dp(20), dp(20));
        header.setBackgroundColor(Color.rgb(24, 90, 188));

        TextView title = new TextView(this);
        title.setText("Mi Asistente");
        title.setTextSize(28);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Tus citas y recordatorios, sin depender de internet.");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.rgb(225, 234, 248));
        subtitle.setPadding(0, dp(4), 0, 0);
        header.addView(subtitle);

        nextView = new TextView(this);
        nextView.setTextSize(14);
        nextView.setTextColor(Color.WHITE);
        nextView.setPadding(dp(12), dp(10), dp(12), dp(10));
        nextView.setBackgroundColor(Color.rgb(13, 71, 161));
        LinearLayout.LayoutParams nextLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nextLp.topMargin = dp(16);
        header.addView(nextView, nextLp);
        root.addView(header);

        Button add = new Button(this);
        add.setText("＋  NUEVA CITA O RECORDATORIO");
        add.setTextSize(15);
        add.setAllCaps(false);
        add.setTextColor(Color.WHITE);
        add.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(24, 90, 188)));
        add.setOnClickListener(v -> startActivity(new Intent(this, EditorActivity.class)));
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        addLp.setMargins(dp(16), dp(18), dp(16), dp(16));
        root.addView(add, addLp);

        TextView section = new TextView(this);
        section.setText("PRÓXIMOS");
        section.setTextSize(12);
        section.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        section.setTextColor(Color.rgb(95, 107, 122));
        section.setPadding(dp(20), dp(4), dp(20), dp(8));
        root.addView(section);

        listView = new ListView(this);
        listView.setDividerHeight(dp(1));
        listView.setBackgroundColor(Color.WHITE);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            ReminderItem item = (ReminderItem) parent.getItemAtPosition(position);
            Intent intent = new Intent(this, EditorActivity.class);
            intent.putExtra("reminder_id", item.id);
            startActivity(intent);
        });
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            ReminderItem item = (ReminderItem) parent.getItemAtPosition(position);
            confirmDelete(item);
            return true;
        });

        emptyView = new TextView(this);
        emptyView.setText("Todavía no tienes nada pendiente.\nCrea tu primera cita o recordatorio.");
        emptyView.setTextSize(16);
        emptyView.setTextColor(Color.rgb(95, 107, 122));
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(dp(32), dp(36), dp(32), dp(36));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        body.addView(emptyView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private void refresh() {
        List<ReminderItem> items = db.upcoming(System.currentTimeMillis());
        listView.setAdapter(new ReminderAdapter(this, items));
        boolean empty = items.isEmpty();
        listView.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);

        if (empty) {
            nextView.setText("Agenda despejada");
        } else {
            ReminderItem next = items.get(0);
            String when = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(next.eventTime);
            nextView.setText("Próximo: " + next.title + "  ·  " + when);
        }
    }

    private void confirmDelete(ReminderItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar")
                .setMessage("¿Eliminar \"" + item.title + "\"?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar", (dialog, which) -> {
                    AlarmScheduler.cancel(this, item.id);
                    db.delete(item.id);
                    refresh();
                })
                .show();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this)
                    .setTitle("Permitir recordatorios")
                    .setMessage("Mi Asistente necesita permiso para mostrarte los avisos de tus citas y recordatorios.")
                    .setNegativeButton("Ahora no", null)
                    .setPositiveButton("Permitir", (d, w) -> requestPermissions(
                            new String[]{Manifest.permission.POST_NOTIFICATIONS}, 40))
                    .show();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
