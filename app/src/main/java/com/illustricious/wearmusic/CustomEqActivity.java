package com.illustricious.wearmusic;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/** Eight-band custom equalizer, 0.5 dB steps over the +/-6 dB range OpenSCQ30 uses for custom curves. */
public final class CustomEqActivity extends CrownActivity implements Controller.Observer {
    private static final int STEP = 5;                                   // tenths of a dB per notch
    private static final int NOTCHES = (Soundcore.EQ_MAX - Soundcore.EQ_MIN) / STEP;   // 24
    private Controller controller;
    private final SeekBar[] bars = new SeekBar[Soundcore.VISIBLE_BANDS];
    private final TextView[] labels = new TextView[Soundcore.VISIBLE_BANDS];
    private TextView status;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        controller = Controller.get(this);
        LinearLayout col = Ui.page(this);
        col.addView(Ui.heading(this, "Custom EQ"));
        int[] start = initialBands();
        for (int i = 0; i < Soundcore.VISIBLE_BANDS; i++) {
            final int band = i;
            labels[i] = Ui.text(this, "", 13, Ui.ACCENT, false);
            col.addView(labels[i]);
            SeekBar bar = new SeekBar(this);
            bar.setMax(NOTCHES);
            bar.setProgress(Soundcore.clamp(Math.round((start[i] - Soundcore.EQ_MIN) / (float) STEP), 0, NOTCHES));
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar s, int p, boolean user) { updateLabel(band); }
                public void onStartTrackingTouch(SeekBar s) {}
                public void onStopTrackingTouch(SeekBar s) {}
            });
            bars[i] = bar;
            col.addView(bar);
            updateLabel(i);
        }
        col.addView(Ui.button(this, "Apply", new View.OnClickListener() {
            public void onClick(View v) { controller.setEqualizer(Soundcore.PRESET_CUSTOM, Soundcore.customBands(values()), "Custom"); } }));
        col.addView(Ui.button(this, "Reset to 0 dB", new View.OnClickListener() {
            public void onClick(View v) { for (SeekBar b : bars) b.setProgress(NOTCHES / 2); } }));
        status = Ui.text(this, "", 12, Ui.MUTED, false);
        col.addView(status);
    }

    private int[] initialBands() {
        Soundcore.State s = controller.state();
        int[] src = new int[Soundcore.VISIBLE_BANDS];
        if (s != null) System.arraycopy(s.eq, 0, src, 0, Soundcore.VISIBLE_BANDS);
        return src;
    }

    private int[] values() {
        int[] v = new int[Soundcore.VISIBLE_BANDS];
        for (int i = 0; i < v.length; i++) v[i] = Soundcore.EQ_MIN + bars[i].getProgress() * STEP;
        return v;
    }

    private void updateLabel(int band) {
        int tenths = Soundcore.EQ_MIN + bars[band].getProgress() * STEP;
        int hz = Soundcore.BAND_HZ[band];
        labels[band].setText((hz >= 1000 ? (hz / 1000f) + " kHz" : hz + " Hz").replace(".0 ", " ")
            + "   " + (tenths > 0 ? "+" : "") + (tenths / 10f) + " dB");
    }

    @Override protected void onStart() { super.onStart(); controller.addObserver(this); onControllerChanged(); }
    @Override protected void onStop() { controller.removeObserver(this); super.onStop(); }
    @Override public void onControllerChanged() { status.setText(controller.isConnected() ? controller.message() : "Disconnected"); }
}
