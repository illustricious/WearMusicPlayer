package com.illustricious.wearmusic;

import android.app.Activity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ScrollView;

/**
 * Base for every screen: rotating the digital crown / bezel scrolls the page. Screens that want the
 * crown for something else (Now Playing: volume) override {@link #onCrown(float)}.
 */
abstract class CrownActivity extends Activity {
    @Override protected void onCreate(android.os.Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }
    private static final float SPEED = 1.6f;

    @Override public boolean onGenericMotionEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_SCROLL && e.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            float px = -e.getAxisValue(MotionEvent.AXIS_SCROLL) * ViewConfiguration.get(this).getScaledVerticalScrollFactor() * SPEED;
            if (onCrown(px)) return true;
        }
        return super.onGenericMotionEvent(e);
    }

    /** @param pixels positive = scroll content down. Return true if consumed. */
    protected boolean onCrown(float pixels) {
        View scrollable = findScrollable(getWindow().getDecorView());
        if (scrollable instanceof AbsListView) { ((AbsListView) scrollable).scrollListBy(Math.round(pixels)); return true; }
        if (scrollable instanceof ScrollView) { scrollable.scrollBy(0, Math.round(pixels)); return true; }
        return false;
    }

    @Override protected void onResume() {
        super.onResume();
        View scrollable = findScrollable(getWindow().getDecorView());
        if (scrollable != null) scrollable.requestFocus();   // rotary events are delivered to the focused view
    }

    private static View findScrollable(View v) {
        if (v instanceof AbsListView || v instanceof ScrollView) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View found = findScrollable(g.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
}
