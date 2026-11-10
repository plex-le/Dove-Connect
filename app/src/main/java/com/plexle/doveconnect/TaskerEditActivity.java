package com.plexle.doveconnect;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

@SuppressLint("MissingPermission")
public class TaskerEditActivity extends Activity {

    public static final String EXTRA_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE";
    public static final String EXTRA_BLURB = "com.twofortyfouram.locale.intent.extra.BLURB";
    public static final String BUNDLE_KEY_MODE = "action_mode";
    public static final String BUNDLE_KEY_MAC = "device_mac";
    public static final String BUNDLE_KEY_NAME = "device_name";

    public static final String MODE_CONNECT = "connect";
    public static final String MODE_DISCONNECT = "disconnect";
    public static final String MODE_TOGGLE = "toggle";

    private RadioGroup deviceGroup;
    private String selectedMac = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DoveBluetoothController controller = DoveBluetoothController.getInstance(this);

        Bundle existing = getIntent() != null ? getIntent().getBundleExtra(EXTRA_BUNDLE) : null;
        if (existing != null) {
            selectedMac = existing.getString(BUNDLE_KEY_MAC, "");
        }

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);
        root.setMinimumWidth(dp(320));
        GradientDrawable dialogBg = new GradientDrawable();
        dialogBg.setColor(0xFF1A1C22);
        dialogBg.setCornerRadius(dp(20));
        root.setBackground(dialogBg);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Dove Connect · Tasker 插件");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setTextColor(0xFFF1F5F9);
        title.setPadding(0, 0, 0, dp(14));
        root.addView(title);

        TextView step1 = new TextView(this);
        step1.setText("1. 选择目标蓝牙设备");
        step1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        step1.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        step1.setTextColor(0xFF94A3B8);
        step1.setPadding(0, 0, 0, dp(8));
        root.addView(step1);

        deviceGroup = new RadioGroup(this);
        deviceGroup.setOrientation(RadioGroup.VERTICAL);

        String targetName = controller.getTargetName();
        RadioButton followDefaultBtn = new RadioButton(this);
        followDefaultBtn.setText("跟随最近连接设备 (" + targetName + ")");
        followDefaultBtn.setTextColor(0xFFE2E8F0);
        followDefaultBtn.setTag(new String[]{"", "默认设备(" + targetName + ")"});
        deviceGroup.addView(followDefaultBtn);
        if (selectedMac == null || selectedMac.isEmpty()) {
            followDefaultBtn.setChecked(true);
        }

        List<BluetoothDevice> paired = controller.getPairedDevices();
        for (BluetoothDevice d : paired) {
            String name = d.getName() != null ? d.getName() : "未知设备";
            String mac = d.getAddress();
            boolean isAudio = controller.isLikelyAudioDevice(d);
            RadioButton rb = new RadioButton(this);
            rb.setText((isAudio ? "🎧 " : "🔹 ") + name);
            rb.setTextColor(0xFFE2E8F0);
            rb.setTag(new String[]{mac, name});
            deviceGroup.addView(rb);
            if (mac.equalsIgnoreCase(selectedMac)) {
                rb.setChecked(true);
            }
        }

        root.addView(deviceGroup);

        TextView step2 = new TextView(this);
        step2.setText("2. 点击选择动作并保存");
        step2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        step2.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        step2.setTextColor(0xFF94A3B8);
        step2.setPadding(0, dp(16), 0, dp(10));
        root.addView(step2);

        addOptionButton(root, "连接 (Connect)", MODE_CONNECT, 0xFF1976D2);
        addOptionButton(root, "断开连接 (Disconnect)", MODE_DISCONNECT, 0xFF334155);
        addOptionButton(root, "切换连接状态 (Toggle)", MODE_TOGGLE, 0xFF0F766E);

        setContentView(scroll);
    }

    private void addOptionButton(LinearLayout parent, final String actionLabel, final String mode, int bgColor) {
        Button btn = new Button(this);
        btn.setText(actionLabel);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);

        GradientDrawable shape = new GradientDrawable();
        shape.setColor(bgColor);
        shape.setCornerRadius(dp(12));
        btn.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape, null));
        btn.setStateListAnimator(null);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        );
        lp.bottomMargin = dp(8);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> finishWithMode(mode, actionLabel));
        parent.addView(btn);
    }

    private void finishWithMode(String mode, String actionLabel) {
        String mac = "";
        String name = "默认设备";
        int checkedId = deviceGroup.getCheckedRadioButtonId();
        if (checkedId != -1) {
            RadioButton rb = deviceGroup.findViewById(checkedId);
            if (rb != null && rb.getTag() instanceof String[]) {
                String[] tag = (String[]) rb.getTag();
                mac = tag[0];
                name = tag[1];
            }
        }

        Intent resultIntent = new Intent();
        Bundle bundle = new Bundle();
        bundle.putString(BUNDLE_KEY_MODE, mode);
        bundle.putString(BUNDLE_KEY_MAC, mac);
        bundle.putString(BUNDLE_KEY_NAME, name);
        resultIntent.putExtra(EXTRA_BUNDLE, bundle);

        String blurb = actionLabel + " -> " + name;
        resultIntent.putExtra(EXTRA_BLURB, blurb);
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
