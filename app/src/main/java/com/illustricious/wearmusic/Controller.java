package com.illustricious.wearmusic;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;

/**
 * Process-wide holder for the selected Soundcore device and its live link, so every screen
 * (home, equalizer, music, device picker) shares one RFCOMM connection. The device address and model
 * come from SharedPreferences, filled in by the device picker: nothing is compiled in.
 */
final class Controller implements SoundcoreLink.Listener {
    interface Observer { void onControllerChanged(); }

    enum Connection { NO_DEVICE, CONNECTING, CONNECTED, DISCONNECTED }

    private static final String PREFS = "r50i", KEY_MAC = "mac", KEY_NAME = "name", KEY_PROFILE = "profile";
    private static final long REFRESH_MS = 30000;
    private static Controller instance;

    static synchronized Controller get(Context context) {
        if (instance == null) instance = new Controller(context.getApplicationContext());
        return instance;
    }

    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Observer> observers = new ArrayList<Observer>();
    private SoundcoreLink link;

    private Connection connection;
    private String message = "";
    private Soundcore.State state;

    private final Runnable periodicRefresh = new Runnable() {
        @Override public void run() {
            if (connection == Connection.CONNECTED && link != null && !observers.isEmpty()) link.refresh();
            if (!observers.isEmpty()) main.postDelayed(this, REFRESH_MS);
        }
    };

    private Controller(Context app) {
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        connection = hasDevice() ? Connection.DISCONNECTED : Connection.NO_DEVICE;
    }

    // ---- selected device ------------------------------------------------------------------------

    boolean hasDevice() { return deviceMac() != null && profile() != null; }
    String deviceMac() { return prefs.getString(KEY_MAC, null); }
    String deviceName() { return prefs.getString(KEY_NAME, null); }
    Soundcore.Profile profile() { return Soundcore.profileByCode(prefs.getString(KEY_PROFILE, "")); }

    void select(String mac, String name, Soundcore.Profile profile) {
        disconnect();
        prefs.edit().putString(KEY_MAC, mac).putString(KEY_NAME, name).putString(KEY_PROFILE, profile.code).apply();
        state = null; message = "";
        connection = Connection.DISCONNECTED;
        notifyChanged();
        connect();
    }

    void forget() {
        disconnect();
        prefs.edit().clear().apply();
        state = null; message = "";
        connection = Connection.NO_DEVICE;
        notifyChanged();
    }

    // ---- connection -------------------------------------------------------------------------------

    Connection connection() { return connection; }
    String message() { return message; }
    Soundcore.State state() { return state; }
    boolean isConnected() { return connection == Connection.CONNECTED; }

    /** Caller must already hold the Bluetooth permission. */
    void connect() {
        if (!hasDevice() || connection == Connection.CONNECTING) return;
        if (link != null) link.close();
        link = new SoundcoreLink(this);
        connection = Connection.CONNECTING;
        message = "Connecting...";
        notifyChanged();
        link.connect(deviceMac(), profile());
    }

    void disconnect() {
        if (link != null) { link.close(); link = null; }
        if (hasDevice()) connection = Connection.DISCONNECTED;
    }

    void setMode(int mode) { if (link != null && isConnected()) { post("Sending " + Soundcore.modeName(mode)); link.setMode(mode); } }

    void setGaming(boolean on) {
        if (link != null && isConnected()) { post("Sending Gaming Mode"); link.setGaming(on); }
    }

    void setEqualizer(int presetId, int[] bands10, String label) {
        if (link != null && isConnected()) { post("Sending EQ: " + label); link.setEqualizer(presetId, bands10, label); }
    }

    void refresh() { if (link != null && isConnected()) link.refresh(); }

    // ---- observers --------------------------------------------------------------------------------

    void addObserver(Observer o) {
        boolean first = observers.isEmpty();
        observers.add(o);
        if (first) main.postDelayed(periodicRefresh, REFRESH_MS);
    }

    void removeObserver(Observer o) {
        observers.remove(o);
        if (observers.isEmpty()) main.removeCallbacks(periodicRefresh);
    }

    private void post(String m) { message = m; notifyChanged(); }

    private void notifyChanged() {
        for (Observer o : new ArrayList<Observer>(observers)) o.onControllerChanged();
    }

    // ---- SoundcoreLink.Listener (worker thread) -----------------------------------------------------

    @Override public void onConnected(final Soundcore.State s) {
        main.post(new Runnable() { public void run() {
            state = s; connection = Connection.CONNECTED; message = "Synced: " + Soundcore.modeName(s.ambientMode());
            notifyChanged();
        }});
    }

    @Override public void onState(final Soundcore.State s) {
        main.post(new Runnable() { public void run() { state = s; notifyChanged(); }});
    }

    @Override public void onDisconnected(final String reason) {
        main.post(new Runnable() { public void run() {
            connection = hasDevice() ? Connection.DISCONNECTED : Connection.NO_DEVICE;
            message = reason; notifyChanged();
        }});
    }

    @Override public void onMessage(final String m) {
        main.post(new Runnable() { public void run() { message = m; notifyChanged(); }});
    }
}
