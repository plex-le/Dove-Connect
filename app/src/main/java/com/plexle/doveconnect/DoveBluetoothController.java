package com.plexle.doveconnect;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.service.quicksettings.TileService;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@SuppressLint("MissingPermission")
public class DoveBluetoothController {
    public static final String TAG = "DoveConnect";
    public static final String ACTION_A2DP_ACTIVE_CHANGED = "android.bluetooth.a2dp.profile.action.ACTIVE_DEVICE_CHANGED";
    public static final String ACTION_HFP_ACTIVE_CHANGED = "android.bluetooth.headset.profile.action.ACTIVE_DEVICE_CHANGED";

    private static final String PREFS_NAME = "dove_prefs";
    private static final String KEY_MAC = "target_mac";
    private static final String KEY_NAME = "target_name";
    private static final String KEY_LAST_SYS_RECENT_MAC = "last_sys_recent_mac";

    private static DoveBluetoothController instance;

    private final Context appContext;
    private final BluetoothAdapter adapter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private BluetoothA2dp a2dpProxy;
    private BluetoothHeadset headsetProxy;
    private final List<Runnable> pendingActions = new ArrayList<>();
    private Runnable tileUpdateListener;

    // 广播去重：防止 Manifest 接收器与动态接收器短时间内重复处理同一条广播
    private String lastBroadcastKey = "";
    private long lastBroadcastTimeMs = 0L;

    public interface ConnectCallback {
        void onStatus(boolean isConnected, String message);
    }

    public static synchronized DoveBluetoothController getInstance(Context context) {
        if (instance == null) {
            instance = new DoveBluetoothController(context.getApplicationContext());
        }
        return instance;
    }

    private DoveBluetoothController(Context context) {
        HiddenApiHelper.exemptAll();
        this.appContext = context;
        BluetoothManager bm = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        this.adapter = bm != null ? bm.getAdapter() : BluetoothAdapter.getDefaultAdapter();
        bindProfiles();
        registerActiveDeviceHook();
    }

    public void setTileUpdateListener(Runnable listener) {
        this.tileUpdateListener = listener;
    }

    private void notifyTileChanged() {
        if (tileUpdateListener != null) {
            mainHandler.post(tileUpdateListener);
        }
        try {
            TileService.requestListeningState(appContext, new ComponentName(appContext, DoveTileService.class));
        } catch (Throwable ignored) {}
    }

