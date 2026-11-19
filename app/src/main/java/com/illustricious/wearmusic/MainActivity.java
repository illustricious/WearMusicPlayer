package com.illustricious.wearmusic;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * One merged Home: the music player card on top, the earbuds card (battery, sound mode, gaming mode,
 * equalizer, device) below, then song search. The crown scrolls the whole page.
 */
public final class MainActivity extends CrownActivity implements Controller.Observer, PlayerService.Listener, MiniPlayerView.Callbacks {
    private static final int[] MODES = {Soundcore.MODE_ANC, Soundcore.MODE_TRANSPARENCY, Soundcore.MODE_NORMAL};
    private static final String[] LABELS = {"ANC", "Transp.", "Normal"};

    private final Handler ui = new Handler();
    private Controller controller;
    private MiniPlayerView mini;
    private PlayerService player;
    private boolean bound;
    private TextView device, batteryLeft, batteryRight, status, gamingHint;
    private Button reconnect, gaming;
    private LinearLayout batteryRow, modeRow, actionRow;
    private final Button[] modeButtons = new Button[3];

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            player = ((PlayerService.LocalBinder) binder).service();
            player.addListener(MainActivity.this);
            onPlayerChanged();
        }
        @Override public void onServiceDisconnected(ComponentName name) { player = null; }
    };

    private final Runnable tick = this::onPlayerChanged;
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        controller = Controller.get(this);
        LinearLayout col = Ui.page(this);

        col.addView(section("MUSIC"));
        mini = new MiniPlayerView(this, this);
        col.addView(mini, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 112)));
        col.addView(Ui.button(this, "Browse Library", open(MusicActivity.class)));

        col.addView(Ui.spacer(this, 14));
        col.addView(section("EARBUDS"));
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.pill(this, Ui.SURFACE));
        int p = Ui.dp(this, 10);
        card.setPadding(p, Ui.dp(this, 12), p, p);
        col.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        device = Ui.text(this, "", 14, Ui.ACCENT, true);
        device.setOnClickListener(open(DevicesActivity.class));
        card.addView(device);

        batteryRow = new LinearLayout(this);
        batteryRow.setOrientation(LinearLayout.HORIZONTAL);
        batteryRow.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 8));
        batteryLeft = battery("L"); batteryRight = battery("R");
        batteryRow.addView(batteryLeft); batteryRow.addView(batteryRight);
        card.addView(batteryRow);

        reconnect = Ui.button(this, "Reconnect", new View.OnClickListener() { public void onClick(View v) { connectWithPermission(); } });
        card.addView(reconnect);

        modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 3; i++) {
            final int mode = MODES[i];
            Button b = Ui.button(this, LABELS[i], new View.OnClickListener() { public void onClick(View v) { controller.setMode(mode); } });
            b.setTextSize(12);
            b.setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1);
            lp.setMargins(i == 0 ? 0 : Ui.dp(this, 3), Ui.dp(this, 2), i == 2 ? 0 : Ui.dp(this, 3), 0);
            b.setLayoutParams(lp);
            modeButtons[i] = b;
            modeRow.addView(b);
        }
        card.addView(modeRow);

        gaming = Ui.button(this, "Gaming Mode", new View.OnClickListener() {
            public void onClick(View v) {
                Soundcore.State s = controller.state();
                if (s != null && s.gamingSupported) controller.setGaming(!s.gaming);
            }
        });
        card.addView(gaming);
        gamingHint = Ui.text(this, "Lower latency for games and video", 11, Ui.MUTED, false);
        card.addView(gamingHint);

        actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.addView(half("Equalizer", open(EqualizerActivity.class), false));
        actionRow.addView(half("Change", open(DevicesActivity.class), true));
        card.addView(actionRow);

        Button[] inCard = {reconnect, gaming, modeButtons[0], modeButtons[1], modeButtons[2]};
        for (Button b : inCard) { b.setTag("card"); Ui.style(b, false); }
        for (int i = 0; i < actionRow.getChildCount(); i++) { Button b = (Button) actionRow.getChildAt(i); b.setTag("card"); Ui.style(b, false); }
        status = Ui.text(this, "", 12, Ui.MUTED, false);
        status.setPadding(0, Ui.dp(this, 8), 0, 0);
        col.addView(status);
        if (controller.hasDevice()) connectWithPermission();
    }

    private TextView battery(String side) {
        TextView t = Ui.text(this, side + " —", 19, Ui.WHITE, true);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return t;
    }

    private Button half(String label, View.OnClickListener l, boolean right) {
        Button b = Ui.button(this, label, l);
        b.setTextSize(13);
        b.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1);
        lp.setMargins(right ? Ui.dp(this, 3) : 0, Ui.dp(this, 5), right ? 0 : Ui.dp(this, 3), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private TextView section(String label) {
        TextView t = Ui.text(this, label, 11, Ui.MUTED, true);
        t.setLetterSpacing(0.15f);
        t.setPadding(0, 0, 0, Ui.dp(this, 4));
        return t;
    }

    private View.OnClickListener open(final Class<?> cls) {
        return new View.OnClickListener() { public void onClick(View v) { startActivity(new Intent(MainActivity.this, cls)); } };
    }

    @Override protected void onStart() {
        super.onStart();
        controller.addObserver(this);
        if (controller.isConnected()) controller.refresh();
        else if (controller.connection() == Controller.Connection.DISCONNECTED && Ui.missing(this, Ui.bluetoothPermission()).length == 0) controller.connect();
        onControllerChanged();
        bound = bindService(new Intent(this, PlayerService.class), connection, Context.BIND_AUTO_CREATE);
    }

    @Override protected void onStop() {
        ui.removeCallbacks(tick);
        if (player != null) { player.removeListener(this); player = null; }
        if (bound) { unbindService(connection); bound = false; }
        controller.removeObserver(this);
        super.onStop();
    }

    private void connectWithPermission() {
        String[] need = Ui.missing(this, Ui.bluetoothPermission());
        if (need.length > 0) { requestPermissions(need, 1); return; }
        if (controller.hasDevice()) controller.connect();
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) connectWithPermission();
        else status.setText("Allow Nearby devices to use Bluetooth controls");
    }

    // ---- player card ----------------------------------------------------------------------------------

    @Override public void onPlayerChanged() {
        if (player == null) { mini.set("", "", 0, false, false); return; }
        ui.removeCallbacks(tick);
        if (player.isPlaying()) ui.postDelayed(tick, 1000);
        MusicLibrary.Track t = player.current();
        int dur = player.durationMs();
        mini.set(t == null ? "" : t.title, t == null ? "" : t.subtitle(), dur > 0 ? player.positionMs() / (float) dur : 0f, player.wantPlayback(), t != null);
    }

    @Override public void onOpen() { startActivity(new Intent(this, NowPlayingActivity.class)); }
    @Override public void onPrevious() { if (player != null) player.previous(); }
    @Override public void onToggle() { if (player != null && player.current() != null) player.toggle(); else startActivity(new Intent(this, MusicActivity.class)); }
    @Override public void onNext() { if (player != null) player.next(); }

    // ---- earbuds card ---------------------------------------------------------------------------------

    @Override public void onControllerChanged() {
        Controller.Connection c = controller.connection();
        String name = controller.deviceName();
        String dot = c == Controller.Connection.CONNECTED ? "Connected: " : c == Controller.Connection.CONNECTING ? "Connecting: " : "";
        device.setText(c == Controller.Connection.NO_DEVICE ? "Choose earbuds" : dot + (name == null ? "" : name));
        device.setTextColor(c == Controller.Connection.CONNECTED ? Ui.OK : c == Controller.Connection.CONNECTING ? Ui.WARN : Ui.BAD);
        reconnect.setVisibility(c == Controller.Connection.DISCONNECTED ? View.VISIBLE : View.GONE);
        Soundcore.State s = controller.state();
        boolean live = c == Controller.Connection.CONNECTED && s != null;
        batteryRow.setVisibility(c == Controller.Connection.NO_DEVICE ? View.GONE : View.VISIBLE);
        modeRow.setVisibility(c == Controller.Connection.NO_DEVICE ? View.GONE : View.VISIBLE);
        actionRow.setVisibility(c == Controller.Connection.NO_DEVICE ? View.GONE : View.VISIBLE);
        if (live) {
            setBattery(batteryLeft, "L", s.percentLeft(), s.chargingLeft);
            setBattery(batteryRight, "R", s.percentRight(), s.chargingRight);
        } else {
            batteryLeft.setText("L —"); batteryRight.setText("R —");
            batteryLeft.setTextColor(Ui.MUTED); batteryRight.setTextColor(Ui.MUTED);
        }
        for (int i = 0; i < 3; i++) {
            modeButtons[i].setEnabled(live);
            Ui.style(modeButtons[i], live && s.ambientMode() == MODES[i]);
        }
        boolean showGaming = live && s.gamingSupported;
        gaming.setVisibility(showGaming || (live && !s.gamingSupported) ? View.VISIBLE : View.GONE);
        gaming.setEnabled(showGaming);
        gaming.setText(showGaming ? "Gaming Mode: " + (s.gaming ? "On" : "Off") : "Gaming Mode requires firmware 1.60+");
        Ui.style(gaming, showGaming && s.gaming);
        gamingHint.setVisibility(showGaming ? View.VISIBLE : View.GONE);
        status.setText(controller.message());
    }

    private static void setBattery(TextView t, String side, int percent, boolean charging) {
        t.setText(side + " " + percent + "%" + (charging ? "+" : ""));
        t.setTextColor(percent <= 20 && !charging ? Ui.BAD : Ui.WHITE);
    }

    @Override protected void onDestroy() {
        if (isFinishing()) controller.disconnect();
        super.onDestroy();
    }
}
