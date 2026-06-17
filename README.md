# LAN Drop (LANDrop) 🚀

> A visually striking, high-speed regional file-sharing application that turns your Android device into a local sharing hub. Wirelessly upload and download files directly to/from any browser on your local Wi-Fi network with zero internet dependency and absolute privacy.
>
> ⚡ **Vibe Coded beautifully with Gemini 3.5 Flash**

---

## 🌌 The Power of Vibe Coding (Gemini 3.5 Flash)

This application has been crafted with **Google AI Studio** and **vibe coded using the Gemini 3.5 Flash model**. 

By translating high-level descriptions into a production-grade, highly optimized codebase, Gemini 3.5 Flash designed and executed:
- **Clean MVVM Android Engine**: Robust, lifecycle-aware background handlers and service binding.
- **Embedded Webserver System**: High-performance HTTP server capable of multi-part streaming uploads with visual feedback.
- **Self-Healing Safeguards**: Clean Kotlin recovery code that prevents state corruption when binding to invalid or restricted ports.
- **Material 3 Dynamic UI**: Elegant layout structure with dark-mode aesthetic styling, dynamic animations, and responsive micro-interactions.

---

## 🛠️ Complete Feature Highlights

- **Direct Wireless Peer-to-Peer**: Send files to any computer, smartphone, or tablet through a simple, beautiful browser-based sharing portal without installing auxiliary clients.
- **Self-Healing Network Port System**: Advanced configuration checker that prevents the server from crashing or locking up when users pick invalid, system-reserved (`1-1023`), or already-occupied ports.
- **Robust Background Execution**: Keeps sharing active even when your screen is off, utilizing secure Android Foreground Services (`FileServerService`) boosted by dynamic WakeLocks and WifiLocks.
- **Modern Jetpack Compose UI**: Stunning dark cosmic UI design built on top of Material Design 3 guidelines:
  - Responsive layout grids with adaptive safety structures.
  - Interactive QR Code generator for instantaneous zero-typing connections.
  - Active transfers monitor and full historical Room Database auditor.
- **Interactive Web Portal**: Beautiful responsive web portal with password protection (PIN), drag-and-drop support, real-time upload progress bars, and localized storage.

---

## 📂 Modular Technical Documentation 📖

To make this codebase intuitive for developers and contributors, the technical documentation is broken down into clean, dedicated files. Click on a link below to explore:

### 🎮 [1. Architecture & Features Details](./docs/ARCHITECTURE_AND_FEATURES.md)
Discover the multi-layered design of our local sharing subsystem.
- Embedded local HTTP server (`LocalFileSystemServer`)
- SQLite Room Database Integration and custom Transfer Logs
- Dynamic Jetpack Compose structure & M3 components
- Multicast DNS (mDNS) advertisement setup for local zero-configuration discovery

### 🛡️ [2. Port Safeguards & Self-Healing Engine](./docs/PORT_SAFEGUARD_AND_RECOVERY.md)
A technical retrospective and walkthrough of our custom port recovery safeguard framework.
- Socket cleanup mechanisms to prevent stale `ServerSocket` locks
- System port boundaries detection (blocking `1-1023` on non-rooted Android)
- `lastKnownWorkingPort` recovery state machine walkthrough
- Standard common ports testing analysis and results

### 🔧 [3. Development, Build, & Test Pipeline](./docs/DEVELOPMENT_AND_SETUP.md)
Step-by-step instructions to clone, build, run, and verify the application.
- Prerequisites & Android SDK requirements
- Unit and Robolectric JVM test commands
- Dynamic resource guidelines and APK compilation

---

## 📦 Supported File Formats & Extensions

LAN Drop handles binary transfers via raw byte buffering. As a generic high-performance file sharing engine, **it supports all file extensions** with zero formatting or size restrictions:

| Category | Supported File Extensions |
| :--- | :--- |
| 🖼️ **Images** | `.png`, `.jpg`, `.jpeg`, `.gif`, `.webp`, `.svg`, `.heic` |
| 🎥 **Video & Audio** | `.mp4`, `.mkv`, `.mov`, `.webm`, `.mp3`, `.wav`, `.aac`, `.flac` |
| 🗄️ **Documents** | `.pdf`, `.txt`, `.docx`, `.xlsx`, `.pptx`, `.md`, `.json`, `.csv` |
| 📦 **Software & Code** | `.apk`, `.bin`, `.kt`, `.html`, `.css`, `.js`, `.py` |
| 🤐 **Archives** | `.zip`, `.rar`, `.7z`, `.tar.gz` |

---

## 🚀 Quick Run Guide

1. Ensure your Android device and the target device (e.g., laptop/tablet) are on the **exact same Wi-Fi network** or sharing a hotspot.
2. Open LAN Drop and toggle **Start Sharing Portal** to boot up the HTTP web portal.
3. Use the generated URL (e.g., `http://192.168.1.100:8080`) or **scan the QR Code** from your other device to start broadcasting, downloading, and storing files!
