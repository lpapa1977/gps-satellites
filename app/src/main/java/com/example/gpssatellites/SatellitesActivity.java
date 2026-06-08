package com.example.gpssatellites;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

public class SatellitesActivity extends Activity implements GnssDataStore.Listener {
    private GnssDataStore dataStore;
    private TextView statusView;
    private TextView summaryView;
    private LinearLayout listContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dataStore = GnssDataStore.get(this);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        dataStore.addListener(this);
        dataStore.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        dataStore.removeListener(this);
        dataStore.stop();
    }

    @Override
    public void onGnssStateChanged(GnssDataStore.GnssState state) {
        statusView.setText(state.statusText);
        summaryView.setText(String.format(
                Locale.US,
                "Satelites visibles: %d | usados en fix: %d",
                state.visibleCount(),
                state.usedCount()
        ));
        renderList(state.satellites, state.selectedSvid);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246, 247, 249));

        root.addView(sectionHeader("GPS Satellites"));
        root.addView(buttonRow());

        statusView = chip("Esperando ubicacion");
        root.addView(statusView);

        summaryView = bodyText("Satelites visibles: 0 | usados en fix: 0");
        summaryView.setPadding(dp(18), dp(12), dp(18), dp(10));
        root.addView(summaryView);

        ScrollView scrollView = new ScrollView(this);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(dp(14), 0, dp(14), dp(18));
        scrollView.addView(listContainer);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        setContentView(root);
    }

    private void renderList(List<GnssDataStore.SatelliteInfo> satellites, int selectedSvid) {
        listContainer.removeAllViews();
        if (satellites.isEmpty()) {
            TextView empty = bodyText("Sin satelites detectados todavia.");
            empty.setPadding(dp(4), dp(12), dp(4), dp(12));
            listContainer.addView(empty);
            return;
        }
        for (GnssDataStore.SatelliteInfo satellite : satellites) {
            listContainer.addView(satelliteCard(satellite, satellite.svid == selectedSvid));
        }
    }

    private View satelliteCard(GnssDataStore.SatelliteInfo satellite, boolean isSelected) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        bg.setColor(Color.WHITE);
        int borderColor = isSelected ? Color.rgb(16, 42, 67)
                : satellite.usedInFix ? Color.rgb(30, 125, 88)
                : Color.rgb(210, 219, 228);
        bg.setStroke(dp(isSelected || satellite.usedInFix ? 2 : 1), borderColor);
        card.setBackground(bg);
        final int svid = satellite.svid;
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dataStore.selectSatellite(svid);
                finish();
            }
        });

        String band = bandName(satellite.carrierFrequencyHz);
        TextView title = new TextView(this);
        title.setText(String.format(Locale.US, "%s · SV %d%s",
                satellite.constellation, satellite.svid, band.isEmpty() ? "" : " · " + band));
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.rgb(16, 42, 67));
        card.addView(title);

        TextView detail = new TextView(this);
        detail.setText(String.format(
                Locale.US,
                "Elevacion %.0f° · Azimut %.0f° · Senal %.0f dBHz · %s",
                satellite.elevation,
                satellite.azimuth,
                satellite.signal,
                satellite.usedInFix ? "usado en fix" : "no usado"
        ));
        detail.setTextSize(13);
        detail.setTextColor(Color.rgb(72, 84, 98));
        detail.setPadding(0, dp(4), 0, 0);
        card.addView(detail);

        TextView orbit = new TextView(this);
        orbit.setText(String.format(
                Locale.US,
                "Efemerides: %s · Almanaque: %s",
                satellite.hasEphemeris ? "si" : "no",
                satellite.hasAlmanac ? "si" : "no"
        ));
        orbit.setTextSize(12);
        orbit.setTextColor(Color.rgb(120, 132, 146));
        orbit.setPadding(0, dp(2), 0, 0);
        card.addView(orbit);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(8), 0, 0);
        card.setLayoutParams(params);
        return card;
    }

    private View sectionHeader(String title) {
        TextView view = new TextView(this);
        view.setText(title);
        view.setTextSize(28);
        view.setTextColor(Color.rgb(16, 42, 67));
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(dp(18), dp(20), dp(18), dp(4));
        return view;
    }

    private LinearLayout buttonRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(18), 0, dp(18), dp(8));

        TextView mapButton = navButton("Mapa", false);
        mapButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        row.addView(mapButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView satellitesButton = navButton("Satelites", true);
        LinearLayout.LayoutParams satellitesParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        satellitesParams.setMargins(dp(8), 0, 0, 0);
        row.addView(satellitesButton, satellitesParams);
        return row;
    }

    private TextView navButton(String text, boolean selected) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(8));
        bg.setColor(selected ? Color.rgb(16, 42, 67) : Color.WHITE);
        bg.setStroke(dp(1), Color.rgb(210, 219, 228));
        view.setBackground(bg);
        view.setTextColor(selected ? Color.WHITE : Color.rgb(16, 42, 67));
        return view;
    }

    private TextView chip(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15);
        view.setTextColor(Color.WHITE);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(dp(18), dp(10), dp(18), dp(10));
        view.setBackgroundColor(Color.rgb(32, 92, 122));
        return view;
    }

    private TextView bodyText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(Color.rgb(72, 84, 98));
        return view;
    }

    private String bandName(float carrierFrequencyHz) {
        if (carrierFrequencyHz <= 0f) {
            return "";
        }
        float mhz = carrierFrequencyHz / 1_000_000f;
        if (mhz > 1565f && mhz < 1615f) {
            return "L1";
        }
        if (mhz > 1215f && mhz < 1255f) {
            return "L2";
        }
        if (mhz > 1192f && mhz < 1212f) {
            return "E5b";
        }
        if (mhz > 1160f && mhz < 1192f) {
            return "L5";
        }
        return String.format(Locale.US, "%.0f MHz", mhz);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
