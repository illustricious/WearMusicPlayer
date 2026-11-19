package com.illustricious.wearmusic;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.OperationCanceledException;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public final class MusicActivity extends CrownActivity {
    private static final int REQUEST_PERMISSIONS = 3;
    private final List<MusicLibrary.Track> results = new ArrayList<>();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ScheduledThreadPoolExecutor searchWorker = new ScheduledThreadPoolExecutor(1);
    private final Runnable runPendingSearch = this::runSearch;
    private CancellationSignal searchSignal;
    private ArrayAdapter<MusicLibrary.Track> adapter;
    private EditText search;
    private TextView info;
    private PlayerService player;
    private boolean bound;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            player = ((PlayerService.LocalBinder) binder).service();
        }
        @Override public void onServiceDisconnected(ComponentName name) { player = null; }
    };

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        ListView list = new ListView(this);
        Ui.styleList(list);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.addView(Ui.heading(this, "Library"));
        Button now = Ui.button(this, "Now Playing", view -> openPlayer());
        Ui.style(now, true);
        header.addView(now);

        search = new EditText(this);
        search.setHint("Title, artist, or album");
        search.setHintTextColor(Ui.MUTED);
        search.setTextColor(Ui.WHITE);
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setGravity(Gravity.CENTER);
        search.setBackground(Ui.pill(this, Ui.SURFACE));
        search.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 46));
        lp.topMargin = Ui.dp(this, 5);
        search.setLayoutParams(lp);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable e) {
                cancelSearch();
                ui.postDelayed(runPendingSearch, 350);
            }
        });
        search.setOnEditorActionListener((view, action, event) -> {
            runSearch();
            return false;
        });
        header.addView(search);
        info = Ui.text(this, "", 12, Ui.MUTED, false);
        info.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 4));
        header.addView(info);
        list.addHeaderView(header, null, false);

        adapter = new ArrayAdapter<MusicLibrary.Track>(this, android.R.layout.simple_list_item_2, android.R.id.text1, results) {
            @Override public View getView(int position, View convert, ViewGroup parent) {
                View row = super.getView(position, convert, parent);
                MusicLibrary.Track track = getItem(position);
                TextView title = row.findViewById(android.R.id.text1);
                TextView subtitle = row.findViewById(android.R.id.text2);
                title.setText(track.title);
                subtitle.setText(track.subtitle() + "  " + MusicLibrary.formatDuration(track.durationMs));
                if (convert == null) {
                    title.setTextSize(15);
                    title.setSingleLine(true);
                    title.setTextColor(Ui.WHITE);
                    subtitle.setTextSize(12);
                    subtitle.setSingleLine(true);
                    subtitle.setTextColor(Ui.MUTED);
                    row.setBackground(Ui.pill(MusicActivity.this, Ui.SURFACE));
                    row.setPadding(Ui.dp(MusicActivity.this, 16), Ui.dp(MusicActivity.this, 6),
                        Ui.dp(MusicActivity.this, 16), Ui.dp(MusicActivity.this, 6));
                }
                return row;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            int index = position - 1;
            if (index < 0 || index >= results.size() || player == null) return;
            player.playQueue(results, index);
            openPlayer();
        });
        setContentView(list);
        if (!Ui.granted(this, Ui.audioPermission())) {
            requestPermissions(new String[] {Ui.audioPermission()}, REQUEST_PERMISSIONS);
        }
    }

    private void openPlayer() {
        startActivity(new Intent(this, NowPlayingActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    @Override protected void onStart() {
        super.onStart();
        bound = bindService(new Intent(this, PlayerService.class), connection, Context.BIND_AUTO_CREATE);
        runSearch();
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(code, permissions, grants);
        if (code == REQUEST_PERMISSIONS) runSearch();
    }

    private void cancelSearch() {
        ui.removeCallbacks(runPendingSearch);
        if (searchSignal != null) {
            searchSignal.cancel();
            searchSignal = null;
        }
    }

    private void runSearch() {
        cancelSearch();
        if (!Ui.granted(this, Ui.audioPermission())) {
            results.clear();
            adapter.notifyDataSetChanged();
            info.setText("Allow music access in Settings > Apps > Music.");
            return;
        }
        String query = search.getText().toString().trim();
        Context context = getApplicationContext();
        CancellationSignal signal = new CancellationSignal();
        searchSignal = signal;
        searchWorker.execute(() -> {
            try {
                signal.throwIfCanceled();
                List<MusicLibrary.Track> found = MusicLibrary.search(context, query, signal);
                ui.post(() -> {
                    if (searchSignal != signal) return;
                    results.clear();
                    results.addAll(found);
                    adapter.notifyDataSetChanged();
                    if (found.isEmpty()) {
                        info.setText(query.isEmpty() ? "Copy audio files to the Music folder on your watch."
                            : "No results for \"" + query + "\"");
                    } else {
                        String count = found.size() >= MusicLibrary.MAX_RESULTS ? found.size() + "+" : Integer.toString(found.size());
                        info.setText(count + (found.size() == 1 ? " track" : " tracks"));
                    }
                });
            } catch (OperationCanceledException ignored) {
                // A newer query or a screen change superseded this one.
            }
        });
    }

    @Override protected void onStop() {
        cancelSearch();
        if (bound) unbindService(connection);
        bound = false;
        player = null;
        super.onStop();
    }

    @Override protected void onDestroy() {
        searchWorker.shutdownNow();
        super.onDestroy();
    }
}
