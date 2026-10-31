# Bitchat Android: Offline Disaster SOS Mesh & Emergency Triage System
### Final Year BE Project (2026–2027)

A decentralized, off-grid communication and emergency disaster triage application that operates over Bluetooth Low Energy (BLE) multi-hop mesh networks without requiring cellular infrastructure, internet access, SIM cards, or central servers.

---

## 🌟 Key Innovations & BE Project Features

- **🚨 Offline Disaster SOS Beacon:** Broadcasts 1-byte aligned compact Big-Endian binary telemetry packets containing real-time GPS coordinates, battery percentage, and triage severity level (`CRITICAL_MEDICAL`, `TRAPPED_SEARCH_RESCUE`, `HAZARD_WARNING`, `SUPPLIES_WATER`).
- **🔄 Multi-Hop Relay with Controlled TTL:** Packets hop peer-to-peer across intermediate civilian smartphones up to 7 hops without requiring Wi-Fi routers or mobile towers.
- **🛡️ Broadcast Storm Mitigation:** In-memory LRU duplicate suppression cache prevents exponential packet retransmission and radio congestion.
- **⚡ Priority Queueing:** Disaster distress beacons bypass adaptive traffic throttling, guaranteeing 100% transmission priority across the BLE mesh.
- **↩️ Closed-Loop Reverse Triage Acknowledgment (ACK):** When rescue personnel acknowledge distress, an `EMERGENCY_ACK` (Opcode 0x31) packet routes back across the mesh, providing the victim with responder ETA and triggering offline Morse code (`... --- ...`) vibration.
- **🔬 In-App Real-Time Network Visualizer & Simulator:** Interactive physics canvas (`/simulate`) demonstrating real-time ad-hoc topology, dynamic link formation/severance, and animated packet transfer across hops.
- **🔐 End-to-End Encryption:** Noise Protocol Framework (Curve25519, ChaCha20-Poly1305) for private peer-to-peer communications.
- **🌐 Dual-Mode Live BLE & Virtual Disaster Lab:** Monitor live Bluetooth hardware peers or run 6-node virtual triage simulations for academic defense.

---

## 📂 Project Documentation & Viva Assets

All comprehensive technical reports, protocol specifications, diagrams, and presentation assets are located in the [`docs/`](docs/) directory:

- 📄 **[Comprehensive Architecture & Viva Defense Guide](docs/PROJECT_ARCHITECTURE_AND_VIVA_GUIDE.md)**: Detailed report with Clean Mermaid diagrams covering system layers, relay algorithms, and examiner Q&A.
- 📑 **[Publication-Quality PDF Project Report](docs/BITCHAT_DISASTER_MESH_PROJECT_REPORT.pdf)**: 5-page formatted report with embedded 300 DPI architecture and sequence diagrams.
- 🎨 **[High-Resolution Visual SVG Diagrams](docs/ARCHITECTURE_DIAGRAMS.html)**: Standalone visual diagrams for laptop and projector presentations.
- 💻 **[Interactive Web Simulation Lab](docs/simulation/index.html)**: HTML5 Canvas multi-hop disaster mesh simulator for demonstration during viva.

---

## 🛠️ Build & Installation

### Prerequisites
- Android Studio Ladybug or newer
- JDK 17 / JVM Target 1.8
- Android SDK 34 (Android 14+)

### Commands
```bash
# Build Debug APK
./gradlew assembleDebug

# Run Unit Tests
./gradlew testDebugUnitTest

# Compile Kotlin Source
./gradlew compileDebugKotlin
```

---

## 📜 License & Academic Attribution
Developed as a Final Year Bachelor of Engineering (B.E.) Capstone Project by **Ramkishan Gupta** (2026–2027).
Released under GNU General Public License v3.0.
