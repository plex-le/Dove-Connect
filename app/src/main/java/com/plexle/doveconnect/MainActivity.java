package com.plexle.doveconnect;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.StatusBarManager;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

@SuppressLint("MissingPermission")
public class MainActivity extends Activity {

    private static final int C_BG = 0xFF111318;
    private static final int C_CARD = 0xFF1E2128;
    private static final int C_STROKE = 0xFF2C313B;
    private static final int C_TEXT = 0xFFF1F5F9;
    private static final int C_TEXT2 = 0xFF94A3B8;
    private static final int C_PRIMARY = 0xFF2F80ED;
    private static final int C_GREEN = 0xFF4CAF50;

    private static final String TASKER_PKG = "net.dinglisch.android.taskerm";

    private DoveBluetoothController controller;
    private SharedPreferences prefs;
    private boolean showMac = false;
    private int currentTab = 0;

    private FrameLayout content;
    private LinearLayout tabConnect;
    private LinearLayout tabAuto;
    private TextView tabConnectLabel;
    private TextView tabAutoLabel;
    private View tabConnectPill;
    private View tabAutoPill;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestBtPermissionsIfNeeded();
        controller = DoveBluetoothController.getInstance(this);
        prefs = getSharedPreferences("dove_ui", MODE_PRIVATE);
        showMac = prefs.getBoolean("show_mac", false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_BG);
        root.setFitsSystemWindows(true);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(buildBottomBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        selectTab(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (controller != null) {
            controller.syncMostRecentConnectedDevice(this::renderCurrentTab);
        }
        renderCurrentTab();
    }

    // ───────────────────────── 底栏 ─────────────────────────

    private View buildBottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(0xFF181B21);
        bar.setPadding(dp(8), dp(8), dp(8), dp(10));

        tabConnect = new LinearLayout(this);
        tabAuto = new LinearLayout(this);
        for (LinearLayout t : new LinearLayout[]{tabConnect, tabAuto}) {
            t.setOrientation(LinearLayout.VERTICAL);
            t.setGravity(Gravity.CENTER_HORIZONTAL);
            bar.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }

        tabConnectPill = new View(this);
        tabConnectLabel = makeTabLabel("连接");
        buildTab(tabConnect, tabConnectPill, "🎧", tabConnectLabel);
        tabConnect.setOnClickListener(v -> selectTab(0));

        tabAutoPill = new View(this);
        tabAutoLabel = makeTabLabel("自动化");
        buildTab(tabAuto, tabAutoPill, "⚡", tabAutoLabel);
        tabAuto.setOnClickListener(v -> selectTab(1));
        return bar;
    }

