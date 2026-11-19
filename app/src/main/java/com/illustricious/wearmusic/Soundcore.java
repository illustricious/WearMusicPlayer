package com.illustricious.wearmusic;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;

/**
 * Soundcore RFCOMM framing and the A3959 (R50i NC) state/command layout.
 * Pure Java (no Android classes) so it can be unit-tested on a desktop JDK.
 * Field layout mirrors OpenSCQ30 (GPL-3.0-or-later), a3959 + common modules.
 */
final class Soundcore {
    private Soundcore() {}

    static final int CMD_REQUEST_STATE = 0x0101;
    static final int CMD_SET_SOUND_MODE = 0x8106;
    static final int CMD_SET_EQUALIZER = 0x8302;
    /** OpenSCQ30 SET_GAMING_MODE_COMMAND = bytes [0x01, 0x87]; body = one bool byte. */
    static final int CMD_SET_GAMING = 0x8701;

    static final int PRESET_CUSTOM = 0xFEFE;
    static final int BANDS = 10;
    static final int VISIBLE_BANDS = 8;
    static final int[] BAND_HZ = {100, 200, 400, 800, 1600, 3200, 6400, 12800};
    /** Custom EQ slider range in tenths of a dB (matches OpenSCQ30's preset range). */
    static final int EQ_MIN = -60, EQ_MAX = 60;
    private static final int VOL_MIN = -120, VOL_MAX = 134;

    // ---- Frames -----------------------------------------------------------------------------

    static final class Frame {
        final int command; final byte[] body;
        Frame(int command, byte[] body) { this.command = command; this.body = body; }
    }

    private static final byte[] INBOUND_MAGIC = {9, (byte) 0xFF, 0, 0, 1};

    static byte[] encode(int command, byte[] body) {
        int len = 10 + body.length;
        byte[] p = new byte[len];
        p[0] = 8; p[1] = (byte) 0xEE;
        p[5] = (byte) command; p[6] = (byte) (command >>> 8);
        p[7] = (byte) len; p[8] = (byte) (len >>> 8);
        System.arraycopy(body, 0, p, 9, body.length);
        p[len - 1] = checksum(p, len - 1);
        return p;
    }

    static byte checksum(byte[] p, int n) {
        int sum = 0;
        for (int i = 0; i < n; i++) sum = (sum + (p[i] & 255)) & 255;
        return (byte) sum;
    }

    static void write(OutputStream out, int command, byte[] body) throws IOException {
        out.write(encode(command, body));
        out.flush();
    }

    static Frame read(InputStream in, long deadline) throws Exception {
        int matched = 0, discarded = 0;
        while (matched < INBOUND_MAGIC.length && discarded < 64) {
            int b = readByte(in, deadline);
            if ((byte) b == INBOUND_MAGIC[matched]) matched++;
            else { discarded++; matched = ((byte) b == INBOUND_MAGIC[0]) ? 1 : 0; }
        }
        if (matched != INBOUND_MAGIC.length) throw new IOException("Soundcore frame header not found");
        byte[] h = new byte[9];
        System.arraycopy(INBOUND_MAGIC, 0, h, 0, 5);
        System.arraycopy(exact(in, 4, deadline), 0, h, 5, 4);
        int len = (h[7] & 255) | ((h[8] & 255) << 8);
        if (len < 10 || len > 1024) throw new IOException("Invalid frame length: " + hex(h));
        byte[] p = new byte[len];
        System.arraycopy(h, 0, p, 0, 9);
        System.arraycopy(exact(in, len - 9, deadline), 0, p, 9, len - 9);
        if (p[len - 1] != checksum(p, len - 1)) throw new IOException("Invalid frame checksum: " + hex(p));
        int cmd = (p[5] & 255) | ((p[6] & 255) << 8);
        byte[] body = new byte[len - 10];
        System.arraycopy(p, 9, body, 0, body.length);
        return new Frame(cmd, body);
    }

    /** Reads frames until one with {@code command} arrives (unrelated pushes are skipped). */
    static Frame readCommand(InputStream in, int command, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        for (int i = 0; i < 12; i++) {
            Frame f = read(in, deadline);
            if (f.command == command) return f;
        }
        throw new IOException("No reply for command " + Integer.toHexString(command));
    }

