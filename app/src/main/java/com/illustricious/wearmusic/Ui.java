package com.illustricious.wearmusic;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** View helpers (no AndroidX): true-black AMOLED look, rounded controls, round-face safe insets. */
final class Ui {
    private Ui() {}

    static final int BLACK = Color.BLACK, SURFACE = Color.rgb(24, 24, 28), SURFACE_HI = Color.rgb(44, 44, 52),
        ACCENT = Color.rgb(125, 211, 252), OK = Color.rgb(134, 239, 172), BAD = Color.rgb(248, 113, 113),
        MUTED = Color.rgb(150, 156, 170), WARN = Color.rgb(250, 204, 21), WHITE = Color.WHITE;

    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    /** Horizontal / vertical safe insets: larger on round faces where corners are clipped. */
    static int insetX(Context c) {
        DisplayMetrics m = c.getResources().getDisplayMetrics();
        return c.getResources().getConfiguration().isScreenRound() ? Math.round(m.widthPixels * 0.11f) : dp(c, 8);
    }

    static int insetY(Context c) {
        DisplayMetrics m = c.getResources().getDisplayMetrics();
        return c.getResources().getConfiguration().isScreenRound() ? Math.round(m.heightPixels * 0.16f) : dp(c, 8);
    }

    /** Vertical scrolling page (crown-scrollable); returns the content column to add views to. */
    static LinearLayout page(Activity a) {
        ScrollView scroll = new ScrollView(a);
        scroll.setBackgroundColor(BLACK);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFocusable(true);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(insetX(a), insetY(a), insetX(a), insetY(a));
        scroll.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        a.setContentView(scroll);
        return col;
    }

    static void styleList(android.widget.ListView list) {
        Context c = list.getContext();
        list.setBackgroundColor(BLACK);
        list.setDivider(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(dp(c, 5));
        list.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        list.setVerticalScrollBarEnabled(false);
        list.setClipToPadding(false);
        list.setPadding(insetX(c), insetY(c), insetX(c), insetY(c));
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color); t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return t;
    }

    static TextView heading(Context c, String s) {
        TextView t = text(c, s, 17, WHITE, true);
        t.setPadding(0, 0, 0, dp(c, 6));
        return t;
    }

    static GradientDrawable pill(Context c, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp(c, 28));
        return g;
    }

    /** Rounded pill button. {@code selected} fills it with the accent colour. */
    static Button button(Context c, String label, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(label); b.setAllCaps(false); b.setOnClickListener(l); b.setTextSize(15);
        b.setStateListAnimator(null);
        b.setPadding(dp(c, 12), 0, dp(c, 12), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 46));
        lp.topMargin = dp(c, 5);
        b.setLayoutParams(lp);
        style(b, false);
        return b;
    }

    static void style(Button b, boolean selected) {
        Context c = b.getContext();
        b.setBackground(pill(c, selected ? ACCENT : "card".equals(b.getTag()) ? SURFACE_HI : SURFACE));
        b.setTextColor(selected ? BLACK : b.isEnabled() ? WHITE : MUTED);
    }

    static View spacer(Context c, int dp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, dp)));
        return v;
    }

    // ---- runtime permissions ------------------------------------------------------------------------

    static boolean granted(Context c, String permission) {
        return Build.VERSION.SDK_INT < 23 || c.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    static String[] missing(Context c, String... permissions) {
        List<String> need = new ArrayList<String>();
        for (String p : permissions) if (!granted(c, p)) need.add(p);
        return need.toArray(new String[0]);
    }

    static String bluetoothPermission() {
        return Build.VERSION.SDK_INT >= 31 ? "android.permission.BLUETOOTH_CONNECT" : "android.permission.BLUETOOTH";
    }

    static String audioPermission() {
        return Build.VERSION.SDK_INT >= 33 ? "android.permission.READ_MEDIA_AUDIO" : "android.permission.READ_EXTERNAL_STORAGE";
    }
}
