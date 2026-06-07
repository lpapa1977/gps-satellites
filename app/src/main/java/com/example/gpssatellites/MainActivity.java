package com.example.gpssatellites;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.location.GnssStatus;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
    private LinearLayout listLayout;

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
                        status.getAzimuthDegrees(i),
                        status.getElevationDegrees(i),
                        status.getCn0DbHz(i),
                        status.usedInFix(i)
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        buildUi();
        requestLocationPermission();
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
        summaryView.setText("Centro: arriba de tu cabeza. Borde: horizonte. N es norte.");
        summaryView.setTextSize(14);
        summaryView.setTextColor(Color.rgb(72, 84, 98));
        summaryView.setPadding(dp(18), dp(12), dp(18), dp(10));
        root.addView(summaryView);

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
            return;
        }
        if (locationManager == null || !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            statusView.setText("Activa GPS/Ubicacion en el telefono");
            return;
        }
        try {
            locationManager.registerGnssStatusCallback(gnssCallback, handler);
            statusView.setText("Escaneando satelites GPS");
        } catch (SecurityException securityException) {
            statusView.setText("Sin permiso de ubicacion precisa");
        } catch (RuntimeException runtimeException) {
            statusView.setText("No se pudo iniciar GNSS");
        }
    }

    private void stopGnss() {
        if (locationManager != null) {
            locationManager.unregisterGnssStatusCallback(gnssCallback);
        }
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
        if (satellites.isEmpty()) {
            TextView empty = rowText("Todavia no hay datos. Salir al exterior mejora la recepcion.", 15, true);
            empty.setPadding(dp(12), dp(14), dp(12), dp(14));
            listLayout.addView(empty);
            return;
        }

        for (SatelliteInfo satellite : satellites) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setBackgroundColor(Color.WHITE);

            TextView id = rowText(satellite.constellation + " " + satellite.svid, 15, true);
            row.addView(id, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView details = rowText(String.format(
                    Locale.US,
                    "%.0f deg az | %.0f deg el | %.1f dB",
                    satellite.azimuth,
                    satellite.elevation,
                    satellite.signal
            ), 13, false);
            details.setGravity(Gravity.RIGHT);
            row.addView(details);

            TextView fix = rowText(satellite.usedInFix ? " USADO" : " VISIBLE", 12, true);
            fix.setTextColor(satellite.usedInFix ? Color.rgb(30, 125, 88) : Color.rgb(184, 122, 28));
            fix.setGravity(Gravity.RIGHT);
            row.addView(fix);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 0, dp(8));
            listLayout.addView(row, params);
        }
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
        final float azimuth;
        final float elevation;
        final float signal;
        final boolean usedInFix;

        SatelliteInfo(int svid, String constellation, float azimuth, float elevation, float signal, boolean usedInFix) {
            this.svid = svid;
            this.constellation = constellation;
            this.azimuth = azimuth;
            this.elevation = elevation;
            this.signal = signal;
            this.usedInFix = usedInFix;
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
            float size = Math.min(getWidth() - dp(36), getHeight() - dp(24));
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
            paint.setTextSize(dp(14));
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("N", centerX, centerY - radius - dp(7), paint);
            canvas.drawText("S", centerX, centerY + radius + dp(18), paint);
            canvas.drawText("O", centerX - radius - dp(12), centerY + dp(5), paint);
            canvas.drawText("E", centerX + radius + dp(12), centerY + dp(5), paint);
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
