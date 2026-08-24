package com.joseph.miasistente;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public final class Ui {
    public static int BG;
    public static int SURFACE;
    public static int SURFACE_2;
    public static int PRIMARY;
    public static int PRIMARY_DARK;
    public static int PRIMARY_SOFT;
    public static int TEXT;
    public static int MUTED;
    public static int BORDER;
    public static int DANGER;
    public static int LISTENING;
    public static boolean DARK;

    private Ui() {}

    public static void applyActivityTheme(Activity activity) {
        DARK = shouldUseDark(activity);
        activity.setTheme(DARK ? R.style.Theme_MiAsistente_Dark : R.style.Theme_MiAsistente_Light);
        applyPreferences(activity);
    }

    public static void applyPreferences(Context context) {
        DARK = shouldUseDark(context);
        if (DARK) {
            BG = Color.rgb(14, 17, 24);
            SURFACE = Color.rgb(24, 28, 38);
            SURFACE_2 = Color.rgb(31, 36, 48);
            TEXT = Color.rgb(244, 246, 251);
            MUTED = Color.rgb(157, 165, 184);
            BORDER = Color.rgb(48, 55, 70);
        } else {
            BG = Color.rgb(246, 248, 252);
            SURFACE = Color.WHITE;
            SURFACE_2 = Color.rgb(250, 251, 254);
            TEXT = Color.rgb(23, 28, 40);
            MUTED = Color.rgb(105, 113, 132);
            BORDER = Color.rgb(228, 232, 240);
        }

        int[] colors = accentColors(AppPrefs.accent(context));
        PRIMARY = colors[0];
        PRIMARY_DARK = colors[1];
        PRIMARY_SOFT = DARK ? blend(PRIMARY, BG, 0.78f) : blend(PRIMARY, Color.WHITE, 0.88f);
        DANGER = Color.rgb(214, 69, 79);
        LISTENING = Color.rgb(229, 74, 90);
    }

    public static void configureBars(Activity activity) {
        activity.getWindow().setStatusBarColor(BG);
        activity.getWindow().setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 23) {
            int flags = activity.getWindow().getDecorView().getSystemUiVisibility();
            if (!DARK) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                if (!DARK) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            activity.getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    private static boolean shouldUseDark(Context context) {
        String mode = AppPrefs.themeMode(context);
        if ("dark".equals(mode)) return true;
        if ("light".equals(mode)) return false;
        int night = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    public static int[] accentColors(String key) {
        switch (key) {
            case "blue": return new int[]{Color.rgb(48, 112, 246), Color.rgb(35, 82, 201)};
            case "teal": return new int[]{Color.rgb(20, 151, 140), Color.rgb(14, 111, 103)};
            case "coral": return new int[]{Color.rgb(235, 104, 74), Color.rgb(197, 74, 49)};
            case "rose": return new int[]{Color.rgb(219, 74, 126), Color.rgb(177, 50, 99)};
            default: return new int[]{Color.rgb(103, 86, 245), Color.rgb(77, 62, 207)};
        }
    }

    private static int blend(int foreground, int background, float backgroundWeight) {
        float fg = 1f - backgroundWeight;
        int r = Math.round(Color.red(foreground) * fg + Color.red(background) * backgroundWeight);
        int g = Math.round(Color.green(foreground) * fg + Color.green(background) * backgroundWeight);
        int b = Math.round(Color.blue(foreground) * fg + Color.blue(background) * backgroundWeight);
        return Color.rgb(r, g, b);
    }

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable rounded(int color, float radiusDp, Context context) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(context, Math.round(radiusDp)));
        return d;
    }

    public static GradientDrawable roundedStroke(int color, int strokeColor, int strokeDp, float radiusDp, Context context) {
        GradientDrawable d = rounded(color, radiusDp, context);
        d.setStroke(dp(context, strokeDp), strokeColor);
        return d;
    }

    public static GradientDrawable gradient(int startColor, int endColor, float radiusDp, Context context) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{startColor, endColor});
        d.setCornerRadius(dp(context, Math.round(radiusDp)));
        return d;
    }

    public static void stylePrimaryButton(Button button) {
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(rounded(PRIMARY, 16, button.getContext()));
        button.setElevation(dp(button.getContext(), 1));
    }

    public static void styleSecondaryButton(Button button) {
        button.setAllCaps(false);
        button.setTextColor(TEXT);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(roundedStroke(SURFACE, BORDER, 1, 15, button.getContext()));
        button.setElevation(0);
    }

    public static void styleSoftButton(Button button) {
        button.setAllCaps(false);
        button.setTextColor(PRIMARY);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(rounded(PRIMARY_SOFT, 15, button.getContext()));
        button.setElevation(0);
    }

    public static TextView label(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(11);
        view.setLetterSpacing(0.08f);
        view.setTextColor(MUTED);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    public static void card(View view) {
        view.setBackground(rounded(SURFACE, 24, view.getContext()));
        view.setElevation(dp(view.getContext(), DARK ? 1 : 2));
    }
}
