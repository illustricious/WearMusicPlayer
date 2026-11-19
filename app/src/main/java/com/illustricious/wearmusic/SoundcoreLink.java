package com.illustricious.wearmusic;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.ParcelUuid;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * RFCOMM link to one Soundcore device whose address is supplied at runtime (never compiled in).
 * All socket I/O runs on a single worker thread; listener callbacks arrive on that thread.
 */
final class SoundcoreLink {
    interface Listener {
        void onConnected(Soundcore.State state);
        void onState(Soundcore.State state);
        void onDisconnected(String reason);
        void onMessage(String message);
    }

    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final UUID VENDOR = UUID.fromString("0cf12d31-fac3-4553-bd80-d6832e700000");
    private static final String VENDOR_PREFIX = "0cf12d31-fac3-4553-bd80-d6832e7";

    private final Listener listener;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private BluetoothSocket socket;
    private volatile Soundcore.State state;
    private volatile boolean closed;

    SoundcoreLink(Listener listener) { this.listener = listener; }

    Soundcore.State state() { return state; }
    boolean isConnected() { BluetoothSocket s = socket; return s != null && s.isConnected(); }

    void connect(final String mac, final Soundcore.Profile profile) {
        worker.execute(new Runnable() { public void run() {
            try {
                if (profile == null) throw new Exception("Unsupported earbud model");
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter == null || !adapter.isEnabled()) throw new Exception("Bluetooth is off");
                if (!BluetoothAdapter.checkBluetoothAddress(mac)) throw new Exception("Invalid Bluetooth address");
                closeSocket();
                BluetoothDevice device = adapter.getRemoteDevice(mac);
                UUID service = VENDOR;
                ParcelUuid[] uuids = device.getUuids();
                if (uuids != null) for (ParcelUuid u : uuids) {
                    if (u.getUuid().toString().startsWith(VENDOR_PREFIX)) { service = u.getUuid(); break; }
                }
                BluetoothSocket s;
                try { s = device.createRfcommSocketToServiceRecord(service); s.connect(); }
                catch (Exception first) { s = device.createRfcommSocketToServiceRecord(SPP); s.connect(); }
                socket = s;
                state = requestState();
                listener.onConnected(state);
            } catch (Exception e) {
                closeSocket();
                listener.onDisconnected(message(e));
            }
        }});
    }

    void refresh() {
        worker.execute(new Runnable() { public void run() {
            try { requireSocket(); state = requestState(); listener.onState(state); }
            catch (Exception e) { fail(e); }
        }});
    }

    void setMode(final int mode) {
        worker.execute(new Runnable() { public void run() {
            try {
                requireSocket();
                Soundcore.State cur = state;
                if (cur == null) throw new Exception("Earbuds are still syncing");
                byte[] body = Soundcore.soundModeBody(cur.soundModes, mode);
                if (!sendWithAck(Soundcore.CMD_SET_SOUND_MODE, body)) {
                    Soundcore.State actual = requestState();
                    state = actual;
                    if (actual.ambientMode() != mode) throw new Exception("No acknowledgment. Current mode: " + Soundcore.modeName(actual.ambientMode()));
                    listener.onState(actual);
                    listener.onMessage("Applied: " + Soundcore.modeName(mode) + " (readback)");
                    return;
                }
                System.arraycopy(body, 0, cur.soundModes, 0, 7);
                listener.onState(cur);
                listener.onMessage("Applied: " + Soundcore.modeName(mode));
            } catch (Exception e) { fail(e); }
        }});
    }

    /** Toggles low-latency gaming mode (firmware >= 01.60 only) and verifies it by reading the state back. */
    void setGaming(final boolean on) {
        worker.execute(new Runnable() { public void run() {
            try {
                requireSocket();
                Soundcore.State cur = state;
                if (cur == null || !cur.gamingSupported) throw new Exception("Gaming Mode requires earbud firmware 01.60 or later");
                boolean acked = sendWithAck(Soundcore.CMD_SET_GAMING, new byte[] {(byte) (on ? 1 : 0)});
                Soundcore.State actual = requestState();
                state = actual;
                listener.onState(actual);
                if (actual.gaming == on) listener.onMessage("Gaming Mode " + (on ? "on" : "off") + (acked ? "" : " (readback)"));
                else throw new Exception("The earbuds did not change Gaming Mode");
            } catch (Exception e) { fail(e); }
        }});
    }

    /** Sends an equalizer curve and verifies it by reading the device state back. */
    void setEqualizer(final int presetId, final int[] bands10, final String label) {
        worker.execute(new Runnable() { public void run() {
            try {
                requireSocket();
                byte[] body = Soundcore.equalizerBody(presetId, bands10);
                boolean acked = sendWithAck(Soundcore.CMD_SET_EQUALIZER, body);
                Soundcore.State actual = requestState();
                state = actual;
                boolean matches = actual.presetId == presetId
                    && (presetId != Soundcore.PRESET_CUSTOM || Arrays.equals(Arrays.copyOf(actual.eq, 8), Arrays.copyOf(bands10, 8)));
                listener.onState(actual);
                if (matches) listener.onMessage("Applied EQ: " + label + (acked ? "" : " (readback)"));
                else throw new Exception("The earbuds did not change EQ");
            } catch (Exception e) { fail(e); }
        }});
    }

    void close() {
        closed = true;
        closeSocket();
        worker.shutdownNow();
    }

    // ---- internals (worker thread only) -------------------------------------------------------

    private void requireSocket() throws Exception {
        if (!isConnected()) throw new Exception("Disconnected");
    }

    private Soundcore.State requestState() throws Exception {
        Soundcore.write(socket.getOutputStream(), Soundcore.CMD_REQUEST_STATE, new byte[0]);
        Soundcore.Frame f = Soundcore.readCommand(socket.getInputStream(), Soundcore.CMD_REQUEST_STATE, 4000);
        return Soundcore.parseState(f.body);
    }

    private boolean sendWithAck(int command, byte[] body) throws Exception {
        for (int attempt = 1; attempt <= 3; attempt++) {
            Soundcore.write(socket.getOutputStream(), command, body);
            try { Soundcore.readCommand(socket.getInputStream(), command, 500L * attempt); return true; }
            catch (SocketTimeoutException timeout) { /* retry */ }
        }
        return false;
    }

    private void fail(Exception e) {
        if (closed) return;
        if (!isConnected()) { closeSocket(); listener.onDisconnected(message(e)); }
        else listener.onMessage(message(e));
    }

    private void closeSocket() {
        BluetoothSocket s = socket; socket = null;
        if (s != null) try { s.close(); } catch (Exception ignored) {}
    }

    private static String message(Exception e) {
        return e.getMessage() == null ? "Bluetooth operation failed" : e.getMessage();
    }
}
