package com.example.gpssatellites;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements GnssDataStore.Listener, SensorEventListener {
    private GnssDataStore dataStore;
    private TextView statusView;
    private TextView summaryView;
    private TextView locationView;
    private TextView diagnosisView;
    private TextView actionView;
    private TextView fixHistoryView;
    private SkyPlotView skyPlotView;
    private LinearLayout infoPanel;

    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private final float[] rotationMatrix = new float[9];
    private final float[] orientation = new float[3];
    private float declination;
    private float smoothedHeading;
    private boolean hasHeading;

    // cached to avoid recomputing on every GNSS update
    private GeomagneticField geoField;
    private Location lastFieldLocation;

    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm:ss", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dataStore = GnssDataStore.get(this);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        dataStore.addListener(this);
        dataStore.start();
        if (sensorManager != null && rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        dataStore.removeListener(this);
        dataStore.stop();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onGnssStateChanged(GnssDataStore.GnssState state) {
        statusView.setText(state.statusText);
        summaryView.setText(state.summaryText);
        skyPlotView.setSatellites(state.satellites);
        locationView.setText(formatLocation(state.location, state.ttffMs));
        updateDiagnosis(state);
        updateFixHistory(state);
        updateGeoField(state.location);
        updateAction(state);
        skyPlotView.setSelectedSvid(state.selectedSvid);
        updateInfoPanel(state);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
        SensorManager.getOrientation(rotationMatrix, orientation);
        float trueHeading = (float) Math.toDegrees(orientation[0]) + declination;
        trueHeading = (trueHeading % 360f + 360f) % 360f;
        if (!hasHeading) {
            smoothedHeading = trueHeading;
            hasHeading = true;
        } else {
            float delta = ((trueHeading - smoothedHeading + 540f) % 360f) - 180f;
            smoothedHeading = (smoothedHeading + delta * 0.15f + 360f) % 360f;
        }
        skyPlotView.setHeading(smoothedHeading);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void updateGeoField(Location location) {
        if (location == null) return;
        if (lastFieldLocation != null && location.distanceTo(lastFieldLocation) < 5000f) return;
        geoField = new GeomagneticField(
                (float) location.getLatitude(),
                (float) location.getLongitude(),
                (float) location.getAltitude(),
                System.currentTimeMillis());
        declination = geoField.getDeclination();
        lastFieldLocation = location;
    }

    private void updateDiagnosis(GnssDataStore.GnssState state) {
        if (state.fixDiagnosis.isEmpty()) {
            diagnosisView.setVisibility(View.GONE);
        } else {
            diagnosisView.setText("⚠ " + state.fixDiagnosis);
            diagnosisView.setVisibility(View.VISIBLE);
        }
    }

    private void updateFixHistory(GnssDataStore.GnssState state) {
        if (state.fixHistory.isEmpty()) {
            fixHistoryView.setVisibility(View.GONE);
            return;
        }
        StringBuilder sb = new StringBuilder("Ultimos fixes: ");
        for (int i = state.fixHistory.size() - 1; i >= 0; i--) {
            if (i < state.fixHistory.size() - 1) sb.append(" · ");
            sb.append(TIME_FMT.format(new Date(state.fixHistory.get(i))));
        }
        fixHistoryView.setText(sb.toString());
        fixHistoryView.setVisibility(View.VISIBLE);
    }

    private void updateAction(GnssDataStore.GnssState state) {
        if (!state.permissionGranted) {
            actionView.setText("Conceder ubicacion precisa");
            actionView.setVisibility(View.VISIBLE);
            actionView.setOnClickListener(v ->
                    requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 42));
            return;
        }
        if (!state.gpsEnabled) {
            actionView.setText("Abrir ajustes de ubicacion");
            actionView.setVisibility(View.VISIBLE);
            actionView.setOnClickListener(v ->
                    startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
            return;
        }
        actionView.setVisibility(View.GONE);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246, 247, 249));

        root.addView(UiKit.sectionHeader(this, "GPS Satellites"));
        root.addView(buildButtonRow());

        statusView = UiKit.chip(this, "Esperando ubicacion");
        root.addView(statusView);

        summaryView = UiKit.bodyText(this, "Mapa del cielo: el centro es el cenit y el borde es el horizonte.");
        summaryView.setPadding(dp(18), dp(12), dp(18), dp(4));
        root.addView(summaryView);

        locationView = UiKit.bodyText(this, "Ubicacion actual: esperando fix");
        locationView.setPadding(dp(18), dp(4), dp(18), dp(2));
        root.addView(locationView);

        diagnosisView = UiKit.bodyText(this, "");
        diagnosisView.setTextColor(Color.rgb(180, 100, 0));
        diagnosisView.setPadding(dp(18), dp(2), dp(18), dp(2));
        diagnosisView.setVisibility(View.GONE);
        root.addView(diagnosisView);

        fixHistoryView = UiKit.bodyText(this, "");
        fixHistoryView.setTextColor(Color.rgb(100, 120, 140));
        fixHistoryView.setTextSize(12);
        fixHistoryView.setPadding(dp(18), dp(2), dp(18), dp(8));
        fixHistoryView.setVisibility(View.GONE);
        root.addView(fixHistoryView);

        actionView = buildActionButton();
        root.addView(actionView);

        skyPlotView = new SkyPlotView(this);
        skyPlotView.setOnSatelliteTapListener(satellite -> dataStore.selectSatellite(satellite.svid));
        root.addView(skyPlotView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(380)));

        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(209, 218, 228));
        root.addView(divider, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));

        infoPanel = new LinearLayout(this);
        infoPanel.setOrientation(LinearLayout.VERTICAL);
        infoPanel.setPadding(dp(18), dp(16), dp(18), dp(24));
        TextView placeholder = UiKit.bodyText(this, "Toca un satelite en el mapa para ver sus datos");
        placeholder.setTextColor(Color.rgb(160, 168, 178));
        infoPanel.addView(placeholder);
        root.addView(infoPanel);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.rgb(246, 247, 249));
        scrollView.addView(root);
        setContentView(scrollView);
    }

    private LinearLayout buildButtonRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(18), 0, dp(18), dp(8));

        row.addView(UiKit.navButton(this, "Mapa", true),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView satellitesButton = UiKit.navButton(this, "Satelites", false);
        satellitesButton.setOnClickListener(v -> startActivity(new Intent(this, SatellitesActivity.class)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(dp(8), 0, 0, 0);
        row.addView(satellitesButton, p);
        return row;
    }

    private TextView buildActionButton() {
        TextView view = new TextView(this);
        view.setTextSize(14);
        view.setTextColor(Color.WHITE);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(16), dp(12), dp(16), dp(12));
        view.setBackgroundColor(Color.rgb(16, 42, 67));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(18), dp(4), dp(18), dp(12));
        view.setLayoutParams(params);
        return view;
    }

    private String formatLocation(Location location, long ttffMs) {
        if (location == null) return "Ubicacion actual: esperando fix GPS";
        String ttff = ttffMs > 0
                ? String.format(Locale.US, " | TTFF %.1f s", ttffMs / 1000f)
                : "";
        return String.format(Locale.US,
                "Ubicacion: %.6f, %.6f | prec %.1f m%s%s",
                location.getLatitude(),
                location.getLongitude(),
                location.hasAccuracy() ? location.getAccuracy() : 0f,
                location.hasAltitude() ? String.format(Locale.US, " | alt %.1f m", location.getAltitude()) : "",
                ttff
        );
    }

    private void updateInfoPanel(GnssDataStore.GnssState state) {
        infoPanel.removeAllViews();

        GnssDataStore.SatelliteInfo selected = null;
        if (state.selectedSvid != -1) {
            for (GnssDataStore.SatelliteInfo sat : state.satellites) {
                if (sat.svid == state.selectedSvid) { selected = sat; break; }
            }
        }

        if (selected == null) {
            TextView placeholder = UiKit.bodyText(this, "Toca un satelite en el mapa para ver sus datos");
            placeholder.setTextColor(Color.rgb(160, 168, 178));
            infoPanel.addView(placeholder);
            return;
        }

        String band = UiKit.bandName(selected.carrierFrequencyHz);

        TextView title = new TextView(this);
        title.setText(String.format(Locale.US, "%s · SV %d%s",
                selected.constellation, selected.svid, band.isEmpty() ? "" : " · " + band));
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.rgb(16, 42, 67));
        infoPanel.addView(title);

        TextView usedView = new TextView(this);
        usedView.setText(selected.usedInFix ? "Usado en fix GPS" : "No usado en fix");
        usedView.setTextSize(13);
        usedView.setTypeface(Typeface.DEFAULT_BOLD);
        usedView.setTextColor(selected.usedInFix ? Color.rgb(30, 125, 88) : Color.rgb(160, 100, 0));
        usedView.setPadding(0, dp(4), 0, dp(10));
        infoPanel.addView(usedView);

        UiKit.addInfoRow(this, infoPanel, "Elevacion", String.format(Locale.US, "%.0f°", selected.elevation));
        UiKit.addInfoRow(this, infoPanel, "Azimut", String.format(Locale.US, "%.0f°", selected.azimuth));
        UiKit.addInfoRow(this, infoPanel, "Senal (CN0)", String.format(Locale.US, "%.1f dBHz", selected.signal));
        if (!Float.isNaN(selected.basebandCn0DbHz)) {
            UiKit.addInfoRow(this, infoPanel, "Senal baseband", String.format(Locale.US, "%.1f dBHz", selected.basebandCn0DbHz));
        }
        if (!Float.isNaN(selected.carrierFrequencyHz) && selected.carrierFrequencyHz > 0) {
            UiKit.addInfoRow(this, infoPanel, "Frecuencia", String.format(Locale.US, "%.2f MHz", selected.carrierFrequencyHz / 1_000_000f));
        }
        UiKit.addInfoRow(this, infoPanel, "Efemerides", selected.hasEphemeris ? "si" : "no");
        UiKit.addInfoRow(this, infoPanel, "Almanaque", selected.hasAlmanac ? "si" : "no");
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class SkyPlotView extends View {
        interface OnSatelliteTapListener {
            void onSatelliteTapped(GnssDataStore.SatelliteInfo satellite);
        }

        // Pre-created paints — never mutated in onDraw
        private final Paint bgFill;
        private final Paint gridStroke;
        private final Paint satUsedFill;
        private final Paint satIdleFill;
        private final Paint satRingStroke;
        private final Paint satSelectedRing;
        private final Paint satTextPaint;
        private final Paint compassFill;
        private final Paint compassText;

        private final List<GnssDataStore.SatelliteInfo> satellites = new java.util.ArrayList<>();
        private final RectF labelBounds = new RectF();
        private float headingDegrees;
        private int selectedSvid = -1;
        private OnSatelliteTapListener tapListener;

        SkyPlotView(android.content.Context context) {
            super(context);
            setBackgroundColor(Color.rgb(246, 247, 249));

            bgFill = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgFill.setStyle(Paint.Style.FILL);
            bgFill.setColor(Color.WHITE);

            gridStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
            gridStroke.setStyle(Paint.Style.STROKE);
            gridStroke.setColor(Color.rgb(209, 218, 228));

            satUsedFill = new Paint(Paint.ANTI_ALIAS_FLAG);
            satUsedFill.setStyle(Paint.Style.FILL);
            satUsedFill.setColor(Color.rgb(30, 125, 88));

            satIdleFill = new Paint(Paint.ANTI_ALIAS_FLAG);
            satIdleFill.setStyle(Paint.Style.FILL);
            satIdleFill.setColor(Color.rgb(244, 176, 55));

            satRingStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
            satRingStroke.setStyle(Paint.Style.STROKE);
            satRingStroke.setColor(Color.WHITE);

            satSelectedRing = new Paint(Paint.ANTI_ALIAS_FLAG);
            satSelectedRing.setStyle(Paint.Style.STROKE);
            satSelectedRing.setColor(Color.rgb(16, 42, 67));

            satTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            satTextPaint.setStyle(Paint.Style.FILL);
            satTextPaint.setColor(Color.WHITE);
            satTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
            satTextPaint.setTextAlign(Paint.Align.CENTER);

            compassFill = new Paint(Paint.ANTI_ALIAS_FLAG);
            compassFill.setStyle(Paint.Style.FILL);
            compassFill.setColor(Color.rgb(16, 42, 67));

            compassText = new Paint(Paint.ANTI_ALIAS_FLAG);
            compassText.setStyle(Paint.Style.FILL);
            compassText.setColor(Color.WHITE);
            compassText.setTypeface(Typeface.DEFAULT_BOLD);
            compassText.setTextAlign(Paint.Align.CENTER);
        }

        void setSatellites(List<GnssDataStore.SatelliteInfo> items) {
            satellites.clear();
            satellites.addAll(items);
            invalidate();
        }

        void setHeading(float degrees) {
            if (Math.abs(degrees - headingDegrees) < 0.5f) return;
            headingDegrees = degrees;
            invalidate();
        }

        void setSelectedSvid(int svid) {
            if (selectedSvid == svid) return;
            selectedSvid = svid;
            invalidate();
        }

        void setOnSatelliteTapListener(OnSatelliteTapListener listener) {
            tapListener = listener;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP && tapListener != null) {
                GnssDataStore.SatelliteInfo sat = findSatelliteAt(event.getX(), event.getY());
                if (sat != null) tapListener.onSatelliteTapped(sat);
            }
            return true;
        }

        private GnssDataStore.SatelliteInfo findSatelliteAt(float touchX, float touchY) {
            float width = getWidth();
            float height = getHeight();
            float radius = Math.min(width, height) * 0.38f;
            float centerX = width / 2f;
            float centerY = height / 2f;

            float dx = touchX - centerX;
            float dy = touchY - centerY;
            double rad = Math.toRadians(headingDegrees);
            float rotX = centerX + (float) (dx * Math.cos(rad) - dy * Math.sin(rad));
            float rotY = centerY + (float) (dx * Math.sin(rad) + dy * Math.cos(rad));

            float tapRadius = dp(28);
            GnssDataStore.SatelliteInfo nearest = null;
            float nearestDist = tapRadius;

            for (GnssDataStore.SatelliteInfo satellite : satellites) {
                float dist2Center = radius * (1f - Math.max(0f, Math.min(90f, satellite.elevation)) / 90f);
                double angle = Math.toRadians(satellite.azimuth - 90f);
                float x = centerX + (float) Math.cos(angle) * dist2Center;
                float y = centerY + (float) Math.sin(angle) * dist2Center;
                float dist = (float) Math.hypot(rotX - x, rotY - y);
                if (dist < nearestDist) { nearestDist = dist; nearest = satellite; }
            }
            return nearest;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth();
            float height = getHeight();
            float radius = Math.min(width, height) * 0.38f;
            float centerX = width / 2f;
            float centerY = height / 2f;

            canvas.save();
            canvas.rotate(-headingDegrees, centerX, centerY);

            canvas.drawCircle(centerX, centerY, radius, bgFill);

            gridStroke.setStrokeWidth(dp(2));
            canvas.drawCircle(centerX, centerY, radius, gridStroke);
            canvas.drawCircle(centerX, centerY, radius * 0.66f, gridStroke);
            canvas.drawCircle(centerX, centerY, radius * 0.33f, gridStroke);
            gridStroke.setStrokeWidth(dp(1));
            canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, gridStroke);
            canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, gridStroke);

            drawCompassBadge(canvas, "NORTE", centerX, centerY - radius + dp(24));
            drawCompassBadge(canvas, "SUR",   centerX, centerY + radius - dp(24));
            drawCompassBadge(canvas, "OESTE", centerX - radius + dp(34), centerY);
            drawCompassBadge(canvas, "ESTE",  centerX + radius - dp(34), centerY);

            satRingStroke.setStrokeWidth(dp(2));
            satSelectedRing.setStrokeWidth(dp(3));
            satTextPaint.setTextSize(dp(10));

            for (GnssDataStore.SatelliteInfo satellite : satellites) {
                float distance = radius * (1f - Math.max(0f, Math.min(90f, satellite.elevation)) / 90f);
                double angle = Math.toRadians(satellite.azimuth - 90f);
                float x = centerX + (float) Math.cos(angle) * distance;
                float y = centerY + (float) Math.sin(angle) * distance;
                float dotRadius = satellite.usedInFix ? dp(11) : dp(9);

                if (satellite.svid == selectedSvid) {
                    canvas.drawCircle(x, y, dotRadius + dp(7), satSelectedRing);
                }
                canvas.drawCircle(x, y, dotRadius, satellite.usedInFix ? satUsedFill : satIdleFill);
                canvas.drawCircle(x, y, dotRadius, satRingStroke);
                canvas.drawText(String.valueOf(satellite.svid), x, y + dp(4), satTextPaint);
            }

            canvas.restore();
        }

        private void drawCompassBadge(Canvas canvas, String label, float cx, float cy) {
            compassText.setTextSize(dp(12));
            float textWidth = compassText.measureText(label);
            float w = textWidth + dp(20);
            float h = dp(26);
            labelBounds.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
            canvas.drawRoundRect(labelBounds, dp(11), dp(11), compassFill);
            canvas.drawText(label, cx, cy + dp(5), compassText);
        }

        private int dp(int value) {
            return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
        }
    }
}
