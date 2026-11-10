package com.plexle.doveconnect;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

public class TileLongPressActivity extends Activity {
    private static final String TAG = "DoveConnect";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DoveBluetoothController controller = DoveBluetoothController.getInstance(this);
        BluetoothDevice connected = controller.findCurrentlyConnectedAudioDevice();
        if (connected != null) {
            controller.updateRecentConnectedDevice(connected);
        }
        BluetoothDevice target = controller.findTargetDevice(null);
        String mac = target != null ? target.getAddress() : controller.getTargetMac();
        openDeviceBluetoothSettings(this, mac);
        finish();
    }

    public static void openDeviceBluetoothSettings(Context context, String macAddress) {
        if (macAddress != null && !macAddress.isEmpty()) {
            try {
                Intent detailIntent = new Intent("com.android.settings.BLUETOOTH_DEVICE_DETAIL_SETTINGS");
                detailIntent.setPackage("com.android.settings");
                Bundle showFragmentArgs = new Bundle();
                showFragmentArgs.putString("device_address", macAddress);
                detailIntent.putExtra(":settings:show_fragment_args", showFragmentArgs);
                detailIntent.putExtra("device_address", macAddress);
                detailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(detailIntent);
                Log.i(TAG, "Opened BLUETOOTH_DEVICE_DETAIL_SETTINGS for " + macAddress);
                return;
            } catch (Throwable t) {
                Log.w(TAG, "Failed to open BLUETOOTH_DEVICE_DETAIL_SETTINGS: " + t.getMessage());
            }
        }
        try {
            Intent btSettings = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            btSettings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(btSettings);
        } catch (Throwable ignored) {}
    }
}
