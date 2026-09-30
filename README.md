# OBD‑Droid

**Full-stack Android diagnostics suite built for technicians, tuners, and enthusiasts.**

OBD‑Droid turns any ELM327-compatible adapter into a professional-grade vehicle workstation. From live telemetry to safety recalls and an AI copilot, every module is engineered to make complex diagnostics feel effortless.

> **TL;DR**
> - Plug in any ELM327 adapter, launch the Android app, and get live data in under a minute.
> - One tap runs a full ECU scan, logs CSV/GPS telemetry, and syncs findings with your AI copilot.
> - Safety recalls, VIN decode, and report exports are all presented in-app with the same cohesive UI.

---

## 🚀 Why It Stands Out

- **Mission Control for Your Vehicle** – Real-time dashboards, custom PIDs, HUD mode, and CSV/GPS logging.
- **Deep Diagnostics Pipeline** – ECU discovery, freeze frames, emissions readiness, and health scoring in one flow.
- **VIN-Aware Intelligence** – VIN decode and NHTSA safety recalls for the connected vehicle.
- **AI Copilot** – OpenAI ChatGPT delivers contextual answers using live vehicle data and prior conversations.
- **Modular Architecture** – Service-driven Android app, standalone companion APIs, and reusable libraries.

> _“From handshake to fix recommendation, OBD‑Droid shows how far Android can stretch inside the garage.”_

---

## 📸 Product Tour

<table>
  <tr>
    <td><img src="docs/screenshots/dashboard.png" width="260" alt="Dashboard" /></td>
    <td>Live vehicle overview with quick actions, health summaries, and shortcut metrics.</td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/live-data.png" width="260" alt="Live Data" /></td>
    <td>Customizable PID lists, chart overlays, HUD mode, and streaming CSV capture.</td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/fault-codes.png" width="260" alt="Fault Codes" /></td>
    <td>Rich DTC cards with freeze frames, remedy hints, and AI follow-ups.</td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/recalls-all.png" width="260" alt="Safety Recalls" /></td>
    <td>NHTSA safety recall campaigns for the connected vehicle's VIN, with remedy details and CSV/JSON export.</td>
  </tr>
</table>

<details>
<summary>More UI moments</summary>

- <img src="docs/screenshots/dashboard-footer.png" width="220" alt="Vehicle Footer" /> – Persistent vehicle identity footer across screens.
- <img src="docs/screenshots/live-data-3.png" width="220" alt="Live Data (Scrolled)" /> – Extended PID telemetry with HUD-ready formatting.
- <img src="docs/screenshots/fault-code-detail.png" width="220" alt="Fault Code Detail" /> – Deep dive card with freeze frame, remedy, and AI assistance.
- <img src="docs/screenshots/full-vehicle-scan.png" width="220" alt="Full Vehicle Scan" /> – ECU discovery progress and baseline snapshot library.
- <img src="docs/screenshots/emissions.png" width="220" alt="Emissions Readiness" /> – I/M monitors with pass/fail flags for inspection prep.
- <img src="docs/screenshots/vin-decoder.png" width="220" alt="VIN Decoder" /> – VIN decode powering downstream features.

</details>

---

## 🎛️ Feature Gallery

<table>
  <tr>
    <td><img src="docs/screenshots/emissions.png" width="220" alt="Emissions Readiness" /></td>
    <td><img src="docs/screenshots/full-vehicle-scan.png" width="220" alt="Full Vehicle Scan" /></td>
    <td><img src="docs/screenshots/ecu-list.png" width="220" alt="ECU Modules" /></td>
    <td><img src="docs/screenshots/live-data-6.png" width="220" alt="Live Data Telemetry" /></td>
  </tr>
  <tr>
    <td>Inspection-ready I/M monitor dashboard showing pass/fail status at a glance.</td>
    <td>Step-by-step ECU discovery with progress tracking and baseline snapshot creation.</td>
    <td>Comprehensive ECU list with addressing, protocol metadata, and change tracking.</td>
    <td>Live powertrain data fused with GPS & motion telemetry from the device sensors.</td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/fault-codes-start-screen.png" width="220" alt="Fault Codes Start" /></td>
    <td><img src="docs/screenshots/fault-codes-detected.png" width="220" alt="Fault Codes Detected" /></td>
    <td><img src="docs/screenshots/fault-code-detail.png" width="220" alt="Fault Code Detail" /></td>
  </tr>
  <tr>
    <td>Fault code launcher prompting users to initiate a fresh scan.</td>
    <td>Detected DTC list with severity badges and quick actions.</td>
    <td>Drill-down view showing summaries, remedies, and export options.</td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/vin-decoder.png" width="220" alt="VIN Decoder" /></td>
    <td><img src="docs/screenshots/dashboard-footer.png" width="220" alt="Vehicle Footer" /></td>
    <td></td>
    <td></td>
  </tr>
  <tr>
    <td>VIN decode powering downstream features like recalls.</td>
    <td>Signature vehicle footer with quick stats, connection state, and VIN context.</td>
    <td></td>
    <td></td>
  </tr>
