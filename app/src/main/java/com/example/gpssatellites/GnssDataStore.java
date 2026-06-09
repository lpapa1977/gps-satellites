package com.example.gpssatellites;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class GnssDataStore {
    interface Listener {
        void onGnssStateChanged(GnssState state);
    }

    static final class GnssState {
        final List<SatelliteInfo> satellites = new ArrayList<>();
        final List<Long> fixHistory = new ArrayList<>(); // timestamps of last 5 fixes
        Location location;
        String statusText = "Esperando permiso de ubicacion precisa";
        String summaryText = "Mapa del cielo: el centro es el cenit y el borde es el horizonte.";
        String fixDiagnosis = "";
        boolean permissionGranted;
        boolean gpsEnabled;
        boolean tracking;
        long updatedAtMs;
        long ttffMs = -1;
        int selectedSvid = -1;

        int visibleCount() {
            return satellites.size();
        }

        int usedCount() {
            int count = 0;
            for (SatelliteInfo satellite : satellites) {
                if (satellite.usedInFix) count++;
            }
            return count;
        }
    }

    static final class SatelliteInfo {
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

    private static GnssDataStore instance;

    static synchronized GnssDataStore get(Context context) {
        if (instance == null) {
            instance = new GnssDataStore(context.getApplicationContext());
        }
        return instance;
    }

    private final Context appContext;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private final GnssState state = new GnssState();
    private LocationManager locationManager;
    private boolean running;
    private int activeClients = 0; // ref count — GPS stops only when all clients release

    private final GnssStatus.Callback gnssCallback = new GnssStatus.Callback() {
        @Override
        public void onStarted() {
            state.statusText = "GPS activo, esperando satelites";
            state.tracking = true;
            notifyListeners();
        }

        @Override
        public void onStopped() {
            state.statusText = "GPS detenido";
            state.tracking = false;
            notifyListeners();
        }

        @Override
        public void onFirstFix(int ttffMillis) {
            state.ttffMs = ttffMillis;
            state.statusText = String.format(Locale.US, "Primera posicion en %.1f s", ttffMillis / 1000f);
            notifyListeners();
        }

        @Override
        public void onSatelliteStatusChanged(GnssStatus status) {
            state.satellites.clear();
            for (int i = 0; i < status.getSatelliteCount(); i++) {
                state.satellites.add(new SatelliteInfo(
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
                ));
            }
            Collections.sort(state.satellites, new Comparator<SatelliteInfo>() {
                @Override
                public int compare(SatelliteInfo left, SatelliteInfo right) {
                    if (left.usedInFix != right.usedInFix) return left.usedInFix ? -1 : 1;
                    return Float.compare(right.signal, left.signal);
                }
            });
            state.updatedAtMs = System.currentTimeMillis();
            state.summaryText = String.format(Locale.US,
                    "%d satelites visibles. %d usados para posicion.",
                    state.visibleCount(), state.usedCount());
            state.fixDiagnosis = computeDiagnosis();
            notifyListeners();
        }
    };

    private final LocationListener gpsWarmupListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            state.location = location;
            state.updatedAtMs = System.currentTimeMillis();
            if (state.visibleCount() == 0) {
                state.statusText = "GPS activo, esperando satelites";
            }
            // keep last 5 fix timestamps
            if (state.fixHistory.size() >= 5) state.fixHistory.remove(0);
            state.fixHistory.add(System.currentTimeMillis());
            notifyListeners();
        }
    };

    private GnssDataStore(Context appContext) {
        this.appContext = appContext;
    }

    void addListener(Listener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
        listener.onGnssStateChanged(state);
    }

    void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    GnssState getState() {
        return state;
    }

    void start() {
        activeClients++;
        locationManager = (LocationManager) appContext.getSystemService(Context.LOCATION_SERVICE);
        state.permissionGranted = appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        state.gpsEnabled = locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        if (!state.permissionGranted) {
            state.tracking = false;
            state.statusText = "Activa el permiso de ubicacion precisa para ver satelites";
            state.summaryText = "La vista de satelites necesita ubicacion precisa.";
            notifyListeners();
            return;
        }

        if (!state.gpsEnabled) {
            state.tracking = false;
            state.statusText = "Activa GPS/Ubicacion en el telefono";
            state.summaryText = "Enciende la ubicacion para empezar a ver el cielo GNSS.";
            notifyListeners();
            return;
        }

        if (running) {
            notifyListeners();
            return;
        }

        try {
            locationManager.registerGnssStatusCallback(gnssCallback, handler);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, gpsWarmupListener);
            Location lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastKnown != null) state.location = lastKnown;
            state.tracking = true;
            state.statusText = "Escaneando satelites GPS";
            state.summaryText = String.format(Locale.US,
                    "%d satelites visibles. %d usados para posicion.",
                    state.visibleCount(), state.usedCount());
            running = true;
            notifyListeners();
        } catch (SecurityException e) {
            state.tracking = false;
            state.statusText = "Sin permiso de ubicacion precisa";
            notifyListeners();
        } catch (RuntimeException e) {
            state.tracking = false;
            state.statusText = "No se pudo iniciar GNSS";
            notifyListeners();
        }
    }

    void selectSatellite(int svid) {
        state.selectedSvid = svid;
        notifyListeners();
    }

    void stop() {
        activeClients = Math.max(0, activeClients - 1);
        if (activeClients > 0) return; // other clients still active, keep GPS running
        if (locationManager != null && running) {
            locationManager.unregisterGnssStatusCallback(gnssCallback);
            locationManager.removeUpdates(gpsWarmupListener);
        }
        running = false;
        state.tracking = false;
    }

    private void notifyListeners() {
        for (Listener listener : new ArrayList<>(listeners)) {
            listener.onGnssStateChanged(state);
        }
    }

    private String computeDiagnosis() {
        int used = state.usedCount();
        int visible = state.satellites.size();
        if (used >= 4) return "";
        if (visible == 0) return "Buscando satelites...";

        float totalCn0 = 0;
        int withEphemeris = 0;
        for (SatelliteInfo s : state.satellites) {
            totalCn0 += s.signal;
            if (s.hasEphemeris) withEphemeris++;
        }
        float avgCn0 = totalCn0 / visible;

        if (avgCn0 < 20f) return "Senal debil - busca cielo abierto";
        if (withEphemeris < 4) return "Descargando efemerides (" + withEphemeris + "/" + visible + ")";
        if (used == 0) return "Calculando posicion...";
        return "Fix parcial (" + used + " satelites)";
    }

    private String constellationName(int constellation) {
        switch (constellation) {
            case GnssStatus.CONSTELLATION_GPS:      return "GPS";
            case GnssStatus.CONSTELLATION_GLONASS:  return "GLONASS";
            case GnssStatus.CONSTELLATION_GALILEO:  return "Galileo";
            case GnssStatus.CONSTELLATION_BEIDOU:   return "BeiDou";
            case GnssStatus.CONSTELLATION_QZSS:     return "QZSS";
            case GnssStatus.CONSTELLATION_SBAS:     return "SBAS";
            case GnssStatus.CONSTELLATION_IRNSS:    return "IRNSS";
            default:                                return "GNSS";
        }
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
}
