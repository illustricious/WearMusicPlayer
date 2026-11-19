package com.illustricious.wearmusic;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public final class NowPlayingActivity extends CrownActivity implements PlayerService.Listener, PlayerView.Callbacks {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ScheduledThreadPoolExecutor artworkWorker = new ScheduledThreadPoolExecutor(1);
    private PlayerView view;
    private PlayerService player;
    private MusicLibrary.Track displayed;
    private Future<?> artworkJob;
    private int artworkGeneration;
    private AudioManager audio;
    private boolean bound, visible, artworkLoaded;
    private float crownAccumulator;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            player = ((PlayerService.LocalBinder) binder).service();
            player.addListener(NowPlayingActivity.this);
            onPlayerChanged();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            player = null;
            onPlayerChanged();
        }
    };

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!visible || player == null || !player.isPlaying()) return;
            view.setPosition(player.positionMs(), player.durationMs());
            ui.postDelayed(this, 500);
        }
    };
    private final Runnable clearOverlay = () -> view.setOverlay(null);

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        audio = getSystemService(AudioManager.class);
        artworkWorker.setRemoveOnCancelPolicy(true);
        view = new PlayerView(this, this);
        setContentView(view);
    }

    @Override protected void onStart() {
        super.onStart();
        visible = true;
        bound = bindService(new Intent(this, PlayerService.class), connection, Context.BIND_AUTO_CREATE);
    }

    @Override protected void onStop() {
        visible = false;
        ui.removeCallbacks(tick);
        ui.removeCallbacks(clearOverlay);
        view.setOverlay(null);
        if (player != null) player.removeListener(this);
        player = null;
        if (bound) unbindService(connection);
        bound = false;
        cancelArtwork();
        super.onStop();
    }

    @Override protected void onDestroy() {
        artworkWorker.shutdownNow();
        super.onDestroy();
    }

    @Override public void onPlayerChanged() {
        if (!visible) return;
        MusicLibrary.Track track = player == null ? null : player.current();
        if (track != displayed || (track != null && !artworkLoaded && artworkJob == null)) {
            displayed = track;
            view.setTrack(track);
            loadArtwork(track);
        }
        view.setPlayback(player != null && player.wantPlayback(), player == null ? 0 : player.positionMs(),
            player == null ? 0 : player.durationMs());
        ui.removeCallbacks(tick);
        if (player != null && player.isPlaying()) ui.postDelayed(tick, 500);
    }

    private void loadArtwork(MusicLibrary.Track track) {
        cancelArtwork();
        artworkLoaded = false;
        view.setArtwork(null);
        if (track == null) return;
        int generation = artworkGeneration;
        Context context = getApplicationContext();
        artworkJob = artworkWorker.submit(() -> {
            Bitmap bitmap = AlbumArtwork.load(context, track.uri);
            ui.post(() -> {
                if (visible && generation == artworkGeneration) {
                    artworkLoaded = true;
                    artworkJob = null;
                    view.setArtwork(bitmap);
                }
            });
        });
    }

    private void cancelArtwork() {
        artworkGeneration++;
        if (artworkJob != null) artworkJob.cancel(true);
        artworkJob = null;
    }

    @Override protected boolean onCrown(float pixels) {
        crownAccumulator += pixels;
        float perStep = Ui.dp(this, 23);
        int steps = (int) (crownAccumulator / perStep);
        if (steps == 0) return true;
        crownAccumulator -= steps * perStep;
        for (int i = 0; i < Math.abs(steps); i++) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, steps > 0 ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0);
        }
        view.setOverlay("Volume " + audio.getStreamVolume(AudioManager.STREAM_MUSIC) + "/" + audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        ui.removeCallbacks(clearOverlay);
        ui.postDelayed(clearOverlay, 1200);
        return true;
    }

    @Override public void onPrevious() { if (player != null) player.previous(); }
    @Override public void onToggle() { if (player != null) player.toggle(); }
    @Override public void onNext() { if (player != null) player.next(); }
    @Override public void onLibrary() {
        startActivity(new Intent(this, MusicActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }
    @Override public void onSeek(float fraction) {
        if (player != null && player.durationMs() > 0) player.seekTo(Math.round(fraction * player.durationMs()));
    }
}
