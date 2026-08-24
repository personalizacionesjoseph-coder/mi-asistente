package com.joseph.miasistente;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReminderAdapter extends BaseAdapter {
    private final Context context;
    private final List<ReminderItem> items;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEE d MMM", Locale.getDefault());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());

    public ReminderAdapter(Context context, List<ReminderItem> items) {
        this.context = context;
        this.items = items;
    }

    @Override public int getCount() { return items.size(); }
    @Override public ReminderItem getItem(int position) { return items.get(position); }
    @Override public long getItemId(int position) { return items.get(position).id; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder h;
        if (convertView == null) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(12), dp(16), dp(12));
            row.setBackgroundColor(Color.WHITE);

            TextView date = new TextView(context);
            date.setGravity(Gravity.CENTER);
            date.setTextSize(12);
            date.setTextColor(Color.rgb(24, 90, 188));
            date.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(dp(86), dp(58));
            row.addView(date, dateLp);

            LinearLayout info = new LinearLayout(context);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(dp(12), 0, 0, 0);
            TextView title = new TextView(context);
            title.setTextSize(17);
            title.setTextColor(Color.rgb(23, 32, 51));
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            TextView meta = new TextView(context);
            meta.setTextSize(13);
            meta.setTextColor(Color.rgb(95, 107, 122));
            meta.setPadding(0, dp(3), 0, 0);
            info.addView(title);
            info.addView(meta);
            row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            h = new Holder(date, title, meta);
            row.setTag(h);
            convertView = row;
        } else {
            h = (Holder) convertView.getTag();
        }

        ReminderItem item = getItem(position);
        Date when = new Date(item.eventTime);
        h.date.setText(dateFormat.format(when).toUpperCase(Locale.getDefault()) + "\n" + timeFormat.format(when));
        h.title.setText(item.title);
        String alert = item.remindMinutes < 0 ? "sin aviso" : alertText(item.remindMinutes);
        h.meta.setText(item.kind + " · " + alert);
        return convertView;
    }

    private String alertText(int minutes) {
        if (minutes == 0) return "aviso a la hora";
        if (minutes == 60) return "aviso 1 h antes";
        if (minutes == 1440) return "aviso 1 día antes";
        return "aviso " + minutes + " min antes";
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static class Holder {
        final TextView date;
        final TextView title;
        final TextView meta;
        Holder(TextView date, TextView title, TextView meta) {
            this.date = date;
            this.title = title;
            this.meta = meta;
        }
    }
}
