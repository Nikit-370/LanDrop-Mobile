# Technical Architecture & Features 🎮

This document provides a comprehensive breakdown of the internal technical systems powering LAN Drop, outlining server behavior, data models, networking protocols, and UI structures.

---

## 🏗️ System Overview & MVVM Structure

LAN Drop is engineered following the clean separation of concerns prescribed by modern Android development standards. It uses the **MVVM (Model-View-ViewModel)** architectural pattern:

```
┌────────────────────────────────────────────────────────┐
│                        VIEW                            │
│    (Jetpack Compose Dashboard / Web Portal UI)         │
└───────────────────────────┬────────────────────────────┘
                            │ (Observes UI States)
                            ▼
┌────────────────────────────────────────────────────────┐
│                     VIEWMODEL                          │
│               (MainViewModel.kt)                       │
└───────────────────────────┬────────────────────────────┘
                            │ (Dispatches Commands / States)
                            ▼
┌────────────────────────────────────────────────────────┐
│                      MANAGER                           │
│             (FileSharingManager.kt)                    │
└─────────────┬─────────────┴─────────────┬──────────────┘
              │                           │
              ▼ (TCP Bind / Sockets)      ▼ (Writes Logs)
┌───────────────────────────┐   ┌────────────────────────┐
│      HTTP SERVER          │   │      REPOSITORY        │
│ (LocalFileSystemServer)   │   │  (Room / TransferDao)  │
└───────────────────────────┘   └────────────────────────┘
```

---

## 🌐 Embedded Local HTTP Server (`LocalFileSystemServer.kt`)

Rather than relying on third-party frameworks like Ktor-Server or Ktor-Client, which can inflate APK size heavily, LAN Drop builds on top of a highly optimized, lightweight **native socket implementation**.

### 1. HTTP Protocol Handling
- **Socket Recycling**: Leverages Java's standard `ServerSocket` utilizing `reuseAddress = true` to allow immediate socket rebinding without hanging in `TIME_WAIT` cycles.
- **Multi-threaded Client Dispatcher**: Offloads incoming socket requests dynamically straight to a customized cached thread pool executor, maintaining continuous thread availability even under multiple concurrent upload/download actions.
- **Resource Routing Table**:
  - `GET /`: Serves the highly polished, reactive web portal interface contained securely inside `WebPortalHtml.kt`.
  - `GET /api/files`: Returns a serialized JSON array representing the local shared files repository.
  - `POST /api/upload`: Receives file streams over standard HTTP POST requests with a dynamic boundary parser to protect local disk spaces.
  - `GET /api/download`: Pushes binary block arrays directly back to client web browsers.

### 2. High-Performance Mobile Upload Engine
- **Infinite Stream Interceptor**: Prevents local device memory exhaustion (OutOfMemoryError) by streaming bytes straight from the socket input buffer into local files on disk. Only a thin 8KB buffer is allocated in RAM throughout the entire file transfer.
- **Multipart Form Boundary Parsing**: Integrates a robust binary stream segment parser that isolates safe meta-payloads (`filename`, `size`, `pin`) from native multi-part content envelopes without breaking.
- **Storage Protection and DOS Mitigation**: Continually cross-references remaining device storage levels during uploads to block system storage exhaustion.

---

## 🗄️ Database Tracking & Persistence Logs (`Room`)

To maintain offline tracking and security auditing, LAN Drop persists all incoming and outgoing transaction telemetry.

### 1. Entity Schema (`TransferEntity.kt`)
The SQLite file sharing transaction entity records critical details:
```kotlin
@Entity(tableName = "transfers")
data class TransferEntity(
    @PrimaryKey(id = true) val id: Long = 0,
    val fileName: String,
    val fileSize: Long,
    val clientIp: String,
    val direction: String, // "INCOMING" or "OUTGOING"
    val timestamp: Long = System.currentTimeMillis(),
    val status: String // "COMPLETED", "FAILED"
)
```

### 2. Unification with Viewmodel States
The data flows reactively standardizing Flow outputs:
- `TransferRepository` translates Room SQLite rows into structured Kotlin Coroutine `Flow<List<TransferEntity>>` arrays.
- `MainViewModel` collects these historical logs asynchronously and updates the visual logs dashboard automatically with responsive animations.

---

## 🌐 Zero-Configuration Multicast DNS (`mDNS`)

To bypass the tedious requirement of prompting users to manually type out complex IP addresses, LAN Drop implements Network Service Discovery (NSD) using Android's native `NsdManager`:

- **Service Type**: Publishes on standard local broadcast protocols at standard service paths: `_landrop._tcp.`.
- **Automatic IP Discovery**: Browsers or native clients on the same local network listen to mDNS announcements, parsing dynamic IP-Port pairs without asking the end user for network credentials.

---

## 🎨 Jetpack Compose UI Subsystem

The application's interface leverages **Material Design 3 (M3)** with custom layout structures:
- **Responsive Dashboard Screen**: Configured with dynamic window size support, making it look elegant on compact smartphones, medium foldables, and landscape tablets.
- **Cosmic Dark Color Motif**: Styled directly utilizing custom system resources, dark container shadows, and generous positive margins matching Android design guidelines.
- **Real-time Transfer Micro-indicators**: Built around rich Jetpack animations, floating controls, custom text scaling, and instant-connect QR Code generators.