    private TextView makeTabLabel(String s) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    private void buildTab(LinearLayout tab, View pill, String emoji, TextView label) {
        FrameLayout pillBox = new FrameLayout(this);
        pill.setBackground(rounded(0x00000000, 16, 0, 0));
        pillBox.addView(pill, new FrameLayout.LayoutParams(dp(64), dp(32), Gravity.CENTER));
        TextView icon = new TextView(this);
        icon.setText(emoji);
        icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        icon.setGravity(Gravity.CENTER);
        pillBox.addView(icon, new FrameLayout.LayoutParams(dp(64), dp(32), Gravity.CENTER));
        tab.addView(pillBox, new LinearLayout.LayoutParams(dp(64), dp(32)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(3);
        tab.addView(label, lp);
    }

    private void selectTab(int tab) {
        currentTab = tab;
        boolean a = tab == 0;
        tabConnectPill.setBackground(rounded(a ? 0x332F80ED : 0x00000000, 16, 0, 0));
        tabAutoPill.setBackground(rounded(!a ? 0x332F80ED : 0x00000000, 16, 0, 0));
        tabConnectLabel.setTextColor(a ? C_TEXT : C_TEXT2);
        tabAutoLabel.setTextColor(!a ? C_TEXT : C_TEXT2);
        tabConnectLabel.setTypeface(null, a ? Typeface.BOLD : Typeface.NORMAL);
        tabAutoLabel.setTypeface(null, !a ? Typeface.BOLD : Typeface.NORMAL);
        renderCurrentTab();
    }

    private void renderCurrentTab() {
        if (content == null) return;
        content.removeAllViews();
        content.addView(currentTab == 0 ? buildConnectPage() : buildAutomationPage());
    }

    // ───────────────────────── Tab 1：连接 ─────────────────────────

    private View buildConnectPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = pageRoot();
        scroll.addView(root);

        root.addView(pageTitle("Dove Connect", "跟随最近连接的耳机"));

        // Hero 卡片
        LinearLayout hero = card();
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        String name = controller.getTargetName();
        String mac = controller.getTargetMac();
        boolean connected = controller.isAudioTrulyConnected();

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView tag = text("当前设备", 12, C_TEXT2, true);
        head.addView(tag, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView badge = text(connected ? "已连接" : "未连接", 12, connected ? C_GREEN : C_TEXT2, true);
        badge.setPadding(dp(10), dp(4), dp(10), dp(4));
        badge.setBackground(rounded(connected ? 0x264CAF50 : 0x229E9E9E, 8, 1, connected ? C_GREEN : 0xFF64748B));
        head.addView(badge);
        hero.addView(head);

        TextView nameTv = text(name, 24, C_TEXT, true);
        nameTv.setPadding(0, dp(10), 0, 0);
        hero.addView(nameTv);

        if (showMac && !mac.isEmpty()) {
            TextView macTv = text(mac, 12, C_TEXT2, false);
            macTv.setPadding(0, dp(2), 0, 0);
            hero.addView(macTv);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(18), 0, 0);

        final Button connectBtn = filledButton(connected ? "重新连接" : "连接", C_PRIMARY);
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, dp(46), 1f);
        l1.rightMargin = dp(8);
        actions.addView(connectBtn, l1);
        connectBtn.setOnClickListener(v -> {
            connectBtn.setEnabled(false);
            controller.connect((ok, msg) -> {
                connectBtn.setEnabled(true);
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                renderCurrentTab();
            });
        });

        final Button discBtn = filledButton("断开", 0xFF3B4252);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, dp(46), 1f);
        l2.rightMargin = dp(8);
        actions.addView(discBtn, l2);
        discBtn.setOnClickListener(v -> {
            discBtn.setEnabled(false);
            controller.disconnect((ok, msg) -> {
                discBtn.setEnabled(true);
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                renderCurrentTab();
            });
        });

        Button setBtn = filledButton("设置", 0xFF2A2F3A);
        actions.addView(setBtn, new LinearLayout.LayoutParams(dp(76), dp(46)));
        setBtn.setOnClickListener(v -> TileLongPressActivity.openDeviceBluetoothSettings(this, controller.getTargetMac()));

        hero.addView(actions);
        root.addView(hero);

        // 设备列表标题 + MAC 开关
        LinearLayout listHead = new LinearLayout(this);
        listHead.setOrientation(LinearLayout.HORIZONTAL);
        listHead.setGravity(Gravity.CENTER_VERTICAL);
        listHead.setPadding(0, dp(24), 0, dp(10));
        listHead.addView(text("已配对设备", 17, C_TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView macToggle = text(showMac ? "隐藏 MAC" : "显示 MAC", 12, C_PRIMARY, true);
        macToggle.setPadding(dp(10), dp(6), dp(10), dp(6));
        macToggle.setOnClickListener(v -> {
            showMac = !showMac;
            prefs.edit().putBoolean("show_mac", showMac).apply();
            renderCurrentTab();
        });
        listHead.addView(macToggle);
        root.addView(listHead);

        List<BluetoothDevice> devices = controller.getPairedDevices();
        for (final BluetoothDevice d : devices) {
            root.addView(deviceRow(d, d.getAddress().equalsIgnoreCase(mac)));
        }
        return scroll;
    }

    private View deviceRow(final BluetoothDevice d, boolean isCurrent) {
        final String name = d.getName() != null ? d.getName() : "未知设备";
        final String mac = d.getAddress();
        boolean isConnected = controller.isDeviceAudioConnected(d);

        LinearLayout row = card();
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        row.setLayoutParams(lp);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        String prefix = controller.isLikelyAudioDevice(d) ? "🎧  " : "🔹  ";
        top.addView(text(prefix + name, 16, C_TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (isCurrent) {
            TextView cur = text("当前", 11, 0xFF64B5F6, true);
            cur.setPadding(dp(8), dp(2), dp(8), dp(2));
            cur.setBackground(rounded(0x222F80ED, 8, 1, C_PRIMARY));
            top.addView(cur);
        }
        row.addView(top);

        String sub = (isConnected ? "已连接" : "未连接") + (showMac ? "  ·  " + mac : "");
        TextView subTv = text(sub, 12, isConnected ? 0xFF81C784 : C_TEXT2, false);
        subTv.setPadding(0, dp(4), 0, dp(10));
        row.addView(subTv);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);

        final Button c = filledButton("连接", 0xFF1F5FBF);
        c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0, dp(38), 1f);
        b1.rightMargin = dp(8);
        btns.addView(c, b1);
        c.setOnClickListener(v -> {
            c.setEnabled(false);
            controller.setTargetDevice(mac, name);
            controller.connect(mac, (ok, msg) -> {
                c.setEnabled(true);
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                renderCurrentTab();
            });
        });

        final Button x = filledButton("断开", 0xFF3B4252);
        x.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(0, dp(38), 1f);
        b2.rightMargin = dp(8);
        btns.addView(x, b2);
        x.setOnClickListener(v -> {
            x.setEnabled(false);
            controller.disconnect(mac, (ok, msg) -> {
                x.setEnabled(true);
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                renderCurrentTab();
            });
        });

        Button s = filledButton("设置", 0xFF2A2F3A);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btns.addView(s, new LinearLayout.LayoutParams(dp(64), dp(38)));
        s.setOnClickListener(v -> TileLongPressActivity.openDeviceBluetoothSettings(this, mac));

        row.addView(btns);
        return row;
    }

    // ───────────────────────── Tab 2：自动化 ─────────────────────────

    private View buildAutomationPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = pageRoot();
        scroll.addView(root);

        root.addView(pageTitle("自动化", "把连接能力放进 Tasker 与快捷设置"));

        // 快捷设置磁贴
        LinearLayout tileCard = card();
        tileCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        tileCard.addView(text("快捷设置磁贴", 17, C_TEXT, true));
        TextView tileDesc = text("下拉通知栏一键连接 / 断开，自动跟随最近连接的耳机。长按磁贴可直达该耳机的蓝牙设置。",
                13, C_TEXT2, false);
        tileDesc.setPadding(0, dp(6), 0, dp(14));
        tileCard.addView(tileDesc);
        Button addTile = filledButton("添加到快捷设置", C_PRIMARY);
        tileCard.addView(addTile, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        addTile.setOnClickListener(v -> requestAddQsTile());
        root.addView(tileCard);

        // Tasker
        LinearLayout taskerCard = card();
        taskerCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(14);
        taskerCard.setLayoutParams(tlp);
        taskerCard.addView(text("Tasker 任务", 17, C_TEXT, true));
        TextView taskerDesc = text(taskerInstalled()
                        ? "一键导入「连接 / 断开 / 切换」三个任务到 Tasker，使用「跟随最近连接设备」。"
                        : "未检测到 Tasker，请先安装 Tasker 后再导入。",
                13, C_TEXT2, false);
        taskerDesc.setPadding(0, dp(6), 0, dp(14));
        taskerCard.addView(taskerDesc);

        Button all = filledButton("一键添加全部 (3 个任务)", C_PRIMARY);
        taskerCard.addView(all, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        all.setOnClickListener(v -> {
            if (!ensureTasker()) return;
            TaskerXmlProvider.importProjectToTasker(this);
        });

        addTaskerRow(taskerCard, "连接", "Dove_Connect", 70, "connect", "连接 (Connect) -> 默认设备");
        addTaskerRow(taskerCard, "断开", "Dove_Disconnect", 71, "disconnect", "断开连接 (Disconnect) -> 默认设备");
        addTaskerRow(taskerCard, "切换", "Dove_Toggle", 72, "toggle", "切换连接状态 (Toggle) -> 默认设备");
        root.addView(taskerCard);
        return scroll;
    }

    private void addTaskerRow(LinearLayout parent, String label, final String taskName, final int id,
                              final String mode, final String blurb) {
        Button b = filledButton("仅添加「" + label + "」", 0xFF2A2F3A);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        lp.topMargin = dp(8);
        parent.addView(b, lp);
        b.setOnClickListener(v -> {
            if (!ensureTasker()) return;
            TaskerXmlProvider.importTaskToTasker(this, taskName, id, mode, blurb, "", "默认设备");
        });
    }

    private boolean taskerInstalled() {
        try {
            getPackageManager().getPackageInfo(TASKER_PKG, 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean ensureTasker() {
        if (taskerInstalled()) return true;
        Toast.makeText(this, "未检测到 Tasker", Toast.LENGTH_SHORT).show();
        return false;
    }

    private void requestAddQsTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            StatusBarManager sbm = getSystemService(StatusBarManager.class);
            if (sbm != null) {
                sbm.requestAddTileService(
                        new ComponentName(this, DoveTileService.class),
                        controller.getTargetName(),
                        Icon.createWithResource(this, R.drawable.ic_qs_dove),
                        getMainExecutor(),
                        result -> Toast.makeText(this, result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
                                ? "磁贴已在快捷设置中" : "已提交添加请求", Toast.LENGTH_SHORT).show()
                );
            }
        } else {
            Toast.makeText(this, "请在快捷设置编辑页手动添加「Dove Connect」", Toast.LENGTH_LONG).show();
        }
    }

    // ───────────────────────── 通用 UI ─────────────────────────

    private LinearLayout pageRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        return root;
    }

    private View pageTitle(String title, String sub) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, 0, 0, dp(20));
        box.addView(text(title, 28, C_TEXT, true));
        TextView s = text(sub, 13, C_TEXT2, false);
        s.setPadding(0, dp(4), 0, 0);
        box.addView(s);
        return box;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(rounded(C_CARD, 16, 1, C_STROKE));
        return l;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return tv;
    }

    private Button filledButton(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(12));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape, null));
        b.setStateListAnimator(null);
        return b;
    }

    private GradientDrawable rounded(int color, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) d.setStroke(dp(strokeDp), strokeColor);
        return d;
    }

    private void requestBtPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 31
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
            }, 100);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            renderCurrentTab();
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
