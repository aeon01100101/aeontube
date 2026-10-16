package com.aeontube.app;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small code-UI helpers so screens stay consistent without layout files. */
public final class Ui {
    public static final int BG = Color.rgb(15, 15, 15);
    public static final int PANEL = Color.rgb(26, 26, 26);
    public static final int RED = Color.rgb(255, 0, 51);
    public static final int WHITE = Color.WHITE;
    public static final int GREY = Color.rgb(170, 170, 170);

    private Ui() {}

    public static int dp(Activity a, int v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }

    public static TextView text(Activity a, String s, int sp, int color, boolean bold) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }

    public static Button button(Activity a, String s) {
        Button b = new Button(a);
        b.setText(s);
        b.setTextSize(14);
        return b;
    }

    public static Button tabButton(Activity a, String s, boolean active) {
        Button b = new Button(a);
        b.setText(s);
        b.setTextSize(15);
        b.setTextColor(active ? WHITE : GREY);
        b.setBackgroundColor(active ? RED : PANEL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(a, 2), dp(a, 2), dp(a, 2), dp(a, 2));
        b.setLayoutParams(lp);
        return b;
    }

    public static LinearLayout column(Activity a) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundColor(BG);
        return l;
    }

    public static LinearLayout row(Activity a) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static void pad(View v, Activity a, int l, int t, int r, int b) {
        v.setPadding(dp(a, l), dp(a, t), dp(a, r), dp(a, b));
    }
}
