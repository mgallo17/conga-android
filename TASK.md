# Conga Android App — Task

Build a native Android app (Java, minSdk 26, targetSdk 35) to control the Cecotec Conga 1490/1590 robot vacuum.

## Context — Protocol

The Conga 1490/1590 uses a TCP binary protocol with Protocol Buffers to communicate with Cecotec's server.
Reference: https://github.com/adrigzr/badconga (deprecated Home Assistant plugin — has the full protocol)
Reference: https://xumeiquer.github.io/LibreConga/research/protocol/

The protocol:
- TCP connection to Cecotec's server
- Binary framing: [length 4 bytes LE] [ctype 2 bytes] [flow 2 bytes] [userId 4 bytes] [deviceId 4 bytes] [sequence 6 bytes] [opcode 2 bytes] [payload protobuf]
- OpCodes: 2005=Ping, 2006=PingResp, 3001=Login, 3002=LoginResp
- After login, commands are sent to control the robot

## App Structure

Create a complete Android Studio project with:

### 1. Project setup
- Package: `com.mgallo17.conga`
- Java (not Kotlin)
- minSdk 26, targetSdk 35
- Dependencies: protobuf-java, okhttp (for any HTTP fallback)

### 2. Screens
- **LoginActivity**: Email + password fields, Login button. Stores credentials in SharedPreferences (encrypted).
- **MainActivity**: Main control screen with buttons:
  - ▶️ Start cleaning
  - ⏹️ Stop
  - 🏠 Return to base
  - 📅 Schedule (future)
  - Status indicator (connected/disconnected/cleaning/charging)

### 3. Core classes
- `CongaClient.java`: Manages TCP connection to Cecotec server, sends/receives Protocol Buffer messages
- `CongaProtocol.java`: Frame builder/parser — builds binary frames with the header structure above
- `CongaCommands.java`: Constants for OpCodes and command payloads
- `CongaService.java`: Android background Service to maintain the TCP connection

### 4. GitHub Actions CI
Create `.github/workflows/build.yml` that:
- Triggers on push to main
- Builds debug APK with `./gradlew assembleDebug`
- Uploads APK as artifact

### 5. Proto files
Create `app/src/main/proto/conga.proto` with the login and command message structures based on the badconga project.

## Notes
- Start with a working skeleton that compiles and has the UI
- The TCP protocol implementation can start with the ping/login flow
- Use the badconga Python code as reference for the exact byte structure
- Add TODO comments where the exact server address/port needs to be confirmed

## When done
Commit everything, push to origin main, then run:
openclaw system event --text "Done: conga-android skeleton built and pushed to GitHub" --mode now