    private static byte[] exact(InputStream in, int n, long deadline) throws Exception {
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) b[i] = (byte) readByte(in, deadline);
        return b;
    }

    private static int readByte(InputStream in, long deadline) throws Exception {
        while (in.available() == 0) {
            if (System.currentTimeMillis() >= deadline) throw new SocketTimeoutException("Timed out waiting for Soundcore packet");
            Thread.sleep(10);
        }
        int b = in.read();
        if (b < 0) throw new IOException("Bluetooth disconnected");
        return b;
    }

    static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte v : b) s.append(String.format("%02X ", v & 255));
        return s.toString().trim();
    }

    // ---- State (A3959 0x0101 reply) ---------------------------------------------------------

    /** Offsets inside the A3959 state packet body. */
    private static final int OFF_TWS = 0, OFF_BATTERY = 2, OFF_EQ = 32, OFF_SOUND_MODES = 64,
        OFF_FIRMWARE = 6, OFF_GAMING = 78;   // 64 + 7 modes, +1 unknown, touch tone, dual conn, surround, auto-off (2), low-battery, gaming
    static final int STATE_MIN_LENGTH = 71;
    static final int BATTERY_MAX = 10;

    static final class State {
        boolean connectedToPeer;
        int levelLeft, levelRight;          // 0..10
        boolean chargingLeft, chargingRight;
        int presetId;
        int[] eq = new int[BANDS];          // tenths of a dB, band 1..10
        byte[] soundModes = new byte[7];
        /** Low-latency "Gaming mode" flag; only meaningful when {@link #gamingSupported} (firmware >= 01.60). */
        boolean gaming, gamingSupported;
        /** Whole state body, kept so a later command can preserve unrelated bytes. */
        byte[] raw;

        int percentLeft() { return Math.min(100, levelLeft * 100 / BATTERY_MAX); }
        int percentRight() { return Math.min(100, levelRight * 100 / BATTERY_MAX); }
        int ambientMode() { return soundModes[0] & 255; }
    }

    static State parseState(byte[] body) throws IOException {
        if (body.length < STATE_MIN_LENGTH) throw new IOException("A3959 state packet too short");
        State s = new State();
        s.raw = body.clone();
        s.connectedToPeer = body[OFF_TWS + 1] != 0;
        s.levelLeft = Math.min(BATTERY_MAX, body[OFF_BATTERY] & 255);
        s.levelRight = Math.min(BATTERY_MAX, body[OFF_BATTERY + 1] & 255);
        s.chargingLeft = body[OFF_BATTERY + 2] == 1;
        s.chargingRight = body[OFF_BATTERY + 3] == 1;
        s.presetId = (body[OFF_EQ] & 255) | ((body[OFF_EQ + 1] & 255) << 8);
        if ((body[OFF_EQ + 2] & 255) != 0xFF) {
            for (int i = 0; i < BANDS; i++) s.eq[i] = (body[OFF_EQ + 2 + i] & 255) + VOL_MIN;
        }
        System.arraycopy(body, OFF_SOUND_MODES, s.soundModes, 0, 7);
        s.gamingSupported = body.length > OFF_GAMING && firmwareAtLeast(body, OFF_FIRMWARE, 1, 60);
        s.gaming = s.gamingSupported && body[OFF_GAMING] == 1;
        return s;
    }

    /** Two 5-byte ASCII versions ("01.60", left then right); true only if both parse and are >= major.minor. */
    static boolean firmwareAtLeast(byte[] body, int offset, int major, int minor) {
        if (body.length < offset + 10) return false;
        for (int side = 0; side < 2; side++) {
            int o = offset + side * 5;
            if (body[o + 2] != '.') return false;
            int[] d = {body[o] - '0', body[o + 1] - '0', body[o + 3] - '0', body[o + 4] - '0'};
            for (int x : d) if (x < 0 || x > 9) return false;
            int ma = d[0] * 10 + d[1], mi = d[2] * 10 + d[3];
            if (ma < major || (ma == major && mi < minor)) return false;
        }
        return true;
    }

    // ---- Sound modes ------------------------------------------------------------------------

    static final int MODE_ANC = 0, MODE_TRANSPARENCY = 1, MODE_NORMAL = 2;

    static String modeName(int mode) {
        return mode == MODE_ANC ? "ANC" : mode == MODE_TRANSPARENCY ? "Transparency" : "Normal";
    }

    /** Body for CMD_SET_SOUND_MODE: the current 7 sound-mode bytes with the mode replaced. */
    static byte[] soundModeBody(byte[] current7, int mode) {
        byte[] b = current7.clone();
        b[0] = (byte) mode;
        b[2] = (byte) mode;
        return b;
    }

    // ---- Equalizer --------------------------------------------------------------------------

    static final class Preset {
        final int id; final String name; final int[] bands;
        Preset(int id, String name, int... bands) { this.id = id; this.name = name; this.bands = bands; }
    }

    /** Same table as OpenSCQ30 common_settings_type_2 (tenths of a dB, 10 bands). */
    static final Preset[] PRESETS = {
        new Preset(0, "Soundcore Signature", 0, 0, 0, 0, 0, 0, 0, 0, 0, -120),
        new Preset(1, "Acoustic", 40, 10, 20, 20, 40, 40, 40, 20, 0, -120),
        new Preset(2, "Bass Booster", 40, 30, 10, 0, 0, 0, 0, 0, 0, -120),
        new Preset(3, "Bass Reducer", -40, -30, -10, 0, 0, 0, 0, 0, 0, -120),
        new Preset(4, "Classical", 30, 30, -20, -20, 0, 20, 30, 40, 0, -120),
        new Preset(5, "Podcast", -30, 20, 40, 40, 30, 20, 0, -20, 0, -120),
        new Preset(6, "Dance", 20, -30, -10, 10, 20, 20, 10, -30, 0, -120),
        new Preset(7, "Deep", 20, 10, 30, 30, 20, -20, -40, -50, 0, -120),
        new Preset(8, "Electronic", 30, 20, -20, 20, 10, 20, 30, 30, 0, -120),
        new Preset(9, "Flat", -20, -20, -10, 0, 0, 0, -20, -20, 0, -120),
        new Preset(10, "Hip-Hop", 20, 30, -10, -10, 20, -10, 20, 30, 0, -120),
        new Preset(11, "Jazz", 20, 20, -20, -20, 0, 20, 30, 40, 0, -120),
        new Preset(12, "Latin", 0, 0, -20, -20, -20, 0, 30, 50, 0, -120),
        new Preset(13, "Lounge", -10, 20, 40, 30, 0, -20, 20, 10, 0, -120),
        new Preset(14, "Piano", 0, 30, 30, 20, 40, 50, 30, 40, 0, -120),
        new Preset(15, "Pop", -10, 10, 30, 30, 10, -10, -20, -30, 0, -120),
        new Preset(16, "R&B", 60, 20, -20, -20, 20, 30, 30, 40, 0, -120),
        new Preset(17, "Rock", 30, 20, -10, -10, 10, 30, 30, 30, 0, -120),
        new Preset(18, "Small Speakers", 40, 30, 10, 0, -20, -30, -40, -40, 0, -120),
        new Preset(19, "Spoken Word", -30, -20, 10, 20, 20, 10, 0, -30, 0, -120),
        new Preset(20, "Treble Booster", -20, -20, -20, -10, 10, 20, 20, 40, 0, -120),
        new Preset(21, "Treble Reducer", 0, 0, 0, -20, -30, -40, -40, -60, 0, -120),
    };

    static Preset presetById(int id) {
        for (Preset p : PRESETS) if (p.id == id) return p;
        return null;
    }

    /** Full 10-band array for a user-edited 8-band curve (hidden bands 9/10 fixed at 0 like OpenSCQ30). */
    static int[] customBands(int[] visible8) {
        int[] b = new int[BANDS];
        for (int i = 0; i < VISIBLE_BANDS; i++) b[i] = clamp(visible8[i], EQ_MIN, EQ_MAX);
        return b;
    }

    private static final double S = 0.85, L = 0.95;
    private static final double[][] DRC = {
        {1.26, -0.71 * S, 0.177, -0.0494, 0.0345, -0.0197, 0.0075, -0.00217},
        {-0.71 * S, 1.73 * L, 0.0, 0.204, -0.068, 0.045, -0.0235, 0.0075},
        {0.177, -0.81 * S, 1.73 * L, -0.81 * S, 0.208, -0.07, 0.045, -0.0197},
        {-0.0494, 0.204, 0.0, 1.73 * L, -0.82 * S, 0.208, -0.068, 0.0345},
        {0.0345, -0.068, 0.208, -0.82 * S, 1.73 * L, 0.0, 0.204, -0.0494},
        {-0.0197, 0.045, -0.07, 0.208, -0.81 * S, 1.73 * L, -0.81 * S, 0.177},
        {0.0075, -0.0235, 0.045, -0.068, 0.204, 0.0, 1.83 * L, -0.71 * S},
        {-0.00217, 0.0075, -0.0197, 0.0345, -0.0494, 0.177, -0.71 * S, 1.5},
    };

    /** Port of OpenSCQ30 VolumeAdjustments::apply_drc (values in tenths of a dB). */
    static int[] applyDrc(int[] bands) {
        double[] adj = new double[bands.length];
        for (int i = 0; i < adj.length; i++) adj[i] = bands[i] / 10.0;
        double lowsSub = adj[2] * 0.81 * S;
        double highsSub = adj[5] * 0.81 * S;
        int[] out = new int[bands.length];
        int[] defaults = {0, 0, 0, 0, 0, 0, 0, 0, 0, -120};
        for (int row = 0; row < bands.length; row++) {
            if (row >= 8) { out[row] = row < defaults.length ? defaults[row] : 0; continue; }
            double acc = 0;
            for (int i = 0; i < 8; i++) {
                if ((row == 1 || row == 3) && i == 2) acc -= lowsSub;
                else if ((row == 4 || row == 6) && i == 5) acc -= highsSub;
                else acc += adj[i] * DRC[row][i];
            }
            out[row] = clamp(roundHalfAway(acc / 10.0 * 10.0), VOL_MIN, VOL_MAX);
        }
        return out;
    }

    private static int roundHalfAway(double v) { return (int) (v < 0 ? Math.ceil(v - 0.5) : Math.floor(v + 0.5)); }
    static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    /** Body for CMD_SET_EQUALIZER: preset id (LE), 10 band bytes, 10 DRC-adjusted band bytes. */
    static byte[] equalizerBody(int presetId, int[] bands10) {
        int[] drc = applyDrc(bands10);
        byte[] body = new byte[2 + BANDS * 2];
        body[0] = (byte) presetId; body[1] = (byte) (presetId >>> 8);
        for (int i = 0; i < BANDS; i++) {
            body[2 + i] = (byte) (clamp(bands10[i], VOL_MIN, VOL_MAX) - VOL_MIN);
            body[2 + BANDS + i] = (byte) (drc[i] - VOL_MIN);
        }
        return body;
    }

    // ---- Device-model profiles ----------------------------------------------------------------

    /**
     * Protocol profile for a Soundcore model. Only profiles listed here are ever spoken to: for any other
     * device the app refuses to send commands instead of guessing a payload.
     */
    static final class Profile {
        final String code, title;
        Profile(String code, String title) { this.code = code; this.title = title; }
    }

    static final Profile A3959 = new Profile("A3959", "Soundcore R50i NC (A3959)");
    static final Profile[] PROFILES = {A3959};

    static Profile profileByCode(String code) {
        for (Profile p : PROFILES) if (p.code.equals(code)) return p;
        return null;
    }

    /** Best-effort suggestion from the Bluetooth name; the user still confirms it. */
    static Profile suggestProfile(String bluetoothName) {
        if (bluetoothName == null) return null;
        String n = bluetoothName.toLowerCase().replace(" ", "");
        if (n.contains("r50i") && n.contains("nc")) return A3959;
        if (n.contains("a3959")) return A3959;
        return null;
    }

    static boolean looksLikeSoundcore(String bluetoothName) {
        if (bluetoothName == null) return false;
        String n = bluetoothName.toLowerCase();
        return n.contains("soundcore") || n.contains("anker") || n.contains("liberty") || n.contains("space")
            || n.contains("life ") || n.contains("r50i") || n.matches(".*\\ba3[0-9]{3}\\b.*");
    }
}