</table>

---

## 🧩 Feature Deep Dive

### Real-time Telemetry
- Adaptive polling with automatic ISO/KWP/CAN protocol negotiation.
- Multiple view modes (list, chart, dashboard, HUD) backed by shared `ProcessVariables`.
- CSV logger service stitches OBD values with GPS, accelerometer, and metadata for post-drive analysis.
  <br/><img src="docs/screenshots/live-data-6.png" width="280" alt="Live Data with Telemetry" />
  <div><em>Live data + GPS/motion telemetry captured from device sensors alongside vehicle PIDs.</em></div>

### Diagnostics & Scanning
- Full-vehicle scan orchestrator maps ECUs, persistent DTCs, pending codes, and module metadata.
- Baseline scan library compares historic snapshots to spot new modules or faults.
- Emissions center mirrors inspection readiness (I/M monitors, catalyst status, O₂ sensors).

> <img src="docs/screenshots/full-vehicle-scan.png" width="280" alt="Full vehicle scan workflow" />

### Intelligent Assistance
- **CoPilot** (OpenAI ChatGPT) pulls context from active DTCs, vehicle metadata, and prior chats.
- Automatic suggested next steps and part lookup hints based on failure patterns.
- Conversation history cached per vehicle for continuity.

### Vehicle Intelligence
- VIN decode via `VINDecoder` (NHTSA) with manufacturer/trim heuristics.
- Recall center listing NHTSA campaigns for the connected vehicle's VIN.

### Fault Code Workflow
- Launch screen guides the user to initiate a fresh scan before clearing codes.<br/><img src="docs/screenshots/fault-codes-start-screen.png" width="220" alt="Fault Codes Start" />
- Detected DTC list surfaces severity badges, quick actions, and export shortcuts.<br/><img src="docs/screenshots/fault-codes-detected.png" width="220" alt="Fault Codes Detected" />
- Detail cards provide summaries, consequences, remedies, and OEM links in-app.<br/><img src="docs/screenshots/fault-code-detail.png" width="220" alt="Fault Code Detail" />

---

## 🧱 Architecture at a Glance

```
            ┌──────────┐
Adapter ⇨ CommService ⇨ ObdProt ⇨ ObdDataService ─┬─► Live Data / Gauges
            └──────────┘                           ├─► Fault Codes / Emissions
                                                  │   (freeze frames, readiness)
                                                  ├─► ScanOrchestrator → Baseline Library
                                                  ├─► CsvLoggingService (GPS + sensors)
                                                  └─► Feature Modules (CoPilot, Recalls)

NHTSA API            ⇨ RecallLookupAndroid ⇨ RecallActivity
Claude API           ⇨ CoPilotService ⇨ CoPilotActivity (contextual AI answers)
```

- **Separation of Concerns** – OBD stack lives in its own package, while UI features orchestrate data via managers/services.
- **Request Queues & Caching** – VIN lookups and recall results are cached in-app for instant revisits offline.
- **Configurable Telemetry** – Logging and AI features respond to user settings stored in `SharedPreferences`.

---

## 🔧 Tech Stack

