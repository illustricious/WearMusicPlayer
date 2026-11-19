package com.illustricious.wearmusic;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.provider.MediaStore;
import java.util.ArrayList;
import java.util.List;

/** Searches audio files that Android's MediaStore has indexed on this device. */
final class MusicLibrary {
    private MusicLibrary() {}

    static final class Track {
        final long id; final String title, artist, album; final long durationMs; final Uri uri;
        Track(long id, String title, String artist, String album, long durationMs, Uri uri) {
            this.id = id; this.title = title == null || title.isEmpty() ? "Unknown title" : title; this.artist = artist; this.album = album; this.durationMs = durationMs; this.uri = uri;
        }
        String subtitle() {
            boolean noArtist = artist == null || artist.length() == 0 || "<unknown>".equals(artist);
            return noArtist ? (album == null || "<unknown>".equals(album) ? "" : album) : artist;
        }
    }

    static final int MAX_RESULTS = 300;

    /**
     * Every whitespace-separated word of {@code query} must appear in the title, artist or album
     * (case-insensitive). An empty query lists the whole library.
     */
    static List<Track> search(Context context, String query, CancellationSignal signal) {
        List<Track> out = new ArrayList<Track>();
        StringBuilder where = new StringBuilder(MediaStore.Audio.Media.IS_MUSIC + " != 0");
        List<String> args = new ArrayList<String>();
        if (query != null) {
            for (String word : query.trim().split("\\s+")) {
                if (word.length() == 0) continue;
                String like = "%" + escapeLike(word) + "%";
                where.append(" AND (").append(MediaStore.Audio.Media.TITLE).append(" LIKE ? ESCAPE '\\' OR ")
                     .append(MediaStore.Audio.Media.ARTIST).append(" LIKE ? ESCAPE '\\' OR ")
                     .append(MediaStore.Audio.Media.ALBUM).append(" LIKE ? ESCAPE '\\')");
                args.add(like); args.add(like); args.add(like);
            }
        }
        String[] cols = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION};
        ContentResolver cr = context.getContentResolver();
        Cursor c = null;
        try {
            c = cr.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols, where.toString(),
                args.toArray(new String[0]), MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC", signal);
            if (c == null) return out;
            while (c.moveToNext() && out.size() < MAX_RESULTS) {
                long id = c.getLong(0);
                out.add(new Track(id, c.getString(1), c.getString(2), c.getString(3), c.getLong(4),
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)));
            }
        } catch (SecurityException denied) {
            // Permission not granted yet: behave like an empty library; the UI explains what to do.
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    static String formatDuration(long ms) {
        long seconds = Math.max(0, ms) / 1000;
        return (seconds / 60) + ":" + (seconds % 60 < 10 ? "0" : "") + (seconds % 60);
    }
}
