package com.illustricious.wearmusic;
import android.content.*;
import android.os.*;
import android.widget.*;
public final class PlaybackOptionsActivity extends CrownActivity implements PlayerService.Listener {
    private PlayerService player;
    private boolean bound;
    private TextView timer;
    private Button repeat;
    private final ServiceConnection connection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder binder) {
            player = ((PlayerService.LocalBinder) binder).service(); player.addListener(PlaybackOptionsActivity.this); onPlayerChanged();
        }
        public void onServiceDisconnected(ComponentName name) { player = null; }
    };
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout col = Ui.page(this);
        col.addView(Ui.heading(this, "Playback"));
        repeat = Ui.button(this, "Repeat: off", v -> { if (player != null) player.setRepeatMode((player.repeatMode() + 1) % 3); });
        col.addView(repeat); col.addView(Ui.spacer(this, 12));
        timer = Ui.text(this, "Sleep timer off", 13, Ui.ACCENT, true); col.addView(timer);
        for (int n : new int[]{15, 30, 60, 0}) {
            col.addView(Ui.button(this, n == 0 ? "Cancel timer" : n + " minutes", v -> { if (player != null) {
                    if (n > 0 && player.current() == null) android.widget.Toast.makeText(this, "Start a track first", android.widget.Toast.LENGTH_SHORT).show();
                    else player.setSleepMinutes(n);
                } }));
        }
    }
    @Override protected void onStart() { super.onStart(); bound = bindService(new Intent(this, PlayerService.class), connection, Context.BIND_AUTO_CREATE); }
    @Override protected void onStop() {
        if (player != null) player.removeListener(this); player = null;
        if (bound) unbindService(connection); bound = false; super.onStop();
    }
    public void onPlayerChanged() {
        if (player == null) return;
        repeat.setText("Repeat: " + new String[]{"off", "all", "one"}[player.repeatMode()]);
        Ui.style(repeat, player.repeatMode() != 0);
        timer.setText(player.sleepMinutes() == 0 ? "Sleep timer off" : "Stops in " + player.sleepMinutes() + " min");
    }
}