| Layer | Details |
| --- | --- |
| Language | Java 17 (Android) |
| UI | AppCompat + Material Components, RecyclerView, custom cards, HUD mode |
| Architecture | Service + manager pattern, feature-scoped packages, background workers |
| Data | SharedPreferences caching, on-disk CSV/JSON exports |
| Integrations | NHTSA APIs, OpenAI ChatGPT |
| Tooling | Gradle, Android Studio Giraffe+, Lint, unit tests |
| Submodules | [DTC Database](https://github.com/Wal33D/dtc-database) · [NHTSA Recall Lookup](https://github.com/Wal33D/nhtsa-recall-lookup) · [Automotive Logo Library](https://github.com/Wal33D/automotive-logo-library) |

---

## 📦 Project Map

```
OBD-Droid/
├── app/
│   ├── src/java/com/obddroid/
│   │   ├── features/          # Feature modules (copilot, emissions, recalls, etc.)
│   │   ├── obd/               # Core OBD protocol implementation
│   │   ├── scan/              # ECU discovery & orchestration
│   │   ├── telemetry/         # CSV logging, GPS stitching
│   │   ├── services/          # Foreground/background Android services
│   │   ├── ui/activities/     # Primary screens
│   │   └── utils/             # Helpers, VIN decoding, state managers
├── modules/                   # External libraries (submodules)
│   ├── dtc-database/          # Offline DTC catalog
│   ├── nhtsa-recall-lookup/   # NHTSA API bindings
│   └── automotive-logo-library/
├── docs/                      # Product notes, screenshots
└── README.md
```

---

## ⚙️ Getting Started

### Prerequisites
- Android Studio **Giraffe** (or newer) with **JDK 17**.
- Android SDK targets **API 21 – 34** (Android 5.0 – 14).
- Physical Android device (recommended) with Bluetooth or USB host support.
- ELM327-compatible OBD-II adapter (Bluetooth Classic, BLE serial, USB, or Wi-Fi).

### Clone & Bootstrap

```bash
git clone https://github.com/Wal33D/OBD-Droid.git
cd OBD-Droid

# Pull bundled libraries (DTC database, logos, NHTSA bindings)
git submodule update --init --recursive

# Build & install debug variant
./gradlew :app:assembleDebug
./gradlew :app:installDebug

# Launch on device
adb shell am start -n com.obddroid/.ui.activities.MainActivity
```

### Android Auto (Desktop Head Unit)

Android Auto shows adapter status, live data, and the last-read fault codes from the connection made in the phone app (`com.obddroid.car`). Connect to the adapter on the phone first; the car screens don't open their own connection.

1. In Android Studio's SDK Manager → **SDK Tools**, install **Android Auto Desktop Head Unit Emulator**.
2. Use a phone emulator with a **Google Play** system image (or a real phone) that has the Android Auto app.
3. In Android Auto settings, tap **Version** 10 times to enable developer mode. Then, from the overflow menu, enable **Unknown sources** and choose **Start head unit server**.
4. Install the debug build and start the DHU:

```bash
./gradlew :app:installDebug
adb forward tcp:5277 tcp:5277
$ANDROID_HOME/extras/google/auto/desktop-head-unit
```

Without a physical adapter, run an ELM327 simulator on your computer (`pip install ELM327-emulator`, then `elm -n 35000`). In the app's adapter screen, choose **Wi-Fi** with IP `10.0.2.2` and port `35000`.

---

## 🧪 Developer Workflow

| Task | Command |
| --- | --- |
| Clean build | `./gradlew clean` |
| Compile sources | `./gradlew :app:compileDebugJavaWithJavac` |
| Unit tests | `./gradlew :app:testDebugUnitTest` |
| Lint & static analysis | `./gradlew :app:lintDebug` |
| Instrumentation tests | `./gradlew :app:connectedDebugAndroidTest` |
| Generate signed bundle | `./gradlew :app:bundleRelease` |

**Environment toggles**
- `gradle.properties` houses feature flags, logging verbosity, and Claude API settings.
- `app/build.gradle` encapsulates flavors, signing configs, and dependency graph.

---

## 🔌 Integrations

| Integration | Purpose | Notes |
| --- | --- | --- |
| **NHTSA Recall API** | Campaign listings, remedy info, VIN decodes | Backbone for the Safety Recalls screen and VIN decoder. |
| **OpenAI ChatGPT** | Conversational diagnostics | Context-aware responses with client-side redaction. |
| **VIN Decoder** | Make/model/trim heuristics | Normalizes manufacturer naming for recall lookups. |

---

## 🗺️ Roadmap

- [ ] Jetpack Compose migration for telemetry dashboards.
- [ ] BLE adapter improvements and auto-reconnect heuristics.
- [ ] Cloud sync for baseline scans.
- [ ] In-app marketplace for pro data packs (TSBs, repair procedures).

---

## 🤝 Meet the Maker

Built and maintained by **Waleed Judah**
📫 aquataze@yahoo.com · 🔗 [LinkedIn](https://www.linkedin.com/in/waleed-judah-53406787/)

> Seriously though — if you're hiring, reach out. All I do is build.

> Found this useful? ⭐ the repo, share it with your community, and keep connected cars transparent.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE). See the `LICENSE` file for details.