    private void registerActiveDeviceHook() {
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
            filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
            filter.addAction(ACTION_A2DP_ACTIVE_CHANGED);
            filter.addAction(ACTION_HFP_ACTIVE_CHANGED);
            filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
            filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    onBluetoothBroadcast(intent, null);
                }
            };
            if (Build.VERSION.SDK_INT >= 33) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                appContext.registerReceiver(receiver, filter);
            }
        } catch (Throwable t) {
            Log.w(TAG, "registerActiveDeviceHook error: " + t.getMessage());
        }
    }

    @SuppressWarnings("deprecation")
    private static BluetoothDevice extractDevice(Intent intent) {
        if (intent == null) return null;
        if (Build.VERSION.SDK_INT >= 33) {
            return intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
        } else {
            return intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        }
    }

    public void onBluetoothBroadcast(Intent intent) {
        onBluetoothBroadcast(intent, null);
    }

    public void onBluetoothBroadcast(Intent intent, final Runnable onComplete) {
        if (intent == null || intent.getAction() == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        String action = intent.getAction();
        BluetoothDevice dev = extractDevice(intent);
        int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);

        String dedupKey = action + "|" + (dev != null ? dev.getAddress() : "null") + "|" + state;
        long now = SystemClock.elapsedRealtime();
        synchronized (this) {
            if (dedupKey.equals(lastBroadcastKey) && (now - lastBroadcastTimeMs) < 150L) {
                if (onComplete != null) onComplete.run();
                return;
            }
            lastBroadcastKey = dedupKey;
            lastBroadcastTimeMs = now;
        }

        if (BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(action)
                || BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
            if (state == BluetoothProfile.STATE_CONNECTED && dev != null) {
                Log.i(TAG, "Audio device connected (" + action + "): " + dev.getName() + " [" + dev.getAddress() + "]");
                updateRecentConnectedDevice(dev);
                activateAudioOnDevice(dev);
                notifyTileChanged();
                if (onComplete != null) onComplete.run();
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                syncMostRecentConnectedDevice(() -> {
                    notifyTileChanged();
                    if (onComplete != null) onComplete.run();
                });
            } else {
                if (onComplete != null) onComplete.run();
            }
        } else if (ACTION_A2DP_ACTIVE_CHANGED.equals(action) || ACTION_HFP_ACTIVE_CHANGED.equals(action)) {
            if (dev != null) {
                Log.i(TAG, "Active audio device changed (" + action + "): " + dev.getName() + " [" + dev.getAddress() + "]");
                updateRecentConnectedDevice(dev);
            }
            notifyTileChanged();
            if (onComplete != null) onComplete.run();
        } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
            if (dev != null && isLikelyAudioDevice(dev)) {
                Log.i(TAG, "Audio device ACL connected: " + dev.getName() + " [" + dev.getAddress() + "]");
                updateRecentConnectedDevice(dev);
                notifyTileChanged();
            }
            if (onComplete != null) onComplete.run();
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            mainHandler.postDelayed(() -> syncMostRecentConnectedDevice(() -> {
                notifyTileChanged();
                if (onComplete != null) onComplete.run();
            }), 300);
        } else {
            if (onComplete != null) onComplete.run();
        }
    }

    public void updateRecentConnectedDevice(BluetoothDevice dev) {
        if (dev == null || dev.getAddress() == null) return;
        String mac = dev.getAddress().trim().toUpperCase();
        String name = (dev.getName() != null && !dev.getName().isEmpty()) ? dev.getName() : mac;
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_MAC, mac)
                .putString(KEY_NAME, name)
                .putString(KEY_LAST_SYS_RECENT_MAC, mac)
                .putLong("conn_ts_" + mac, System.currentTimeMillis())
                .apply();
    }

    public long getDeviceConnectTimestamp(String mac) {
        if (mac == null || mac.isEmpty()) return 0L;
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong("conn_ts_" + mac.trim().toUpperCase(), 0L);
    }

    public void syncMostRecentConnectedDevice(final Runnable onComplete) {
        runWhenProfilesReady(() -> {
            try {
                // ① 优先检查当前真正建立了 A2DP / HFP 音频连接的设备
                BluetoothDevice activeOrConnected = findCurrentlyConnectedAudioDevice();
                if (activeOrConnected != null) {
                    updateRecentConnectedDevice(activeOrConnected);
                    if (onComplete != null) onComplete.run();
                    return;
                }

                // ①.5 检查系统界面显示已连接 (ACL 在线，包括假连接/LE 连接) 的音频设备，按最近连接时间戳选最新的一台
                BluetoothDevice aclAudioConnected = findMostRecentAclConnectedAudioDevice();
                if (aclAudioConnected != null) {
                    SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    String currentMac = prefs.getString(KEY_MAC, "");
                    if (!aclAudioConnected.getAddress().equalsIgnoreCase(currentMac)) {
                        Log.i(TAG, "Switched target to ACL-connected audio device: " + aclAudioConnected.getName());
                        updateRecentConnectedDevice(aclAudioConnected);
                    }
                    if (onComplete != null) onComplete.run();
                    return;
                }

                // ② 兜底查询系统底层的 getMostRecentlyConnectedDevices()
                if (adapter != null) {
                    try {
                        Method m = BluetoothAdapter.class.getDeclaredMethod("getMostRecentlyConnectedDevices");
                        m.setAccessible(true);
                        Object res = m.invoke(adapter);
                        if (res instanceof List) {
                            List<?> list = (List<?>) res;
                            for (Object item : list) {
                                if (item instanceof BluetoothDevice) {
                                    BluetoothDevice d = (BluetoothDevice) item;
                                    if (isLikelyAudioDevice(d)) {
                                        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                                        String lastSysMac = prefs.getString(KEY_LAST_SYS_RECENT_MAC, "");
                                        if (!d.getAddress().equalsIgnoreCase(lastSysMac)) {
                                            Log.i(TAG, "System most recently connected audio device updated to: " + d.getName());
                                            updateRecentConnectedDevice(d);
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Throwable t) {
                        Log.d(TAG, "getMostRecentlyConnectedDevices fallback unavailable: " + t.getMessage());
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "syncMostRecentConnectedDevice error: " + t.getMessage());
            }
            if (onComplete != null) onComplete.run();
        });
    }

    public BluetoothDevice findMostRecentAclConnectedAudioDevice() {
        if (adapter == null || !adapter.isEnabled()) return null;
        try {
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            if (bonded == null || bonded.isEmpty()) return null;

            SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String savedMac = prefs.getString(KEY_MAC, "");

            BluetoothDevice best = null;
            long bestTs = -1L;

            for (BluetoothDevice d : bonded) {
                if (!isLikelyAudioDevice(d) || !isDeviceAclConnected(d)) {
                    continue;
                }
                long ts = getDeviceConnectTimestamp(d.getAddress());
                if (best == null || ts > bestTs
                        || (ts == bestTs && d.getAddress().equalsIgnoreCase(savedMac))) {
                    best = d;
                    bestTs = ts;
                }
            }
            return best;
        } catch (Throwable t) {
            Log.w(TAG, "findMostRecentAclConnectedAudioDevice error: " + t.getMessage());
            return null;
        }
    }

    public boolean isDeviceAclConnected(BluetoothDevice device) {
        if (device == null) return false;
        try {
            Method isConn = BluetoothDevice.class.getDeclaredMethod("isConnected");
            isConn.setAccessible(true);
            return (boolean) isConn.invoke(device);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public BluetoothDevice findCurrentlyConnectedAudioDevice() {
        try {
            if (a2dpProxy != null) {
                try {
                    Method getActive = BluetoothA2dp.class.getDeclaredMethod("getActiveDevice");
                    getActive.setAccessible(true);
                    BluetoothDevice active = (BluetoothDevice) getActive.invoke(a2dpProxy);
                    if (active != null) return active;
                } catch (Throwable ignored) {}
                List<BluetoothDevice> a2dpList = a2dpProxy.getConnectedDevices();
                if (a2dpList != null && !a2dpList.isEmpty()) {
                    return a2dpList.get(0);
                }
            }
            if (headsetProxy != null) {
                try {
                    Method getActive = BluetoothHeadset.class.getDeclaredMethod("getActiveDevice");
                    getActive.setAccessible(true);
                    BluetoothDevice active = (BluetoothDevice) getActive.invoke(headsetProxy);
                    if (active != null) return active;
                } catch (Throwable ignored) {}
                List<BluetoothDevice> hfpList = headsetProxy.getConnectedDevices();
                if (hfpList != null && !hfpList.isEmpty()) {
                    return hfpList.get(0);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "findCurrentlyConnectedAudioDevice error: " + t.getMessage());
        }
        return null;
    }

    public void bindProfiles() {
        if (adapter == null) return;
        if (a2dpProxy == null) {
            adapter.getProfileProxy(appContext, new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    a2dpProxy = (BluetoothA2dp) proxy;
                    Log.i(TAG, "A2DP proxy connected");
                    drainPendingActions();
                }

                @Override
                public void onServiceDisconnected(int profile) {
                    a2dpProxy = null;
                }
            }, BluetoothProfile.A2DP);
        }
        if (headsetProxy == null) {
            adapter.getProfileProxy(appContext, new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    headsetProxy = (BluetoothHeadset) proxy;
                    Log.i(TAG, "HEADSET proxy connected");
                    drainPendingActions();
                }

                @Override
                public void onServiceDisconnected(int profile) {
                    headsetProxy = null;
                }
            }, BluetoothProfile.HEADSET);
        }
    }

    private void drainPendingActions() {
        if (a2dpProxy == null || headsetProxy == null) return;
        List<Runnable> toRun;
        synchronized (pendingActions) {
            if (pendingActions.isEmpty()) return;
            toRun = new ArrayList<>(pendingActions);
            pendingActions.clear();
        }
        for (Runnable r : toRun) {
            mainHandler.post(r);
        }
    }

    private void runWhenProfilesReady(final Runnable action) {
        if (action == null) return;
        bindProfiles();
        if (a2dpProxy != null && headsetProxy != null) {
            action.run();
            return;
        }
        synchronized (pendingActions) {
            pendingActions.add(action);
        }
        mainHandler.postDelayed(() -> {
            boolean shouldRun;
            synchronized (pendingActions) {
                shouldRun = pendingActions.remove(action);
            }
            if (shouldRun) {
                action.run();
            }
        }, 450);
    }

    public String getTargetMac() {
        BluetoothDevice connectedNow = findCurrentlyConnectedAudioDevice();
        if (connectedNow != null && connectedNow.getAddress() != null) {
            updateRecentConnectedDevice(connectedNow);
            return connectedNow.getAddress().trim().toUpperCase();
        }
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_MAC, "");
        if (saved != null && !saved.isEmpty() && !saved.startsWith("XX:")) {
            return saved;
        }
        BluetoothDevice aclNow = findMostRecentAclConnectedAudioDevice();
        if (aclNow != null && aclNow.getAddress() != null) {
            updateRecentConnectedDevice(aclNow);
            return aclNow.getAddress().trim().toUpperCase();
        }
        List<BluetoothDevice> paired = getPairedDevices();
        if (!paired.isEmpty()) {
            BluetoothDevice first = paired.get(0);
            updateRecentConnectedDevice(first);
            return first.getAddress().trim().toUpperCase();
        }
        return "";
    }

    public String getTargetName() {
        BluetoothDevice d = findTargetDevice(null);
        if (d != null && d.getName() != null && !d.getName().isEmpty()) {
            return d.getName();
        }
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedName = prefs.getString(KEY_NAME, "");
        return (savedName != null && !savedName.isEmpty()) ? savedName : "未配对耳机";
    }

    public void setTargetDevice(String mac, String name) {
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = prefs.edit();
        if (mac != null) {
            String upperMac = mac.trim().toUpperCase();
            ed.putString(KEY_MAC, upperMac);
            ed.putLong("conn_ts_" + upperMac, System.currentTimeMillis());
        }
        if (name != null) ed.putString(KEY_NAME, name.trim());
        ed.apply();
    }

    public List<BluetoothDevice> getPairedDevices() {
        List<BluetoothDevice> result = new ArrayList<>();
        if (adapter == null || !adapter.isEnabled()) return result;
        try {
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            if (bonded != null) {
                for (BluetoothDevice d : bonded) {
                    if (isLikelyAudioDevice(d)) {
                        result.add(d);
                    }
                }
                Collections.sort(result, (a, b) -> {
                    boolean aConn = isDeviceAudioConnected(a);
                    boolean bConn = isDeviceAudioConnected(b);
                    if (aConn != bConn) return aConn ? -1 : 1;
                    boolean aAcl = isDeviceAclConnected(a);
                    boolean bAcl = isDeviceAclConnected(b);
                    if (aAcl != bAcl) return aAcl ? -1 : 1;
                    long aTs = getDeviceConnectTimestamp(a.getAddress());
                    long bTs = getDeviceConnectTimestamp(b.getAddress());
                    if (aTs != bTs) return Long.compare(bTs, aTs);
                    String na = a.getName() != null ? a.getName() : a.getAddress();
                    String nb = b.getName() != null ? b.getName() : b.getAddress();
                    return na.compareToIgnoreCase(nb);
                });
            }
        } catch (Throwable t) {
            Log.e(TAG, "getPairedDevices error", t);
        }
        return result;
    }

    public boolean isLikelyAudioDevice(BluetoothDevice d) {
        if (d == null) return false;
        try {
            BluetoothClass btClass = d.getBluetoothClass();
            if (btClass != null) {
                int major = btClass.getMajorDeviceClass();
                if (major == BluetoothClass.Device.Major.AUDIO_VIDEO) {
                    return true;
                }
                // 明确过滤手机、电脑、键鼠外设、打印机/影像设备、健康/网络设备等非音频播放设备
                if (major == BluetoothClass.Device.Major.PHONE
                        || major == BluetoothClass.Device.Major.COMPUTER
                        || major == BluetoothClass.Device.Major.PERIPHERAL
                        || major == BluetoothClass.Device.Major.IMAGING
                        || major == BluetoothClass.Device.Major.NETWORKING
                        || major == BluetoothClass.Device.Major.HEALTH) {
                    return false;
                }
            }
            // 若 Major Class 未分类，检查是否声明了音频接收端 UUID (A2DP Sink / HFP / HSP / LE Audio)
            ParcelUuid[] uuids = d.getUuids();
            if (uuids != null) {
                for (ParcelUuid u : uuids) {
                    if (u == null || u.getUuid() == null) continue;
                    String s = u.getUuid().toString().toLowerCase();
                    if (s.startsWith("0000110b-")      // AudioSink (A2DP Sink)
                            || s.startsWith("0000111e-") // Handsfree (HFP)
                            || s.startsWith("00001108-") // Headset (HSP)
                            || s.startsWith("0000184e-") // Audio Stream Control (LE Audio)
                            || s.startsWith("00001853-")) { // Common Audio (LE Audio)
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public BluetoothDevice findTargetDevice(String customMacOrName) {
        if (adapter == null || !adapter.isEnabled()) return null;
        String query = (customMacOrName != null && !customMacOrName.trim().isEmpty())
                ? customMacOrName.trim()
                : getTargetMac();
        if (query.isEmpty()) return null;
        try {
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            if (bonded != null) {
                for (BluetoothDevice d : bonded) {
                    if (d.getAddress().equalsIgnoreCase(query)) {
                        return d;
                    }
                }
                for (BluetoothDevice d : bonded) {
                    String name = d.getName();
                    if (name != null && name.equalsIgnoreCase(query)) {
                        return d;
                    }
                }
                for (BluetoothDevice d : bonded) {
                    String name = d.getName();
                    if (name != null && name.toLowerCase().contains(query.toLowerCase())) {
                        return d;
                    }
                }
            }
            if (BluetoothAdapter.checkBluetoothAddress(query.toUpperCase())) {
                return adapter.getRemoteDevice(query.toUpperCase());
            }
        } catch (Throwable t) {
            Log.e(TAG, "findTargetDevice error", t);
        }
        return null;
    }

    public boolean isAudioTrulyConnected() {
        return isDeviceAudioConnected(findTargetDevice(null));
    }

    public boolean isDeviceAudioConnected(BluetoothDevice device) {
        if (device == null) return false;
        try {
            if (a2dpProxy != null) {
                int a2dpState = a2dpProxy.getConnectionState(device);
                if (a2dpState == BluetoothProfile.STATE_CONNECTED) {
                    return true;
                }
            }
            if (headsetProxy != null) {
                int hfpState = headsetProxy.getConnectionState(device);
                if (hfpState == BluetoothProfile.STATE_CONNECTED) {
                    return true;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "isDeviceAudioConnected check error: " + t.getMessage());
        }
        return false;
    }

    public void activateAudioOnDevice(BluetoothDevice device) {
        if (device == null) return;
        if (a2dpProxy != null) {
            try {
                Method a2dpActive = BluetoothA2dp.class.getDeclaredMethod("setActiveDevice", BluetoothDevice.class);
                a2dpActive.setAccessible(true);
                Object r = a2dpActive.invoke(a2dpProxy, device);
                Log.i(TAG, "BluetoothA2dp.setActiveDevice(" + device.getAddress() + ") -> " + r);
            } catch (Throwable t) {
                Log.w(TAG, "BluetoothA2dp.setActiveDevice error: " + t);
            }
        }
        if (headsetProxy != null) {
            try {
                Method hfpActive = BluetoothHeadset.class.getDeclaredMethod("setActiveDevice", BluetoothDevice.class);
                hfpActive.setAccessible(true);
                Object r = hfpActive.invoke(headsetProxy, device);
                Log.i(TAG, "BluetoothHeadset.setActiveDevice(" + device.getAddress() + ") -> " + r);
            } catch (Throwable t) {
                Log.w(TAG, "BluetoothHeadset.setActiveDevice error: " + t);
            }
        }
    }

    public void connect(final ConnectCallback callback) {
        connect(null, callback);
    }

    public void connect(final String customMacOrName, final ConnectCallback callback) {
        runWhenProfilesReady(() -> executeConnect(customMacOrName, callback));
    }

    public void forceConnect(final ConnectCallback callback) {
        connect(null, callback);
    }

    public void forceConnect(final String customMacOrName, final ConnectCallback callback) {
        connect(customMacOrName, callback);
    }

    private void executeConnect(final String customMacOrName, final ConnectCallback callback) {
        final BluetoothDevice device = findTargetDevice(customMacOrName);
        if (device == null) {
            if (callback != null) callback.onStatus(false, "未找到目标蓝牙耳机");
            return;
        }
        final String devName = device.getName() != null ? device.getName() : device.getAddress();

        try {
            boolean alreadyA2dpConnected = false;

            if (a2dpProxy != null) {
                int a2dpState = a2dpProxy.getConnectionState(device);
                alreadyA2dpConnected = (a2dpState == BluetoothProfile.STATE_CONNECTED);
                try {
                    Method a2dpConn = BluetoothA2dp.class.getDeclaredMethod("connect", BluetoothDevice.class);
                    a2dpConn.setAccessible(true);
                    Object r = a2dpConn.invoke(a2dpProxy, device);
                    Log.i(TAG, "BluetoothA2dp.connect(" + device.getAddress() + ") -> " + r);
                } catch (Throwable t) {
                    Log.w(TAG, "BluetoothA2dp.connect() error: " + t.getCause());
                }
            }

            if (headsetProxy != null) {
                try {
                    Method hfpConn = BluetoothHeadset.class.getDeclaredMethod("connect", BluetoothDevice.class);
                    hfpConn.setAccessible(true);
                    Object r = hfpConn.invoke(headsetProxy, device);
                    Log.i(TAG, "BluetoothHeadset.connect(" + device.getAddress() + ") -> " + r);
                } catch (Throwable t) {
                    Log.w(TAG, "BluetoothHeadset.connect() error: " + t.getCause());
                }
            }

            if (alreadyA2dpConnected) {
                updateRecentConnectedDevice(device);
                activateAudioOnDevice(device);
                notifyTileChanged();
                if (callback != null) {
                    callback.onStatus(true, "已连接 " + devName);
                }
                return;
            }

            pollUntilConnected(device, devName, 0, callback);

        } catch (Throwable t) {
            Log.e(TAG, "executeConnect exception", t);
            if (callback != null) callback.onStatus(false, "连接异常: " + t.getMessage());
        }
    }

    private void pollUntilConnected(final BluetoothDevice device, final String devName, final int attempt, final ConnectCallback callback) {
        if (isDeviceAudioConnected(device)) {
            updateRecentConnectedDevice(device);
            activateAudioOnDevice(device);
            notifyTileChanged();
            if (callback != null) {
                callback.onStatus(true, "已连接 " + devName);
            }
            return;
        }
        if (attempt >= 9) {
            boolean connected = isDeviceAudioConnected(device);
            if (connected) {
                updateRecentConnectedDevice(device);
                activateAudioOnDevice(device);
                notifyTileChanged();
            }
            if (callback != null) {
                callback.onStatus(connected, connected ? "已连接 " + devName : "已向 " + devName + " 发送连接指令");
            }
            return;
        }
        mainHandler.postDelayed(() -> pollUntilConnected(device, devName, attempt + 1, callback), 500);
    }

    public void disconnect(final ConnectCallback callback) {
        disconnect(null, callback);
    }

    public void disconnect(final String customMacOrName, final ConnectCallback callback) {
        runWhenProfilesReady(() -> executeDisconnect(customMacOrName, callback));
    }

    private void executeDisconnect(final String customMacOrName, final ConnectCallback callback) {
        final BluetoothDevice device = findTargetDevice(customMacOrName);
        if (device == null) {
            if (callback != null) callback.onStatus(false, "未找到目标蓝牙耳机");
            return;
        }
        final String devName = device.getName() != null ? device.getName() : device.getAddress();

        if (a2dpProxy != null) {
            try {
                Method m = BluetoothA2dp.class.getDeclaredMethod("disconnect", BluetoothDevice.class);
                m.setAccessible(true);
                Object r = m.invoke(a2dpProxy, device);
                Log.i(TAG, "BluetoothA2dp.disconnect(" + device.getAddress() + ") -> " + r);
            } catch (Throwable t) {
                Log.w(TAG, "BluetoothA2dp.disconnect error: " + t.getCause());
            }
        }

        if (headsetProxy != null) {
            try {
                Method m = BluetoothHeadset.class.getDeclaredMethod("disconnect", BluetoothDevice.class);
                m.setAccessible(true);
                Object r = m.invoke(headsetProxy, device);
                Log.i(TAG, "BluetoothHeadset.disconnect(" + device.getAddress() + ") -> " + r);
            } catch (Throwable t) {
                Log.w(TAG, "BluetoothHeadset.disconnect error: " + t.getCause());
            }
        }

        try {
            Method devDisc = BluetoothDevice.class.getDeclaredMethod("disconnect");
            devDisc.setAccessible(true);
            Object r = devDisc.invoke(device);
            Log.i(TAG, "BluetoothDevice.disconnect() -> " + r);
        } catch (Throwable t) {
            Log.w(TAG, "BluetoothDevice.disconnect error: " + t.getCause());
        }

        pollUntilDisconnected(device, devName, 0, callback);
    }

    private void pollUntilDisconnected(final BluetoothDevice device, final String devName, final int attempt, final ConnectCallback callback) {
        if (!isDeviceAudioConnected(device)) {
            notifyTileChanged();
            if (callback != null) {
                callback.onStatus(false, "已断开 " + devName);
            }
            return;
        }
        if (attempt >= 6) {
            boolean connected = isDeviceAudioConnected(device);
            notifyTileChanged();
            if (callback != null) {
                callback.onStatus(connected, connected ? devName + " 仍处于连接状态" : "已断开 " + devName);
            }
            return;
        }
        mainHandler.postDelayed(() -> pollUntilDisconnected(device, devName, attempt + 1, callback), 400);
    }

    public void toggle(final ConnectCallback callback) {
        toggle(null, callback);
    }

    public void toggle(final String customMacOrName, final ConnectCallback callback) {
        runWhenProfilesReady(() -> {
            BluetoothDevice dev = findTargetDevice(customMacOrName);
            if (isDeviceAudioConnected(dev)) {
                executeDisconnect(customMacOrName, callback);
            } else {
                executeConnect(customMacOrName, callback);
            }
        });
    }
}
