# Dove Connect

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" height="96" alt="Dove Connect Logo" />
</p>

<p align="center">
  <strong>One-tap true audio connection, seamless device switching, Quick Settings Tile, and Tasker integration for Android.</strong>
</p>

<p align="center">
  <a href="README_ZH.md">简体中文</a> | <strong>English</strong>
</p>

---

## Overview

**Dove Connect** is a lightweight, zero-dependency Android utility designed for Pixel and other modern Android devices (Android 10 - Android 17 / SDK 29 - 36). It solves common Bluetooth audio routing frustrations—such as earbuds reconnecting only to low-power BLE or failing to take over active media routing—by directly driving Android's Bluetooth A2DP & Headset (HFP) profile state machines and preemptively taking audio focus.

## Key Features

- **True Audio Force Connect**: Bypasses sluggish system reconnect dialogs and directly invokes A2DP and HFP connection routines, auto-polling until media output is actively routed.
- **Clean 3-Way Disconnect**: Performs thorough disconnection across A2DP, HFP, and transport layers, preventing earbuds from hanging in ghost states.
- **Auto-Following Target**: Automatically tracks your most recently connected Bluetooth headset or audio sink—no manual profile switching required.
- **Quick Settings Tile**: Pull down the notification shade to toggle connection state with a single tap. Long-press to jump straight to the device's system Bluetooth settings.
- **Tasker Automation Integration**:
  - Acts as a standard Locale/Tasker action plugin (supports Connect, Disconnect, and Toggle actions).
  - Built-in **One-Click Tasker Import**: Instantly export and share ready-to-use Tasker Projects and Tasks via ContentProvider directly into Tasker.
- **Privacy & Clean UI**:
  - Dark-mode Material layout with intuitive dual-tab navigation (**Connect** and **Automation**).
  - MAC addresses hidden by default with a quick toggle for privacy.
  - Zero third-party SDK tracking or unnecessary background battery drain.

## Installation & Requirements

- **OS**: Android 10 (API 29) to Android 17 (API 36).
- **Permissions**:
  - `BLUETOOTH_CONNECT` & `BLUETOOTH_SCAN` (Android 12+)
  - `BLUETOOTH` & `BLUETOOTH_ADMIN` (Legacy)
  - Hidden API exemptions automatically managed at runtime via `HiddenApiHelper`.

### Build from Source

You can build and deploy the APK directly with the bundled build script (requires Android SDK command-line tools: `aapt2`, `d8`, `zipalign`, and `apksigner`):

```bash
chmod +x build_and_install.sh
./build_and_install.sh
```

## How It Works

1. **Profile Proxy Binding**: Binds to `BluetoothA2dp` and `BluetoothHeadset` service proxies.
2. **Preemptive Audio Routing**: Once A2DP/HFP profiles reach `STATE_CONNECTED`, the app immediately calls `setActiveDevice` on the proxies to ensure media and call audio route to the designated device without delay.
3. **Tasker Plugin Protocol**: Implements the open `com.twofortyfouram.locale` plugin protocol, responding to Tasker fire events asynchronously via `goAsync()`.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
