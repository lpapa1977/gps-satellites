package com.example.gpssatellites;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.GeomagneticField;
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

import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements GnssDataStore.Listener, SensorEventListener {
    private GnssDataStore dataStore;
    private TextView statusView;
    private TextView summaryView;
    private TextView locationView;
    private TextView actionView;
    private SkyPlotView skyPlotView;
    private LinearLayout infoPanel;

    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private final float[] rotationMatrix = new float[9];
    private final float[] orientation = new float[3];
    private float declination;
    private float smoothedHeading;
    private boolean hasHeading;

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
        locationView.setText(formatLocation(state.location));
        if (state.location != null) {
            GeomagneticField field = new GeomagneticField(
                    (float) state.location.getLatitude(),
                    (float) state.location.getLongitude(),
                    (float) state.location.getAltitude(),
                    System.currentTimeMillis());
            declination = field.getDeclination();
        }
        updateAction(state);
        skyPlotView.setSelectedSvid(state.selectedSvid);
        updateInfoPanel(state);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) {
            return;
        }
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

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246, 247, 249));

        root.addView(sectionHeader("GPS Satellites"));
        root.addView(buttonRow());

        statusView = chip("Esperando ubicacion");
        root.addView(statusView);

        summaryView = bodyText("Mapa del cielo: el centro es el cenit y el borde es el horizonte.");
        summaryView.setPadding(dp(18), dp(12), dp(18), dp(4));
        root.addView(summaryView);

        locationView = bodyText("Ubicacion actual: esperando fix");
        locationView.setPadding(dp(18), dp(4), dp(18), dp(10));
        root.addView(locationView);

        actionView = actionButton("Abrir ajustes de ubicacion");
        root.addView(actionView);

        skyPlotView = new SkyPlotView(this);
        skyPlotView.setOnSatelliteTapListener(new SkyPlotView.OnSatelliteTapListener() {
            @Override
            public void onSatelliteTapped(GnssDataStore.SatelliteInfo satellite) {
                dataStore.selectSatellite(satellite.svid);
            }
        });
        root.addView(skyPlotView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(380)
        ));

        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(209, 218, 228));
        root.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));

        infoPanel = new LinearLayout(this);
        infoPanel.setOrientation(LinearLayout.VERTICAL);
        infoPanel.setPadding(dp(18), dp(16), dp(18), dp(24));
        TextView placeholder = bodyText("Toca un satelite en el mapa para ver sus datos");
        placeholder.setTextColor(Color.rgb(160, 168, 178));
        infoPanel.addView(placeholder);
        root.addView(infoPanel);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.rgb(246, 247, 249));
        scrollView.addView(root);
        setContentView(scrollView);
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

        TextView mapButton = navButton("Mapa", true);
        row.addView(mapButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView satellitesButton = navButton("Satelites", false);
        satellitesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SatellitesActivity.class));
            }
        });
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

    private TextView actionButton(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(Color.WHITE);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(16), dp(12), dp(16), dp(12));
        view.setBackgroundColor(Color.rgb(16, 42, 67));
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(dp(18), dp(4), dp(18), dp(12));
        view.setLayoutParams(params);
        return view;
    }

    private void updateAction(final GnssDataStore.GnssState state) {
        if (!state.permissionGranted) {
            actionView.setText("Conceder ubicacion precisa");
            actionView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 42);
                }
            });
            return;
        }
        if (!state.gpsEnabled) {
            actionView.setText("Abrir ajustes de ubicacion");
            actionView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                }
            });
            return;
        }
        actionView.setText("Abrir ajustes de ubicacion");
        actionView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            }
        });
    }

    private String formatLocation(Location location) {
        if (location == null) {
            return "Ubicacion actual: esperando fix GPS";
        }
        return String.format(
                Locale.US,
                "Ubicacion actual: %.6f, %.6f | precision %.1f m%s",
                location.getLatitude(),
                location.getLongitude(),
                location.hasAccuracy() ? location.getAccuracy() : 0f,
                location.hasAltitude() ? String.format(Locale.US, " | altitud %.1f m", location.getAltitude()) : ""
        );
    }

    private void updateInfoPanel(GnssDataStore.GnssState state) {
        infoPanel.removeAllViews();

        GnssDataStore.SatelliteInfo selected = null;
        if (state.selectedSvid != -1) {
            for (GnssDataStore.SatelliteInfo sat : state.satellites) {
                if (sat.svid == state.selectedSvid) {
                    selected = sat;
                    break;
                }
            }
        }

        if (selected == null) {
            TextView placeholder = bodyText("Toca un satelite en el mapa para ver sus datos");
            placeholder.setTextColor(Color.rgb(160, 168, 178));
            infoPanel.addView(placeholder);
            return;
        }

        String band = bandName(selected.carrierFrequencyHz);

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

        addInfoRow(infoPanel, "Elevacion", String.format(Locale.US, "%.0f°", selected.elevation));
        addInfoRow(infoPanel, "Azimut", String.format(Locale.US, "%.0f°", selected.azimuth));
        addInfoRow(infoPanel, "Senal (CN0)", String.format(Locale.US, "%.0f dBHz", selected.signal));
        addInfoRow(infoPanel, "Efemerides", selected.hasEphemeris ? "si" : "no");
        addInfoRow(infoPanel, "Almanaque", selected.hasAlmanac ? "si" : "no");
    }

    private void addInfoRow(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(3), 0, dp(3));

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(13);
        labelView.setTextColor(Color.rgb(120, 132, 146));
        row.addView(labelView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextSize(13);
        valueView.setTypeface(Typeface.DEFAULT_BOLD);
        valueView.setTextColor(Color.rgb(16, 42, 67));
        row.addView(valueView);

        parent.addView(row);
    }

    private String bandName(float carrierFrequencyHz) {
        if (Float.isNaN(carrierFrequencyHz) || carrierFrequencyHz <= 0f) {
            return "";
        }
        float mhz = carrierFrequencyHz / 1_000_000f;
        if (mhz > 1565f && mhz < 1615f) return "L1";
        if (mhz > 1215f && mhz < 1255f) return "L2";
        if (mhz > 1192f && mhz < 1212f) return "E5b";
        if (mhz > 1160f && mhz < 1192f) return "L5";
        return String.format(Locale.US, "%.0f MHz", mhz);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class SkyPlotView extends View {
        interface OnSatelliteTapListener {
            void onSatelliteTapped(GnssDataStore.SatelliteInfo satellite);
        }

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<GnssDataStore.SatelliteInfo> satellites = new java.util.ArrayList<>();
        private final RectF labelBounds = new RectF();
        private float headingDegrees;
        private int selectedSvid = -1;
        private OnSatelliteTapListener tapListener;

        SkyPlotView(android.content.Context context) {
            super(context);
            setBackgroundColor(Color.rgb(246, 247, 249));
        }

        void setSatellites(List<GnssDataStore.SatelliteInfo> items) {
            satellites.clear();
            satellites.addAll(items);
            invalidate();
        }

        void setHeading(float degrees) {
            if (Math.abs(degrees - headingDegrees) < 0.5f) {
                return;
            }
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
                if (sat != null) {
                    tapListener.onSatelliteTapped(sat);
                }
            }
            return true;
        }

        private GnssDataStore.SatelliteInfo findSatelliteAt(float touchX, float touchY) {
            float width = getWidth();
            float height = getHeight();
            float radius = Math.min(width, height) * 0.38f;
            float centerX = width / 2f;
            float centerY = height / 2f;

            // Inverse-rotate the touch point to match the rotated canvas space
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
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = satellite;
                }
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

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(centerX, centerY, radius, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.rgb(209, 218, 228));
            canvas.drawCircle(centerX, centerY, radius, paint);
            canvas.drawCircle(centerX, centerY, radius * 0.66f, paint);
            canvas.drawCircle(centerX, centerY, radius * 0.33f, paint);
            canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, paint);
            canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, paint);

            drawCompassBadge(canvas, "NORTE", centerX, centerY - radius + dp(24));
            drawCompassBadge(canvas, "SUR", centerX, centerY + radius - dp(24));
            drawCompassBadge(canvas, "OESTE", centerX - radius + dp(34), centerY);
            drawCompassBadge(canvas, "ESTE", centerX + radius - dp(34), centerY);

            for (GnssDataStore.SatelliteInfo satellite : satellites) {
                float distance = radius * (1f - Math.max(0f, Math.min(90f, satellite.elevation)) / 90f);
                double angle = Math.toRadians(satellite.azimuth - 90f);
                float x = centerX + (float) Math.cos(angle) * distance;
                float y = centerY + (float) Math.sin(angle) * distance;
                float dotRadius = satellite.usedInFix ? dp(11) : dp(9);

                if (satellite.svid == selectedSvid) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(3));
                    paint.setColor(Color.rgb(16, 42, 67));
                    canvas.drawCircle(x, y, dotRadius + dp(7), paint);
                }

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(satellite.usedInFix ? Color.rgb(30, 125, 88) : Color.rgb(244, 176, 55));
                canvas.drawCircle(x, y, dotRadius, paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                paint.setColor(Color.WHITE);
                canvas.drawCircle(x, y, dotRadius, paint);

                paint.setStyle(Paint.Style.FILL);
                paint.setTextSize(dp(10));
                paint.setTypeface(Typeface.DEFAULT_BOLD);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setColor(Color.WHITE);
                canvas.drawText(String.valueOf(satellite.svid), x, y + dp(4), paint);
            }

            canvas.restore();
        }

        private void drawCompassBadge(Canvas canvas, String label, float centerX, float centerY) {
            paint.setTextSize(dp(12));
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            float textWidth = paint.measureText(label);
            float width = textWidth + dp(20);
            float height = dp(26);
            labelBounds.set(centerX - width / 2f, centerY - height / 2f, centerX + width / 2f, centerY + height / 2f);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(16, 42, 67));
            canvas.drawRoundRect(labelBounds, dp(11), dp(11), paint);

            paint.setColor(Color.WHITE);
            canvas.drawText(label, centerX, centerY + dp(5), paint);
        }

        private int dp(int value) {
            return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
        }
    }
}
