package com.example.gpssatellites;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.GnssStatus;
import android.location.LocationManager;
import android.location.LocationListener;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_LOCATION = 42;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<SatelliteInfo> satellites = new ArrayList<>();

    private LocationManager locationManager;
    private SkyPlotView skyPlotView;
    private TextView statusView;
    private TextView summaryView;
    private TextView actionView;
    private LinearLayout listLayout;
    private boolean gnssStarted;

    private final GnssStatus.Callback gnssCallback = new GnssStatus.Callback() {
        @Override
        public void onStarted() {
            statusView.setText("GPS activo, esperando satelites");
        }

        @Override
        public void onStopped() {
            statusView.setText("GPS detenido");
        }

        @Override
        public void onFirstFix(int ttffMillis) {
            statusView.setText("Primera posicion en " + (ttffMillis / 1000f) + " s");
        }

        @Override
        public void onSatelliteStatusChanged(GnssStatus status) {
            satellites.clear();
            for (int i = 0; i < status.getSatelliteCount(); i++) {
                SatelliteInfo satellite = new SatelliteInfo(
                        status.getSvid(i),
                        constellationName(status.getConstellationType(i)),
                        status.getConstellationType(i),
                        status.getAzimuthDegrees(i),
                        status.getElevationDegrees(i),
                        status.getCn0DbHz(i),
                        status.usedInFix(i),
                        status.hasAlmanacData(i),
                        status.hasEphemerisData(i),
                        carrierFrequency(status, i),
                        basebandSignal(status, i)
                );
                satellites.add(satellite);
            }
            Collections.sort(satellites, new Comparator<SatelliteInfo>() {
                @Override
                public int compare(SatelliteInfo left, SatelliteInfo right) {
                    if (left.usedInFix != right.usedInFix) {
                        return left.usedInFix ? -1 : 1;
                    }
                    return Float.compare(right.signal, left.signal);
                }
            });
            updateUi();
        }
    };

    private final LocationListener gpsWarmupListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            if (satellites.isEmpty()) {
                updateUi();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        buildUi();
        requestLocationPermission();
        updateUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startGnss();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopGnss();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION) {
            startGnss();
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246, 247, 249));

        TextView title = new TextView(this);
        title.setText("GPS Satellites");
        title.setTextSize(28);
        title.setTextColor(Color.rgb(16, 42, 67));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(18), dp(20), dp(18), dp(4));
        root.addView(title);

        statusView = new TextView(this);
        statusView.setText("Se necesita permiso de ubicacion precisa");
        statusView.setTextSize(16);
        statusView.setTextColor(Color.WHITE);
        statusView.setTypeface(Typeface.DEFAULT_BOLD);
        statusView.setPadding(dp(18), dp(10), dp(18), dp(10));
        statusView.setBackgroundColor(Color.rgb(32, 92, 122));
        root.addView(statusView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        summaryView = new TextView(this);
        summaryView.setText("Centro: arriba de tu cabeza. Borde: horizonte. El mapa marca NORTE, SUR, ESTE y OESTE.");
        summaryView.setTextSize(14);
        summaryView.setTextColor(Color.rgb(72, 84, 98));
        summaryView.setPadding(dp(18), dp(12), dp(18), dp(10));
        root.addView(summaryView);

        actionView = new TextView(this);
        actionView.setText("Abrir ajustes de ubicacion");
        actionView.setTextSize(14);
        actionView.setTextColor(Color.WHITE);
        actionView.setTypeface(Typeface.DEFAULT_BOLD);
        actionView.setGravity(Gravity.CENTER);
        actionView.setPadding(dp(16), dp(12), dp(16), dp(12));
        actionView.setBackgroundColor(Color.rgb(16, 42, 67));
        actionView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            }
        });
        root.addView(actionView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        skyPlotView = new SkyPlotView(this);
        root.addView(skyPlotView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(360)
        ));

        ScrollView scrollView = new ScrollView(this);
        listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        listLayout.setPadding(dp(14), dp(8), dp(14), dp(18));
        scrollView.addView(listLayout);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        setContentView(root);
    }

    private void requestLocationPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
        }
    }

    private void startGnss() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            statusView.setText("Activa el permiso de ubicacion precisa para ver satelites");
            actionView.setText("Conceder ubicacion precisa");
            actionView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    requestLocationPermission();
                }
            });
            return;
        }
        if (locationManager == null || !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            statusView.setText("Activa GPS/Ubicacion en el telefono");
            actionView.setText("Abrir ajustes de ubicacion");
            actionView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                }
            });
            return;
        }
        try {
            if (!gnssStarted) {
                locationManager.registerGnssStatusCallback(gnssCallback, handler);
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        1000L,
                        0f,
                        gpsWarmupListener,
                        Looper.getMainLooper()
                );
                gnssStarted = true;
            }
            statusView.setText("Escaneando satelites GPS");
            actionView.setText("Reintentar lectura");
            actionView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    restartGnss();
                }
            });
        } catch (SecurityException securityException) {
            statusView.setText("Sin permiso de ubicacion precisa");
            actionView.setText("Conceder ubicacion precisa");
        } catch (RuntimeException runtimeException) {
            statusView.setText("No se pudo iniciar GNSS");
            actionView.setText("Reintentar lectura");
        }
    }

    private void stopGnss() {
        if (locationManager != null) {
            locationManager.unregisterGnssStatusCallback(gnssCallback);
            locationManager.removeUpdates(gpsWarmupListener);
        }
        gnssStarted = false;
    }

    private void restartGnss() {
        stopGnss();
        startGnss();
        updateUi();
    }

    private void updateUi() {
        int used = 0;
        for (SatelliteInfo satellite : satellites) {
            if (satellite.usedInFix) {
                used++;
            }
        }

        summaryView.setText(String.format(
                Locale.US,
                "%d satelites visibles. %d usados para posicion. Los puntos verdes se usan; los amarillos solo estan visibles.",
                satellites.size(),
                used
        ));

        skyPlotView.setSatellites(satellites);
        updateList();
    }

    private void updateList() {
        listLayout.removeAllViews();
        TextView sectionTitle = rowText("Listado completo de satelites", 18, true);
        sectionTitle.setTextColor(Color.rgb(16, 42, 67));
        sectionTitle.setPadding(0, 0, 0, dp(8));
        listLayout.addView(sectionTitle);

        if (satellites.isEmpty()) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            row.setBackground(cardBackground());

            TextView empty = rowText("Todavia no hay satelites visibles.", 15, true);
            empty.setTextColor(Color.rgb(16, 42, 67));
            row.addView(empty);

            TextView hint = rowText("Sal al exterior, activa Ubicacion precisa y espera unos segundos para que el receptor levante el cielo.", 13, false);
            hint.setTextColor(Color.rgb(72, 84, 98));
            hint.setPadding(0, dp(6), 0, 0);
            row.addView(hint);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 0, dp(8));
            listLayout.addView(row, params);
            return;
        }

        for (SatelliteInfo satellite : satellites) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            row.setBackground(cardBackground());

            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(header);

            TextView id = rowText(satellite.constellation + " " + satellite.svid, 16, true);
            header.addView(id, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView fix = rowText(satellite.usedInFix ? "USADO" : "VISIBLE", 12, true);
            fix.setTextColor(satellite.usedInFix ? Color.rgb(30, 125, 88) : Color.rgb(184, 122, 28));
            fix.setGravity(Gravity.RIGHT);
            header.addView(fix);

            addDetail(row, "Constelacion", satellite.constellation);
            addDetail(row, "SVID", String.valueOf(satellite.svid));
            addDetail(row, "Tipo Android", String.valueOf(satellite.constellationType));
            addDetail(row, "Azimut", formatDegrees(satellite.azimuth) + " desde el norte");
            addDetail(row, "Elevacion", formatDegrees(satellite.elevation) + " sobre el horizonte");
            addDetail(row, "Senal C/N0", formatDb(satellite.signal));
            addDetail(row, "Usado en posicion", satellite.usedInFix ? "Si" : "No");
            addDetail(row, "Almanaque", satellite.hasAlmanac ? "Disponible" : "No informado");
            addDetail(row, "Efemerides", satellite.hasEphemeris ? "Disponibles" : "No informadas");
            addDetail(row, "Frecuencia portadora", satellite.carrierFrequencyHz > 0f
                    ? String.format(Locale.US, "%.3f MHz", satellite.carrierFrequencyHz / 1_000_000f)
                    : "No informada");
            addDetail(row, "Baseband C/N0", Float.isNaN(satellite.basebandCn0DbHz)
                    ? "No informado"
                    : formatDb(satellite.basebandCn0DbHz));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 0, dp(8));
            listLayout.addView(row, params);
        }
    }

    private void addDetail(LinearLayout row, String label, String value) {
        TextView detail = rowText(label + ": " + value, 13, false);
        detail.setTextColor(Color.rgb(72, 84, 98));
        detail.setPadding(0, dp(4), 0, 0);
        row.addView(detail);
    }

    private TextView rowText(String text, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(Color.rgb(23, 33, 43));
        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return view;
    }

    private GradientDrawable cardBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.WHITE);
        drawable.setStroke(dp(1), Color.rgb(219, 226, 235));
        drawable.setCornerRadius(dp(8));
        return drawable;
    }

    private float carrierFrequency(GnssStatus status, int index) {
        if (Build.VERSION.SDK_INT >= 26 && status.hasCarrierFrequencyHz(index)) {
            return status.getCarrierFrequencyHz(index);
        }
        return Float.NaN;
    }

    private float basebandSignal(GnssStatus status, int index) {
        if (Build.VERSION.SDK_INT >= 30 && status.hasBasebandCn0DbHz(index)) {
            return status.getBasebandCn0DbHz(index);
        }
        return Float.NaN;
    }

    private String formatDegrees(float value) {
        return String.format(Locale.US, "%.1f deg", value);
    }

    private String formatDb(float value) {
        return String.format(Locale.US, "%.1f dB-Hz", value);
    }

    private String formatSignal(float value) {
        return String.format(Locale.US, "%.1f dB-Hz", value);
    }

    private String constellationName(int constellation) {
        switch (constellation) {
            case GnssStatus.CONSTELLATION_GPS:
                return "GPS";
            case GnssStatus.CONSTELLATION_GLONASS:
                return "GLONASS";
            case GnssStatus.CONSTELLATION_GALILEO:
                return "Galileo";
            case GnssStatus.CONSTELLATION_BEIDOU:
                return "BeiDou";
            case GnssStatus.CONSTELLATION_QZSS:
                return "QZSS";
            case GnssStatus.CONSTELLATION_SBAS:
                return "SBAS";
            case GnssStatus.CONSTELLATION_IRNSS:
                return "IRNSS";
            default:
                return "GNSS";
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class SatelliteInfo {
        final int svid;
        final String constellation;
        final int constellationType;
        final float azimuth;
        final float elevation;
        final float signal;
        final boolean usedInFix;
        final boolean hasAlmanac;
        final boolean hasEphemeris;
        final float carrierFrequencyHz;
        final float basebandCn0DbHz;

        SatelliteInfo(
                int svid,
                String constellation,
                int constellationType,
                float azimuth,
                float elevation,
                float signal,
                boolean usedInFix,
                boolean hasAlmanac,
                boolean hasEphemeris,
                float carrierFrequencyHz,
                float basebandCn0DbHz
        ) {
            this.svid = svid;
            this.constellation = constellation;
            this.constellationType = constellationType;
            this.azimuth = azimuth;
            this.elevation = elevation;
            this.signal = signal;
            this.usedInFix = usedInFix;
            this.hasAlmanac = hasAlmanac;
            this.hasEphemeris = hasEphemeris;
            this.carrierFrequencyHz = carrierFrequencyHz;
            this.basebandCn0DbHz = basebandCn0DbHz;
        }
    }

    private static final class SkyPlotView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF plotBounds = new RectF();
        private final List<SatelliteInfo> satellites = new ArrayList<>();

        SkyPlotView(Context context) {
            super(context);
            setBackgroundColor(Color.rgb(246, 247, 249));
        }

        void setSatellites(List<SatelliteInfo> newSatellites) {
            satellites.clear();
            satellites.addAll(newSatellites);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float size = Math.min(getWidth() - dp(86), getHeight() - dp(60));
            float centerX = getWidth() / 2f;
            float centerY = getHeight() / 2f;
            float radius = size / 2f;
            plotBounds.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);

            drawGrid(canvas, centerX, centerY, radius);
            drawSatellites(canvas, centerX, centerY, radius);
        }

        private void drawGrid(Canvas canvas, float centerX, float centerY, float radius) {
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

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(16, 42, 67));
            paint.setTextSize(dp(16));
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            drawCompassLabel(canvas, "NORTE", centerX, centerY - radius - dp(28));
            drawCompassLabel(canvas, "SUR", centerX, centerY + radius + dp(30));
            drawCompassLabel(canvas, "OESTE", centerX - radius - dp(34), centerY);
            drawCompassLabel(canvas, "ESTE", centerX + radius + dp(34), centerY);
        }

        private void drawCompassLabel(Canvas canvas, String label, float centerX, float centerY) {
            paint.setTextSize(dp(13));
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            float width = paint.measureText(label) + dp(18);
            float height = dp(26);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(16, 42, 67));
            canvas.drawRoundRect(
                    centerX - width / 2f,
                    centerY - height / 2f,
                    centerX + width / 2f,
                    centerY + height / 2f,
                    dp(11),
                    dp(11),
                    paint
            );

            paint.setColor(Color.WHITE);
            canvas.drawText(label, centerX, centerY + dp(5), paint);
        }

        private void drawSatellites(Canvas canvas, float centerX, float centerY, float radius) {
            for (SatelliteInfo satellite : satellites) {
                float distance = radius * (1f - Math.max(0f, Math.min(90f, satellite.elevation)) / 90f);
                double angle = Math.toRadians(satellite.azimuth - 90f);
                float x = centerX + (float) Math.cos(angle) * distance;
                float y = centerY + (float) Math.sin(angle) * distance;
                float dotRadius = satellite.usedInFix ? dp(11) : dp(9);

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
        }

        private int dp(int value) {
            return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
        }
    }
}
