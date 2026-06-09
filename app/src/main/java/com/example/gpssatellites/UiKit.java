package com.example.gpssatellites;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

final class UiKit {

    static int dp(Context ctx, int value) {
        return (int) (value * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }

    static TextView sectionHeader(Context ctx, String title) {
        TextView view = new TextView(ctx);
        view.setText(title);
        view.setTextSize(28);
        view.setTextColor(Color.rgb(16, 42, 67));
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(dp(ctx, 18), dp(ctx, 20), dp(ctx, 18), dp(ctx, 4));
        return view;
    }

    static TextView navButton(Context ctx, String text, boolean selected) {
        TextView view = new TextView(ctx);
        view.setText(text);
        view.setTextSize(14);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(ctx, 8));
        bg.setColor(selected ? Color.rgb(16, 42, 67) : Color.WHITE);
        bg.setStroke(dp(ctx, 1), Color.rgb(210, 219, 228));
        view.setBackground(bg);
        view.setTextColor(selected ? Color.WHITE : Color.rgb(16, 42, 67));
        return view;
    }

    static TextView chip(Context ctx, String text) {
        TextView view = new TextView(ctx);
        view.setText(text);
        view.setTextSize(15);
        view.setTextColor(Color.WHITE);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(dp(ctx, 18), dp(ctx, 10), dp(ctx, 18), dp(ctx, 10));
        view.setBackgroundColor(Color.rgb(32, 92, 122));
        return view;
    }

    static TextView bodyText(Context ctx, String text) {
        TextView view = new TextView(ctx);
        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(Color.rgb(72, 84, 98));
        return view;
    }

    static void addInfoRow(Context ctx, LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(ctx, 3), 0, dp(ctx, 3));

        TextView labelView = new TextView(ctx);
        labelView.setText(label);
        labelView.setTextSize(13);
        labelView.setTextColor(Color.rgb(120, 132, 146));
        row.addView(labelView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueView = new TextView(ctx);
        valueView.setText(value);
        valueView.setTextSize(13);
        valueView.setTypeface(Typeface.DEFAULT_BOLD);
        valueView.setTextColor(Color.rgb(16, 42, 67));
        row.addView(valueView);

        parent.addView(row);
    }

    static String bandName(float carrierFrequencyHz) {
        if (Float.isNaN(carrierFrequencyHz) || carrierFrequencyHz <= 0f) return "";
        float mhz = carrierFrequencyHz / 1_000_000f;
        if (mhz > 1565f && mhz < 1615f) return "L1";
        if (mhz > 1215f && mhz < 1255f) return "L2";
        if (mhz > 1192f && mhz < 1212f) return "E5b";
        if (mhz > 1160f && mhz < 1192f) return "L5";
        return String.format(Locale.US, "%.0f MHz", mhz);
    }
}
