# Dove Connect

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" height="96" alt="Dove Connect Logo" />
</p>

<p align="center">
  <strong>专为 Android 打造的一键蓝牙音频真连接、快捷磁贴与 Tasker 自动化插件。</strong>
</p>

<p align="center">
  <strong>简体中文</strong> | <a href="README.md">English</a>
</p>

---

## 项目简介

**Dove Connect** 是一款专为 Google Pixel 及各类 Android 设备（支持 Android 10 至 Android 17 / SDK 29 - 36）打造的轻量级蓝牙音频连接管理工具。

它针对现代蓝牙耳机（如 Bose、Sony、AirPods 等）经常出现的“只连上 BLE 低功耗伴侣节点却无法发声”、“媒体音频未正确接管”、“连接后需手动进设置点连接”等顽疾，直接驱动 Android 底层 `BluetoothA2dp` 与 `BluetoothHeadset` (HFP) 状态机，并在连接成功的瞬间主动接管系统音频焦点。

## 核心特性

- **真正强力连接 (True Audio Connect)**：绕过系统迟钝的重连握手，直接调用 A2DP 和 HFP 连接例程，轮询确认音频通路建立并抢占为主输出设备。
- **彻底三连断开 (Clean Disconnect)**：同时触发 A2DP、HFP 与底层传输链路断开指令，杜绝耳机断开后残留虚假连接或无法释放音频流的异常。
- **自动跟随最近设备**：默认自动锁定并跟随最近连接过的蓝牙耳机或音箱，日常使用无需手动重新绑定。
- **快捷设置磁贴 (Quick Settings Tile)**：下拉通知栏即可一键连接/断开，长按磁贴可秒级直达当前耳机的系统蓝牙详情页。
- **Tasker 深度联动**：
  - 标准 Locale/Tasker 插件支持（支持「连接」、「断开」、「切换状态 (Toggle)」三类动作）。
  - 内置**一键导入 Tasker**：基于 ContentProvider 协议，一键将配置好的项目/任务无缝导入到 Tasker 中。
- **纯净与隐私保护**：
  - 原生暗色 Material 风格，连接与自动化双 Tab 底栏。
  - 默认隐藏 MAC 地址，支持一键点击切换显示。
  - 零第三方 SDK 依赖，无常驻后台耗电进程。

## 系统要求与权限

- **支持系统**：Android 10 (API 29) ~ Android 17 (API 36)。
- **核心权限**：
  - `BLUETOOTH_CONNECT` 与 `BLUETOOTH_SCAN`（Android 12+）
  - `BLUETOOTH` 与 `BLUETOOTH_ADMIN`（低版本兼容）
  - 运行时通过 `HiddenApiHelper` 自动完成系统 Hidden API 豁免。

### 源码编译与安装

项目中已包含轻量级独立构建脚本（依赖 Android SDK 命令工具 `aapt2`、`d8`、`zipalign` 与 `apksigner`）：

```bash
chmod +x build_and_install.sh
./build_and_install.sh
```

## 技术实现原理

1. **Profile Proxy 代理绑定**：异步监听并绑定 `BluetoothProfile.A2DP` 与 `BluetoothProfile.HEADSET` 代理。
2. **音频抢占与轮询确认**：在 Profile 状态变为 `STATE_CONNECTED` 后，反射调用 `setActiveDevice` 立即将媒体与通话流定向至目标设备。
3. **Tasker 事件异步交付**：实现 `com.twofortyfouram.locale.intent.action.FIRE_SETTING` 广播，配合 `goAsync()` 保证后台执行与状态反馈的可靠性。

## 开源协议

本项目采用 MIT 协议开源，详见 [LICENSE](LICENSE) 文件。
