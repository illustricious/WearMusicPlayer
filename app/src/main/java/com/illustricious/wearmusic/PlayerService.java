package com.illustricious.wearmusic;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PlayerService extends Service {
    interface Listener { void onPlayerChanged(); }

    private static final String TOGGLE = "com.illustricious.wearmusic.TOGGLE";
    private static final String NEXT = "com.illustricious.wearmusic.NEXT";
    private static final String PREVIOUS = "com.illustricious.wearmusic.PREV";
    private static final String STOP = "com.illustricious.wearmusic.STOP";
    private static final String CHANNEL = "playback";
    private static final int NOTIFICATION_ID = 1;
    private static final long PAUSED_TIMEOUT_MS = 10 * 60 * 1000L;

    final class LocalBinder extends Binder { PlayerService service() { return PlayerService.this; } }
    private final IBinder binder = new LocalBinder();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private final AudioAttributes attributes = new AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
    private AudioManager audio;
    private AudioFocusRequest focusRequest;
    private MediaPlayer player;
    private MediaSession session;
    private NotificationManager notifications;
    private PendingIntent openPlayer;
    private List<MusicLibrary.Track> queue = Collections.emptyList();
    private MusicLibrary.Track publishedTrack;
    private int index = -1, focusGeneration;
    private int repeatMode;
    private long sleepDeadline;
    private final Runnable sleepTimeout = () -> { sleepDeadline = 0; pause(); };
    int repeatMode() { return repeatMode; }
    void setRepeatMode(int mode) { repeatMode = Math.max(0, Math.min(2, mode)); getSharedPreferences("playback", MODE_PRIVATE).edit().putInt("repeat", repeatMode).apply(); publish(); }
    void setSleepMinutes(int minutes) {
        if (minutes > 0 && current() == null) return;
        main.removeCallbacks(sleepTimeout);
        sleepDeadline = minutes > 0 ? SystemClock.elapsedRealtime() + minutes * 60000L : 0;
        if (minutes > 0) main.postDelayed(sleepTimeout, minutes * 60000L);
        publish();
    }
    int sleepMinutes() { return sleepDeadline == 0 ? 0 : (int) Math.max(1, (sleepDeadline - SystemClock.elapsedRealtime() + 59999) / 60000); }

    private boolean prepared, wantsPlay, resumeOnFocus, focused, foreground;
    private boolean notificationPlaying;
    private final Runnable pausedTimeout = () -> {
        if (!wantsPlay) stopPlayback();
    };

    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { pause(); }
    };

    @Override public void onCreate() {
        super.onCreate();
        repeatMode = getSharedPreferences("playback", MODE_PRIVATE).getInt("repeat", 0);
        audio = getSystemService(AudioManager.class);
        notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(CHANNEL, "Playback", NotificationManager.IMPORTANCE_LOW));
        Intent open = new Intent(this, NowPlayingActivity.class)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        openPlayer = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        session = new MediaSession(this, "Music");
        session.setSessionActivity(openPlayer);
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { resume(); }
            @Override public void onPause() { pause(); }
            @Override public void onSkipToNext() { next(); }
            @Override public void onSkipToPrevious() { previous(); }
            @Override public void onSeekTo(long position) {
                seekTo((int) Math.max(0, Math.min(position, durationMs())));
            }
            @Override public void onStop() { stopPlayback(); }
        }, main);
        registerReceiver(noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
        publish();
    }

    @Override public IBinder onBind(Intent intent) { return binder; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (current() == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        String action = intent == null ? null : intent.getAction();
        if (TOGGLE.equals(action)) toggle();
        else if (NEXT.equals(action)) next();
        else if (PREVIOUS.equals(action)) previous();
        else if (STOP.equals(action)) stopPlayback();
        return START_NOT_STICKY;
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        if (!wantPlayback()) stopPlayback();
    }

    @Override public void onDestroy() {
        main.removeCallbacksAndMessages(null);
        unregisterReceiver(noisy);
        releasePlayer();
        abandonFocus();
        session.release();
        super.onDestroy();
    }

    void addListener(Listener listener) { listeners.add(listener); }
    void removeListener(Listener listener) { listeners.remove(listener); }

    MusicLibrary.Track current() { return index >= 0 && index < queue.size() ? queue.get(index) : null; }
    boolean isPlaying() { return prepared && player.isPlaying(); }
    boolean wantPlayback() { return wantsPlay || resumeOnFocus; }
    int positionMs() { return prepared ? player.getCurrentPosition() : 0; }
    int durationMs() { return prepared ? player.getDuration() : 0; }

    void playQueue(List<MusicLibrary.Track> tracks, int start) {
        if (start < 0 || start >= tracks.size()) return;
        queue = new ArrayList<>(tracks);
        index = start;
        resumeOnFocus = false;
        wantsPlay = true;
        if (!foreground) {
            startService(new Intent(this, PlayerService.class));
            startForeground(NOTIFICATION_ID, buildNotification(current(), false),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            foreground = true;
            notificationPlaying = false;
        }
        prepareCurrent();
    }

    void toggle() { if (wantPlayback()) pause(); else resume(); }

    private void resume() {
        if (current() == null) return;
        resumeOnFocus = false;
        wantsPlay = true;
        main.removeCallbacks(pausedTimeout);
        if (player == null) prepareCurrent();
        else if (prepared) startPrepared();
        else publish();
    }

    private void pause() {
        if (current() == null) return;
        wantsPlay = false;
        resumeOnFocus = false;
        if (isPlaying()) player.pause();
        abandonFocus();
        schedulePausedTimeout();
        publish();
    }

    private void schedulePausedTimeout() {
        main.removeCallbacks(pausedTimeout);
        main.postDelayed(pausedTimeout, PAUSED_TIMEOUT_MS);
    }

    void next() {
        if (index + 1 >= queue.size()) {
            if (repeatMode == 1 && !queue.isEmpty()) { index = 0; prepareCurrent(); }
            else stopPlayback();
            return;
        }
        index++;
        prepareCurrent();
    }

    void previous() {
        if (index > 0 && positionMs() <= 3000) {
            index--;
            prepareCurrent();
        } else seekTo(0);
    }

    void seekTo(int ms) {
        if (prepared && durationMs() > 0) {
            player.seekTo(Math.max(0, Math.min(ms, durationMs())), MediaPlayer.SEEK_CLOSEST);
        }
    }

    private void stopPlayback() {
        main.removeCallbacks(pausedTimeout);
        main.removeCallbacks(sleepTimeout);
        sleepDeadline = 0;
        wantsPlay = false;
        resumeOnFocus = false;
        releasePlayer();
        abandonFocus();
        index = -1;
        queue = Collections.emptyList();
        if (foreground) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            foreground = false;
        }
        publish();
        stopSelf();
    }

    private void prepareCurrent() {
        MusicLibrary.Track track = current();
        releasePlayer();
        MediaPlayer candidate = new MediaPlayer();
        player = candidate;
        try {
            candidate.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            candidate.setAudioAttributes(attributes);
            candidate.setOnPreparedListener(mp -> {
                if (mp != player) return;
                prepared = true;
                if (wantsPlay) startPrepared();
                else publish();
            });
            candidate.setOnSeekCompleteListener(mp -> {
                if (mp == player) publish();
            });
            candidate.setOnCompletionListener(mp -> {
                if (mp == player && wantsPlay) {
                    if (repeatMode == 2) { seekTo(0); startPrepared(); }
                    else next();
                }
            });
            candidate.setOnErrorListener((mp, what, extra) -> {
                if (mp == player) prepared = false;
                skipFailedPlayer(mp);
                return true;
            });
            candidate.setDataSource(this, track.uri);
            candidate.prepareAsync();
        } catch (java.io.IOException | RuntimeException failure) {
            skipFailedPlayer(candidate);
        }
        publish();
    }

    private void skipFailedPlayer(MediaPlayer failed) {
        main.post(() -> {
            if (failed != player) return;
            next();
        });
    }

    private void startPrepared() {
        if (sleepDeadline > 0 && SystemClock.elapsedRealtime() >= sleepDeadline) { sleepTimeout.run(); return; }
        if (!requestFocus()) {
            wantsPlay = false;
            schedulePausedTimeout();
        } else if (!isPlaying()) {
            player.start();
        }
        publish();
    }

    private void releasePlayer() {
        prepared = false;
        if (player != null) {
            player.release();
            player = null;
        }
    }

    private boolean requestFocus() {
        if (focused) return true;
        abandonFocus();
        int generation = focusGeneration;
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(change -> { if (generation == focusGeneration) onFocusChange(change); }, main)
            .build();
        focused = audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if (!focused) focusRequest = null;
        return focused;
    }

    private void onFocusChange(int change) {
        if (focusRequest == null) return;
        if (change == AudioManager.AUDIOFOCUS_LOSS) {
            pause();
        } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            focused = false;
            resumeOnFocus = wantsPlay || resumeOnFocus;
            wantsPlay = false;
            if (isPlaying()) player.pause();
            schedulePausedTimeout();
            publish();
        } else if (change == AudioManager.AUDIOFOCUS_GAIN) {
            focused = true;
            if (resumeOnFocus) resume();
        }
    }

    private void abandonFocus() {
        focusGeneration++;
        if (focusRequest != null) {
            audio.abandonAudioFocusRequest(focusRequest);
            focusRequest = null;
        }
        focused = false;
    }

    private void publish() {
        MusicLibrary.Track track = current();
        boolean trackChanged = track != publishedTrack;
        boolean playing = isPlaying();
        if (trackChanged) {
            publishedTrack = track;
            session.setMetadata(track == null ? null : new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, track.album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, track.durationMs).build());
        }
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
            | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_STOP;
        if (durationMs() > 0) actions |= PlaybackState.ACTION_SEEK_TO;
        int state = PlaybackState.STATE_NONE;
        if (track != null) {
            state = playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
            if (wantsPlay && !prepared) state = PlaybackState.STATE_BUFFERING;
        }
        session.setPlaybackState(new PlaybackState.Builder().setActions(track == null ? 0 : actions)
            .setState(state, positionMs(), playing ? 1f : 0f, SystemClock.elapsedRealtime()).build());
        if (trackChanged) session.setActive(track != null);
        if (track != null && foreground && (trackChanged || wantPlayback() != notificationPlaying)) {
            notificationPlaying = wantPlayback();
            notifications.notify(NOTIFICATION_ID, buildNotification(track, notificationPlaying));
        }
        for (Listener listener : new ArrayList<>(listeners)) listener.onPlayerChanged();
    }

    private Notification buildNotification(MusicLibrary.Track track, boolean playing) {
        return new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(track.title)
            .setContentText(track.subtitle())
            .setContentIntent(openPlayer)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .addAction(action(android.R.drawable.ic_media_previous, "Previous", PREVIOUS))
            .addAction(action(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                playing ? "Pause" : "Play", TOGGLE))
            .addAction(action(android.R.drawable.ic_media_next, "Next", NEXT))
            .addAction(action(android.R.drawable.ic_menu_close_clear_cancel, "Stop", STOP))
            .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0, 1, 2))
            .build();
    }

    private Notification.Action action(int icon, String title, String action) {
        PendingIntent button = PendingIntent.getService(this, action.hashCode(), new Intent(this, PlayerService.class).setAction(action),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Action.Builder(Icon.createWithResource(this, icon), title, button).build();
    }
}
