package com.illustricious.wearmusic;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Path;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
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
        void onSeek(float fraction);
    }

    private static final int ACCENT = 0xffbaffd5;
    private static final int INK = 0xff101c17;
    private static final int WHITE = 0xfff5fff9;
    private static final int MUTED = 0xffc0d2c8;
    private final TextView heading, title, artist, time, library;
    private final ImageButton previous, toggle, next;
    private final Backdrop backdrop;
    private int positionMs, durationMs;
    private boolean hasTrack, pauseIcon;
    private String overlay;
    private int displayedSecond = -1;
    private String displayTime = "";

    PlayerView(Context context, Callbacks callbacks) {
        super(context);
        setBackgroundColor(0xff0b1512);
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
        library.setBackground(surface(0xc9354940, false));
        library.setFocusable(true);
        library.setOnClickListener(view -> callbacks.onLibrary());
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
        button.setBackground(surface(primary ? ACCENT : 0xd932453c, primary));
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
        place(heading, size, 0.50f, 0.145f, 0.57f, 0.09f, 0.053f);
        place(title, size, 0.50f, 0.33f, 0.73f, 0.20f, 0.090f);
        place(artist, size, 0.50f, 0.465f, 0.76f, 0.09f, 0.061f);
        place(time, size, 0.50f, 0.548f, 0.68f, 0.08f, 0.053f);
        place(previous, size, 0.244f, 0.706f, 0.232f, 0.232f, 0);
        place(toggle, size, 0.50f, 0.706f, 0.262f, 0.262f, 0);
        place(next, size, 0.756f, 0.706f, 0.232f, 0.232f, 0);
        place(library, size, 0.50f, 0.888f, 0.37f, 0.125f, 0.057f);
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

    void setOverlay(String text) {
        overlay = text;
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
        String value = overlay != null ? overlay : durationMs > 0 ? displayTime : hasTrack ? "" : "Open Library to play";
        if (!TextUtils.equals(time.getText(), value)) time.setText(value);
    }

    private final class Backdrop extends View {
        private final Callbacks callbacks;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Rect artSource = new Rect();
        private final RectF disc = new RectF();
        private final Path artClip = new Path();
        private final RectF ring = new RectF();
        private Shader shade;
        private Bitmap artwork;
        private float radius, centerX, centerY, stroke;
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
            radius = size * 0.475f;
            ring.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
            disc.set(centerX - size * 0.445f, centerY - size * 0.445f, centerX + size * 0.445f, centerY + size * 0.445f);
            artClip.reset();
            artClip.addOval(disc, Path.Direction.CW);
            shade = h > 0 ? new LinearGradient(0, 0, 0, h, 0x32101b16, 0xf20a120e, Shader.TileMode.CLAMP) : null;
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
                paint.setAlpha(105);
                canvas.drawBitmap(artwork, artSource, disc, paint);
                paint.setAlpha(255);
                canvas.restore();
            } else {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(stroke / 2);
                paint.setColor(0xff354f42);
                for (int band = 1; band <= 5; band++) {
                    canvas.drawCircle(centerX, centerY, disc.width() * band / 12f, paint);
                }
                paint.setStyle(Paint.Style.FILL);
            }
            paint.setShader(shade);
            canvas.drawOval(disc, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(0xff344940);
            canvas.drawArc(ring, -90, 360, false, paint);
            float fraction = preview >= 0 ? preview : progress;
            if (fraction > 0) {
                paint.setColor(ACCENT);
                canvas.drawArc(ring, -90, 360 * fraction, false, paint);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX() - centerX, y = event.getY() - centerY;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (durationMs <= 0 || Math.abs(Math.hypot(x, y) - radius) > radius * 0.13f) return false;
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
            double angle = Math.atan2(x, -y) / (Math.PI * 2);
            return (float) (angle < 0 ? angle + 1 : angle);
        }
    }
}
