package com.plexle.doveconnect;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

public class TaskerFireReceiver extends BroadcastReceiver {
    private static final String TAG = "DoveConnect";
    public static final String ACTION_FIRE_SETTING = "com.twofortyfouram.locale.intent.action.FIRE_SETTING";

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        final Context appCtx = context.getApplicationContext();
        DoveBluetoothController controller = DoveBluetoothController.getInstance(appCtx);

        // 处理系统蓝牙连接状态广播：使用 goAsync 确保后台延迟检查完成后再释放接收器生命周期
        if (action.startsWith("android.bluetooth.")) {
            final PendingResult btResult = goAsync();
            controller.onBluetoothBroadcast(intent, () -> {
                try {
                    btResult.finish();
                } catch (Throwable ignored) {}
            });
            return;
        }

        if (!ACTION_FIRE_SETTING.equals(action)) {
            return;
        }

        Log.i(TAG, "TaskerFireReceiver executing Tasker plugin setting");
        String mode = TaskerEditActivity.MODE_CONNECT;
        String targetMacOrName = null;

        Bundle bundle = intent.getBundleExtra(TaskerEditActivity.EXTRA_BUNDLE);
        if (bundle != null) {
            mode = bundle.getString(TaskerEditActivity.BUNDLE_KEY_MODE, TaskerEditActivity.MODE_CONNECT);
            String bundledMac = bundle.getString(TaskerEditActivity.BUNDLE_KEY_MAC, "");
            if (bundledMac != null && !bundledMac.isEmpty()) {
                targetMacOrName = bundledMac;
            }
        }

        final PendingResult pendingResult = goAsync();
        DoveBluetoothController.ConnectCallback cb = (isConnected, message) -> {
            Log.i(TAG, "Tasker execution complete: " + message);
            Toast.makeText(appCtx, message, Toast.LENGTH_SHORT).show();
            try {
                pendingResult.setResultCode(android.app.Activity.RESULT_OK);
                pendingResult.finish();
            } catch (Throwable ignored) {}
        };

        if (TaskerEditActivity.MODE_DISCONNECT.equals(mode)) {
            controller.disconnect(targetMacOrName, cb);
        } else if (TaskerEditActivity.MODE_TOGGLE.equals(mode)) {
            controller.toggle(targetMacOrName, cb);
        } else {
            controller.connect(targetMacOrName, cb);
        }
    }
}
