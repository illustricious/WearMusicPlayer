package com.illustricious.wearmusic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

/**
 * Compact player card for the Home screen: title/artist on top (tap = open full Now Playing) and a row of
 * three equal-width touch zones (previous / play-pause / next) so taps never land on the wrong control.
 */
final class MiniPlayerView extends View {
    interface Callbacks { void onOpen(); void onPrevious(); void onToggle(); void onNext(); }

    private final Callbacks callbacks;
    private final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG),
        track = new Paint(Paint.ANTI_ALIAS_FLAG), bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint title = new TextPaint(Paint.ANTI_ALIAS_FLAG), artist = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private String titleText = "", artistText = "";
    private float progress;
    private boolean playing, hasTrack;
    private int down = -1;                       // 0 open, 1 prev, 2 toggle, 3 next

    MiniPlayerView(Context c, Callbacks callbacks) {
        super(c);
        this.callbacks = callbacks;
        float d = c.getResources().getDisplayMetrics().density;
        card.setColor(Ui.SURFACE);
        track.setColor(Ui.SURFACE_HI); bar.setColor(Ui.ACCENT);
        title.setColor(Ui.WHITE); title.setTextSize(15 * d); title.setTypeface(Typeface.DEFAULT_BOLD);
        artist.setColor(Ui.MUTED); artist.setTextSize(12 * d);
        setMinimumHeight(Math.round(112 * d));
    }

    void set(String titleText, String artistText, float progress, boolean playing, boolean hasTrack) {
        this.titleText = titleText == null ? "" : titleText; this.artistText = artistText == null ? "" : artistText;
        this.progress = progress; this.playing = playing; this.hasTrack = hasTrack;
        invalidate();
    }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), Math.round(112 * getResources().getDisplayMetrics().density));
    }

    private float d() { return getResources().getDisplayMetrics().density; }
    private float rowTop() { return getHeight() * 0.50f; }

    @Override protected void onDraw(Canvas c) {
        float d = d(), w = getWidth(), h = getHeight();
        rect.set(0, 0, w, h);
        c.drawRoundRect(rect, 26 * d, 26 * d, card);

        float pad = 16 * d;
        String t = hasTrack ? titleText : "No track selected";
        c.drawText(TextUtils.ellipsize(t, title, w - 2 * pad, TextUtils.TruncateAt.END).toString(), pad, 28 * d, title);
        String a = hasTrack ? artistText : "Browse Library to play";
        c.drawText(TextUtils.ellipsize(a, artist, w - 2 * pad, TextUtils.TruncateAt.END).toString(), pad, 46 * d, artist);

        float by = h - 6 * d;                                   // thin progress bar at the bottom of the card
        rect.set(pad, by - 3 * d, w - pad, by);
        c.drawRoundRect(rect, 2 * d, 2 * d, track);
        if (hasTrack && progress > 0) { rect.set(pad, by - 3 * d, pad + (w - 2 * pad) * Math.min(1f, progress), by); c.drawRoundRect(rect, 2 * d, 2 * d, bar); }

        float y = h * 0.68f;
        skip(c, w / 6f, y, true);
        skip(c, w * 5f / 6f, y, false);
        fill.setStyle(Paint.Style.FILL); fill.setColor(Ui.WHITE);
        c.drawCircle(w / 2f, y, 20 * d, fill);
        fill.setColor(Ui.BLACK);
        if (playing) {
            c.drawRoundRect(w / 2f - 7 * d, y - 8 * d, w / 2f - 2 * d, y + 8 * d, 1.5f * d, 1.5f * d, fill);
            c.drawRoundRect(w / 2f + 2 * d, y - 8 * d, w / 2f + 7 * d, y + 8 * d, 1.5f * d, 1.5f * d, fill);
        } else {
            path.reset(); path.moveTo(w / 2f - 5 * d, y - 9 * d); path.lineTo(w / 2f + 9 * d, y); path.lineTo(w / 2f - 5 * d, y + 9 * d); path.close();
            c.drawPath(path, fill);
        }
    }

    private void skip(Canvas c, float x, float y, boolean previous) {
        float d = d(), s = previous ? -1 : 1;
        fill.setStyle(Paint.Style.FILL); fill.setColor(hasTrack ? Ui.WHITE : Ui.SURFACE_HI);
        path.reset();
        path.moveTo(x - s * 8 * d, y - 8 * d); path.lineTo(x + s * 4 * d, y); path.lineTo(x - s * 8 * d, y + 8 * d); path.close();
        c.drawPath(path, fill);
        float a = x + s * 5 * d, b = x + s * 8.5f * d;
        c.drawRoundRect(Math.min(a, b), y - 8 * d, Math.max(a, b), y + 8 * d, 1.2f * d, 1.2f * d, fill);
    }

    private int zone(float x, float y) {
        if (y < rowTop()) return 0;
        int third = (int) (x / (getWidth() / 3f));
        return 1 + Math.max(0, Math.min(2, third));
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: down = zone(e.getX(), e.getY()); return true;
            case MotionEvent.ACTION_UP:
                if (down >= 0 && down == zone(e.getX(), e.getY())) {
                    performClick();
                    if (down == 0) callbacks.onOpen();
                    else if (hasTrack || down == 2) { if (down == 1) callbacks.onPrevious(); else if (down == 2) callbacks.onToggle(); else callbacks.onNext(); }
                }
                down = -1; return true;
            case MotionEvent.ACTION_CANCEL: down = -1; return true;
            default: return true;
        }
    }

    @Override public boolean performClick() { return super.performClick(); }
}
