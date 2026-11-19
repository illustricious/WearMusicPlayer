package com.illustricious.wearmusic;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

final class PlayerView extends FrameLayout {
    interface Callbacks {
        void onPrevious();
        void onToggle();
        void onNext();
        void onLibrary();
        void onOptions();
        void onSeek(float fraction);
    }

    private static final int ACCENT = 0xffc5f56f;
    private static final int INK = 0xff101c17;
    private static final int WHITE = 0xfff5fff9;
    private static final int MUTED = 0xffc0d2c8;
    private final TextView heading, title, artist, time, library, options;
    private final ImageButton previous, toggle, next;
    private final Backdrop backdrop;
    private int positionMs, durationMs;
    private boolean hasTrack, pauseIcon;
    private int displayedSecond = -1;
    private String displayTime = "";

    PlayerView(Context context, Callbacks callbacks) {
        super(context);
        setBackgroundColor(0xff000000);
        backdrop = new Backdrop(context, callbacks);
        addView(backdrop, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        heading = label("Now Playing", ACCENT, true);
        title = label("No track selected", WHITE, true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        artist = label("", MUTED, false);
        artist.setSingleLine();
        artist.setEllipsize(TextUtils.TruncateAt.END);
        time = label("", MUTED, false);
        time.setTypeface(Typeface.MONOSPACE);
        previous = control(android.R.drawable.ic_media_previous, "Previous track", false, callbacks::onPrevious);
        toggle = control(android.R.drawable.ic_media_play, "Play", true, callbacks::onToggle);
        next = control(android.R.drawable.ic_media_next, "Next track", false, callbacks::onNext);
        library = label("Library", WHITE, true);
        library.setBackground(surface(0xff1d292d, false));
        library.setFocusable(true);
        library.setOnClickListener(view -> callbacks.onLibrary());
        options = label("Options", WHITE, true);
        options.setBackground(surface(0xff1d292d, false));
        options.setFocusable(true);
        options.setOnClickListener(view -> callbacks.onOptions());
        previous.setEnabled(false);
        toggle.setEnabled(false);
        next.setEnabled(false);
        updateTime();
    }

    private TextView label(String text, int color, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setIncludeFontPadding(false);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        addView(view, new LayoutParams(0, 0));
        return view;
    }

    private ImageButton control(int icon, String description, boolean primary, Runnable action) {
        ImageButton button = new ImageButton(getContext());
        button.setImageResource(icon);
        button.setImageTintList(new ColorStateList(
            new int[][] {new int[] {-android.R.attr.state_enabled}, new int[0]},
            new int[] {0xff899890, primary ? INK : WHITE}));
        button.setContentDescription(description);
        button.setBackground(surface(primary ? ACCENT : 0xff1d292d, primary));
        button.setPadding(Ui.dp(getContext(), 7), Ui.dp(getContext(), 7), Ui.dp(getContext(), 7), Ui.dp(getContext(), 7));
        button.setOnClickListener(view -> action.run());
        addView(button, new LayoutParams(0, 0));
        return button;
    }

    private RippleDrawable surface(int color, boolean primary) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(Ui.dp(getContext(), 32));
        return new RippleDrawable(ColorStateList.valueOf(primary ? 0x44101c17 : 0x44ffffff), shape, null);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
        int size = Math.min(width, height);
        horizontalInset = (width - size) / 2f;
        verticalInset = (height - size) / 2f;
        place(heading, size, .5f, .115f, .55f, .06f, .036f);
        place(title, size, .5f, .38f, .72f, .15f, .055f);
        place(artist, size, .5f, .49f, .70f, .05f, .043f);
        place(previous, size, .25f, .635f, .205f, .205f, 0);
        place(toggle, size, .5f, .635f, .23f, .23f, 0);
        place(next, size, .75f, .635f, .205f, .205f, 0);
        place(time, size, .5f, .805f, .54f, .04f, .037f);
        place(library, size, .375f, .88f, .24f, .10f, .037f);
        place(options, size, .635f, .88f, .24f, .10f, .037f);
        textSize = size;
        super.onMeasure(widthSpec, heightSpec);
    }

    private void place(View child, int size, float centerX, float centerY, float width, float height, float typeScale) {
        LayoutParams params = (LayoutParams) child.getLayoutParams();
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.width = Math.round(size * width);
        params.height = Math.round(size * height);
        params.leftMargin = Math.round(horizontalInset + size * centerX - params.width / 2f);
        params.topMargin = Math.round(verticalInset + size * centerY - params.height / 2f);
        if (typeScale > 0 && textSize != size) ((TextView) child).setTextSize(TypedValue.COMPLEX_UNIT_PX, size * typeScale);
    }

    private float horizontalInset, verticalInset;
    private int textSize = -1;

    void setTrack(MusicLibrary.Track track) {
        hasTrack = track != null;
        title.setText(hasTrack ? track.title : "No track selected");
        artist.setText(hasTrack ? track.subtitle() : "");
        previous.setEnabled(hasTrack);
        toggle.setEnabled(hasTrack);
        next.setEnabled(hasTrack);
        displayedSecond = -1;
        setPosition(0, 0);
    }

    void setPlayback(boolean wantsPlay, int position, int duration) {
        if (pauseIcon != wantsPlay) {
            pauseIcon = wantsPlay;
            toggle.setImageResource(wantsPlay ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
            toggle.setContentDescription(wantsPlay ? "Pause" : "Play");
        }
        setPosition(position, duration);
    }

    void setPosition(int position, int duration) {
        if (durationMs != duration) displayedSecond = -1;
        positionMs = position;
        durationMs = duration;
        backdrop.setProgress(duration > 0 ? Math.max(0, Math.min(1f, position / (float) duration)) : 0);
        updateTime();
    }

    void setArtwork(Bitmap art) {
        backdrop.setArtwork(art);
    }

    private void updateTime() {
        int position = backdrop.preview < 0 ? positionMs : Math.round(durationMs * backdrop.preview);
        if (position / 1000 != displayedSecond) {
            displayedSecond = position / 1000;
            displayTime = MusicLibrary.formatDuration(position) + " / " + MusicLibrary.formatDuration(durationMs);
        }
        String value = durationMs > 0 ? displayTime : hasTrack ? "" : "Open Library to play";
        if (!TextUtils.equals(time.getText(), value)) time.setText(value);
    }

    private final class Backdrop extends View {
        private final Callbacks callbacks;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Rect artSource = new Rect();
        private final RectF disc = new RectF();
        private final Path artClip = new Path();
        private final RectF ring = new RectF();
        private Bitmap artwork;
        private float centerX, centerY, stroke;
        private float progress, preview = -1;

        Backdrop(Context context, Callbacks callbacks) {
            super(context);
            this.callbacks = callbacks;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            centerX = w / 2f;
            centerY = h / 2f;
            float size = Math.min(w, h);
            stroke = size * 0.013f;
            disc.set(centerX - size * .07f, size * .16f, centerX + size * .07f, size * .30f);
            ring.set(w * .25f, h * .77f, w * .75f, h * .77f);
            artClip.reset();
            artClip.addOval(disc, Path.Direction.CW);
        }

        void setArtwork(Bitmap bitmap) {
            artwork = bitmap;
            if (bitmap != null) {
                int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
                int x = (bitmap.getWidth() - size) / 2, y = (bitmap.getHeight() - size) / 2;
                artSource.set(x, y, x + size, y + size);
            }
            invalidate();
        }

        void setProgress(float value) {
            if (progress != value) {
                progress = value;
                invalidate();
            }
        }

        @Override protected void onDraw(Canvas canvas) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xff23382e);
            canvas.drawOval(disc, paint);
            if (artwork != null) {
                canvas.save();
                canvas.clipPath(artClip);
                paint.setAlpha(255);
                canvas.drawBitmap(artwork, artSource, disc, paint);
                paint.setAlpha(255);
                canvas.restore();
            } else {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(stroke / 2);
                paint.setColor(0xff354f42);
                for (int band = 1; band <= 5; band++) {
                    canvas.drawCircle(disc.centerX(), disc.centerY(), disc.width() * band / 12f, paint);
                }
                paint.setStyle(Paint.Style.FILL);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(0xff2b363b);
            canvas.drawLine(ring.left, ring.top, ring.right, ring.top, paint);
            float fraction = preview >= 0 ? preview : progress;
            if (fraction > 0) {
                paint.setColor(ACCENT);
                canvas.drawLine(ring.left, ring.top, ring.left + ring.width() * fraction, ring.top, paint);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX() - centerX, y = event.getY() - centerY;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (durationMs <= 0 || event.getX() < ring.left || event.getX() > ring.right || Math.abs(event.getY() - ring.top) > getHeight() * .035f) return false;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (preview >= 0 && event.getActionMasked() == MotionEvent.ACTION_UP && durationMs > 0) {
                        callbacks.onSeek(fractionAt(x, y));
                    }
                    preview = -1;
                    updateTime();
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    break;
                default:
                    return true;
            }
            preview = fractionAt(x, y);
            updateTime();
            invalidate();
            return true;
        }

        private float fractionAt(float x, float y) {
            return Math.max(0f, Math.min(1f, (x + centerX - ring.left) / ring.width()));
        }
    }
}
