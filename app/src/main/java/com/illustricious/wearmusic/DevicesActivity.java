package com.illustricious.wearmusic;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Lists the watch's paired Bluetooth devices and lets the user pick the earbuds to control. */
public final class DevicesActivity extends CrownActivity {
    private Controller controller;
    private final List<BluetoothDevice> devices = new ArrayList<BluetoothDevice>();
    private ArrayAdapter<BluetoothDevice> adapter;
    private TextView hint;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        controller = Controller.get(this);
        ListView list = new ListView(this);
        Ui.styleList(list);
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.addView(Ui.heading(this, "Choose earbuds"));
        hint = Ui.text(this, "", 12, Ui.MUTED, false);
        header.addView(hint);
        header.addView(Ui.button(this, "Pairing baru…", new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); } }));
        if (controller.hasDevice()) header.addView(Ui.button(this, "Forget earbuds", new View.OnClickListener() {
            public void onClick(View v) { controller.forget(); finish(); } }));
        list.addHeaderView(header, null, false);
        adapter = new ArrayAdapter<BluetoothDevice>(this, android.R.layout.simple_list_item_2, android.R.id.text1, devices) {
            @Override public View getView(int position, View convert, ViewGroup parent) {
                View row = super.getView(position, convert, parent);
                BluetoothDevice d = getItem(position);
                String name = nameOf(d);
                Soundcore.Profile suggested = Soundcore.suggestProfile(name);
                boolean selected = d.getAddress().equals(controller.deviceMac());
                TextView t1 = (TextView) row.findViewById(android.R.id.text1), t2 = (TextView) row.findViewById(android.R.id.text2);
                t1.setText((selected ? "✓ " : "") + name); t1.setTextSize(15);
                t2.setText(suggested != null ? suggested.title : Soundcore.looksLikeSoundcore(name) ? "Unknown model. Tap to choose." : "Unrecognized device");
                t2.setTextColor(suggested != null ? Ui.OK : Ui.MUTED);
                row.setBackground(Ui.pill(DevicesActivity.this, Ui.SURFACE));
                row.setPadding(Ui.dp(DevicesActivity.this, 16), Ui.dp(DevicesActivity.this, 6), Ui.dp(DevicesActivity.this, 16), Ui.dp(DevicesActivity.this, 6));
                t1.setTextColor(Ui.WHITE);
                return row;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            public void onItemClick(android.widget.AdapterView<?> p, View v, int position, long id) {
                int i = position - 1;
                if (i >= 0 && i < devices.size()) choose(devices.get(i));
            }
        });
        setContentView(list);
    }

    @Override protected void onStart() { super.onStart(); load(); }

    private void load() {
        String[] need = Ui.missing(this, Ui.bluetoothPermission());
        if (need.length > 0) { hint.setText("Allow Nearby devices to see paired earbuds"); requestPermissions(need, 2); return; }
        devices.clear();
        BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
        if (a == null || !a.isEnabled()) { hint.setText("Bluetooth is off"); adapter.notifyDataSetChanged(); return; }
        Set<BluetoothDevice> bonded = a.getBondedDevices();
        if (bonded != null) devices.addAll(bonded);
        Collections.sort(devices, new Comparator<BluetoothDevice>() {
            public int compare(BluetoothDevice x, BluetoothDevice y) {
                int rx = rank(nameOf(x)), ry = rank(nameOf(y));
                return rx != ry ? rx - ry : nameOf(x).compareToIgnoreCase(nameOf(y));
            }
        });
        hint.setText(devices.isEmpty() ? "Pair your earbuds in Bluetooth settings first." : "Select your Soundcore earbuds");
        adapter.notifyDataSetChanged();
    }

    private static int rank(String name) {
        return Soundcore.suggestProfile(name) != null ? 0 : Soundcore.looksLikeSoundcore(name) ? 1 : 2;
    }

    private static String nameOf(BluetoothDevice d) {
        String n = d.getName();
        return n == null || n.length() == 0 ? d.getAddress() : n;
    }

    private void choose(final BluetoothDevice d) {
        final String name = nameOf(d);
        Soundcore.Profile suggested = Soundcore.suggestProfile(name);
        if (suggested != null) { pick(d, suggested); return; }
        // Names can be renamed by the user, so a model is only assigned after explicit confirmation.
        final Soundcore.Profile[] profiles = Soundcore.PROFILES;
        String[] items = new String[profiles.length];
        for (int i = 0; i < items.length; i++) items[i] = profiles[i].title;
        new AlertDialog.Builder(this)
            .setTitle("Which model is " + name + "?")
            .setItems(items, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) { pick(d, profiles[which]); } })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void pick(BluetoothDevice d, Soundcore.Profile profile) {
        controller.select(d.getAddress(), nameOf(d), profile);
        finish();
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) load();
    }
}
