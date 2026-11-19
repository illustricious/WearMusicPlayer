package com.illustricious.wearmusic;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

/** Preset picker; "Custom" opens the 8-band editor. */
public final class EqualizerActivity extends CrownActivity implements Controller.Observer {
    private Controller controller;
    private TextView current, status;
    private ArrayAdapter<String> adapter;
    private final String[] names = new String[Soundcore.PRESETS.length];

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        controller = Controller.get(this);
        ListView list = new ListView(this);
        Ui.styleList(list);
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.addView(Ui.heading(this, "Equalizer"));
        current = Ui.text(this, "", 13, Ui.ACCENT, false);
        header.addView(current);
        status = Ui.text(this, "", 12, Ui.MUTED, false);
        header.addView(status);
        header.addView(Ui.button(this, "Custom (8 band)…", new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(EqualizerActivity.this, CustomEqActivity.class)); } }));
        list.addHeaderView(header, null, false);
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, names) {
            @Override public View getView(int position, View convert, ViewGroup parent) {
                TextView t = (TextView) super.getView(position, convert, parent);
                t.setGravity(android.view.Gravity.CENTER);
                t.setTextSize(15); t.setTextColor(Ui.WHITE);
                t.setBackground(Ui.pill(EqualizerActivity.this, Ui.SURFACE));
                t.setMinHeight(Ui.dp(EqualizerActivity.this, 44));
                return t;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            public void onItemClick(android.widget.AdapterView<?> p, View v, int position, long id) {
                int i = position - 1;
                if (i < 0 || i >= Soundcore.PRESETS.length) return;
                Soundcore.Preset preset = Soundcore.PRESETS[i];
                controller.setEqualizer(preset.id, preset.bands, preset.name);
            }
        });
        setContentView(list);
    }

    @Override protected void onStart() { super.onStart(); controller.addObserver(this); onControllerChanged(); }
    @Override protected void onStop() { controller.removeObserver(this); super.onStop(); }

    @Override public void onControllerChanged() {
        Soundcore.State s = controller.state();
        int activeId = controller.isConnected() && s != null ? s.presetId : -1;
        for (int i = 0; i < names.length; i++) names[i] = (Soundcore.PRESETS[i].id == activeId ? "✓ " : "") + Soundcore.PRESETS[i].name;
        adapter.notifyDataSetChanged();
        if (!controller.isConnected() || s == null) current.setText("Disconnected");
        else if (s.presetId == Soundcore.PRESET_CUSTOM) current.setText("Active: Custom");
        else {
            Soundcore.Preset p = Soundcore.presetById(s.presetId);
            current.setText("Active: " + (p == null ? "ID " + s.presetId : p.name));
        }
        status.setText(controller.message());
    }
}
