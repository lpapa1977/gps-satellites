package com.example.gpssatellites;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SatellitesActivity extends Activity implements GnssDataStore.Listener {
    private static final String FILTER_ALL = "Todos";

    private GnssDataStore dataStore;
    private TextView statusView;
    private TextView summaryView;
    private LinearLayout listContainer;
    private String activeFilter = FILTER_ALL;
    private List<GnssDataStore.SatelliteInfo> lastSatellites = new ArrayList<>();
    private int lastSelectedSvid = -1;

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
        lastSatellites = state.satellites;
        lastSelectedSvid = state.selectedSvid;
        statusView.setText(state.statusText);
        summaryView.setText(String.format(Locale.US,
                "Satelites visibles: %d | usados en fix: %d",
                state.visibleCount(), state.usedCount()));
        renderList(state.satellites, state.selectedSvid);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246, 247, 249));

        root.addView(UiKit.sectionHeader(this, "GPS Satellites"));
        root.addView(buildButtonRow());

        statusView = UiKit.chip(this, "Esperando ubicacion");
        root.addView(statusView);

        summaryView = UiKit.bodyText(this, "Satelites visibles: 0 | usados en fix: 0");
        summaryView.setPadding(dp(18), dp(12), dp(18), dp(6));
        root.addView(summaryView);

        root.addView(buildFilterRow());

        ScrollView scrollView = new ScrollView(this);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(dp(14), 0, dp(14), dp(18));
        scrollView.addView(listContainer);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private View buildFilterRow() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(14), dp(4), dp(14), dp(8));

        String[] filters = {FILTER_ALL, "GPS", "Galileo", "GLONASS", "BeiDou", "QZSS"};
        for (String filter : filters) {
            TextView chip = buildFilterChip(filter);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            p.setMargins(0, 0, dp(8), 0);
            row.addView(chip, p);
        }

        scroll.addView(row);
        return scroll;
    }

    private TextView buildFilterChip(String filter) {
        TextView view = new TextView(this);
        view.setText(filter);
        view.setTextSize(13);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(dp(14), dp(6), dp(14), dp(6));
        applyFilterChipStyle(view, filter.equals(activeFilter));
        view.setOnClickListener(v -> {
            activeFilter = filter;
            updateFilterChips((LinearLayout) ((HorizontalScrollView) v.getParent().getParent()).getChildAt(0));
            renderList(lastSatellites, lastSelectedSvid);
        });
        return view;
    }

    private void applyFilterChipStyle(TextView view, boolean active) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(20));
        bg.setColor(active ? Color.rgb(16, 42, 67) : Color.WHITE);
        bg.setStroke(dp(1), Color.rgb(210, 219, 228));
        view.setBackground(bg);
        view.setTextColor(active ? Color.WHITE : Color.rgb(72, 84, 98));
    }

    private void updateFilterChips(LinearLayout row) {
        String[] filters = {FILTER_ALL, "GPS", "Galileo", "GLONASS", "BeiDou", "QZSS"};
        for (int i = 0; i < row.getChildCount(); i++) {
            applyFilterChipStyle((TextView) row.getChildAt(i), filters[i].equals(activeFilter));
        }
    }

    private void renderList(List<GnssDataStore.SatelliteInfo> satellites, int selectedSvid) {
        listContainer.removeAllViews();

        List<GnssDataStore.SatelliteInfo> filtered = new ArrayList<>();
        for (GnssDataStore.SatelliteInfo sat : satellites) {
            if (activeFilter.equals(FILTER_ALL) || sat.constellation.equals(activeFilter)) {
                filtered.add(sat);
            }
        }

        if (filtered.isEmpty()) {
            String msg = satellites.isEmpty()
                    ? "Sin satelites detectados todavia."
                    : "Sin satelites " + activeFilter + " visibles.";
            TextView empty = UiKit.bodyText(this, msg);
            empty.setPadding(dp(4), dp(12), dp(4), dp(12));
            listContainer.addView(empty);
            return;
        }

        for (GnssDataStore.SatelliteInfo satellite : filtered) {
            listContainer.addView(buildSatelliteCard(satellite, satellite.svid == selectedSvid));
        }
    }

    private View buildSatelliteCard(GnssDataStore.SatelliteInfo satellite, boolean isSelected) {
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

        card.setOnClickListener(v -> {
            dataStore.selectSatellite(satellite.svid);
            // Navigate back to map with this satellite highlighted
            finish();
        });

        String band = UiKit.bandName(satellite.carrierFrequencyHz);
        TextView title = new TextView(this);
        title.setText(String.format(Locale.US, "%s · SV %d%s",
                satellite.constellation, satellite.svid, band.isEmpty() ? "" : " · " + band));
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.rgb(16, 42, 67));
        card.addView(title);

        TextView statusBadge = new TextView(this);
        statusBadge.setText(satellite.usedInFix ? "✓ Usado en fix" : "No usado en fix");
        statusBadge.setTextSize(12);
        statusBadge.setTypeface(Typeface.DEFAULT_BOLD);
        statusBadge.setTextColor(satellite.usedInFix ? Color.rgb(30, 125, 88) : Color.rgb(160, 100, 0));
        statusBadge.setPadding(0, dp(3), 0, dp(3));
        card.addView(statusBadge);

        TextView detail = new TextView(this);
        detail.setText(String.format(Locale.US,
                "Elev %.0f° · Azimut %.0f° · CN0 %.1f dBHz",
                satellite.elevation, satellite.azimuth, satellite.signal));
        detail.setTextSize(13);
        detail.setTextColor(Color.rgb(72, 84, 98));
        detail.setPadding(0, dp(2), 0, 0);
        card.addView(detail);

        // Baseband CN0 (API 30+)
        if (!Float.isNaN(satellite.basebandCn0DbHz)) {
            TextView bb = new TextView(this);
            bb.setText(String.format(Locale.US, "Baseband CN0: %.1f dBHz", satellite.basebandCn0DbHz));
            bb.setTextSize(12);
            bb.setTextColor(Color.rgb(100, 120, 140));
            bb.setPadding(0, dp(2), 0, 0);
            card.addView(bb);
        }

        // Exact frequency
        if (!Float.isNaN(satellite.carrierFrequencyHz) && satellite.carrierFrequencyHz > 0) {
            TextView freq = new TextView(this);
            freq.setText(String.format(Locale.US, "Frecuencia: %.3f MHz",
                    satellite.carrierFrequencyHz / 1_000_000f));
            freq.setTextSize(12);
            freq.setTextColor(Color.rgb(100, 120, 140));
            freq.setPadding(0, dp(2), 0, 0);
            card.addView(freq);
        }

        TextView orbit = new TextView(this);
        orbit.setText(String.format(Locale.US, "Efemerides: %s · Almanaque: %s",
                satellite.hasEphemeris ? "si" : "no",
                satellite.hasAlmanac ? "si" : "no"));
        orbit.setTextSize(12);
        orbit.setTextColor(Color.rgb(120, 132, 146));
        orbit.setPadding(0, dp(2), 0, 0);
        card.addView(orbit);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(8), 0, 0);
        card.setLayoutParams(params);
        return card;
    }

    private LinearLayout buildButtonRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(18), 0, dp(18), dp(8));

        TextView mapButton = UiKit.navButton(this, "Mapa", false);
        mapButton.setOnClickListener(v -> finish());
        row.addView(mapButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(dp(8), 0, 0, 0);
        row.addView(UiKit.navButton(this, "Satelites", true), p);
        return row;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
