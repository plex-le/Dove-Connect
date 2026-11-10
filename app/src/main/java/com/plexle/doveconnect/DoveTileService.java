package com.plexle.doveconnect;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothHeadset;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public class DoveTileService extends TileService {

    private DoveBluetoothController controller;
    private boolean receiverRegistered = false;

    private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshTile(null, null);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        controller = DoveBluetoothController.getInstance(this);
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        controller = DoveBluetoothController.getInstance(this);
        controller.setTileUpdateListener(() -> refreshTile(null, null));
        controller.bindProfiles();
        registerBtReceiver();
        refreshTile(null, null);
        controller.syncMostRecentConnectedDevice(() -> refreshTile(null, null));
    }

    @Override
    public void onStopListening() {
        if (controller != null) {
            controller.setTileUpdateListener(null);
        }
        unregisterBtReceiver();
        super.onStopListening();
    }

    private void registerBtReceiver() {
        if (receiverRegistered) return;
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
            filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
            filter.addAction(DoveBluetoothController.ACTION_A2DP_ACTIVE_CHANGED);
            filter.addAction(DoveBluetoothController.ACTION_HFP_ACTIVE_CHANGED);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(btReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(btReceiver, filter);
            }
            receiverRegistered = true;
        } catch (Throwable ignored) {}
    }

    private void unregisterBtReceiver() {
        if (!receiverRegistered) return;
        try {
            unregisterReceiver(btReceiver);
        } catch (Throwable ignored) {}
        receiverRegistered = false;
    }

    private void refreshTile(String customSubtitle, Integer customState) {
        Tile tile = getQsTile();
        if (tile == null) return;

        boolean connected = controller.isAudioTrulyConnected();
        String targetName = controller.getTargetName();
        tile.setLabel(targetName);
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_qs_dove));

        if (customSubtitle != null) {
            tile.setSubtitle(customSubtitle);
            tile.setState(customState != null ? customState : Tile.STATE_ACTIVE);
        } else if (connected) {
            tile.setState(Tile.STATE_ACTIVE);
            tile.setSubtitle("已连接 · 点击断开");
        } else {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setSubtitle("点击连接");
        }
        tile.updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        controller = DoveBluetoothController.getInstance(this);
        boolean currentlyConnected = controller.isAudioTrulyConnected();

        if (currentlyConnected) {
            refreshTile("正在断开...", Tile.STATE_INACTIVE);
            controller.disconnect((isConnected, message) -> {
                refreshTile(null, null);
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            });
        } else {
            refreshTile("正在连接...", Tile.STATE_ACTIVE);
            controller.connect((isConnected, message) -> {
                refreshTile(null, null);
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            });
        }
    }
}
