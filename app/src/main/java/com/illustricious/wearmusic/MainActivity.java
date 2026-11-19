package com.illustricious.wearmusic;
import android.content.*;
import android.os.*;
import android.widget.*;
/** Music-first home with separate device controls. */
public final class MainActivity extends CrownActivity implements PlayerService.Listener {
    private PlayerService player;
    private boolean bound;
    private TextView track, subtitle;
    private final ServiceConnection connection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder binder) {
            player = ((PlayerService.LocalBinder) binder).service();
            player.addListener(MainActivity.this); onPlayerChanged();
        }
        public void onServiceDisconnected(ComponentName name) { player = null; }
    };
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout col = Ui.page(this);
        col.addView(Ui.text(this, "YOUR OFFLINE MIX", 10, Ui.ACCENT, true));
        col.addView(Ui.heading(this, "Music"));
        track = Ui.text(this, "Ready to play", 19, Ui.WHITE, true);
        track.setMaxLines(2); track.setEllipsize(android.text.TextUtils.TruncateAt.END);
        track.setPadding(8, Ui.dp(this, 10), 8, 0); col.addView(track);
        subtitle = Ui.text(this, "Offline. On your wrist.", 12, Ui.MUTED, false);
        subtitle.setSingleLine(); subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(subtitle); col.addView(Ui.spacer(this, 10));
        Button play = Ui.button(this, "Now playing", v -> open(NowPlayingActivity.class));
        Ui.style(play, true); col.addView(play);
        col.addView(Ui.button(this, "Browse music", v -> open(MusicActivity.class)));
        col.addView(Ui.button(this, "Playback options", v -> open(PlaybackOptionsActivity.class)));
        col.addView(Ui.button(this, "Earbuds & sound", v -> open(EarbudsActivity.class)));
    }
    private void open(Class<?> screen) { startActivity(new Intent(this, screen)); }
    @Override protected void onStart() {
        super.onStart(); bound = bindService(new Intent(this, PlayerService.class), connection, Context.BIND_AUTO_CREATE);
    }
    @Override protected void onStop() {
        if (player != null) player.removeListener(this);
        player = null; if (bound) unbindService(connection); bound = false; super.onStop();
    }
    @Override public void onPlayerChanged() {
        MusicLibrary.Track t = player == null ? null : player.current();
        track.setText(t == null ? "Ready to play" : t.title);
        subtitle.setText(t == null ? "Offline. On your wrist." : t.subtitle());
    }
}
