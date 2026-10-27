# Tomek Mesh Chat

A deliberately small Android chat client for existing Meshtastic radios.

**Connect your local radio, open one conversation, and type.** No map, radio configuration editor, GPS dashboard, or account setup.

Copyright (c) 2026 Tomasz Abramowicz · MIT License · Independent prototype

> **Source project, not a prebuilt APK.** This archive has not been built with the Android SDK or tested on physical phones. A GitHub Actions workflow is included to compile a debug APK and execute protocol self-tests. Successful compilation does not establish hardware compatibility.

## Intended setup

```text
Phone A -- Bluetooth BLE or USB OTG -- ESP32-S3 + SX1262
                                             |
                                            LoRa
                                             |
Phone B -- Bluetooth BLE or USB OTG -- ESP32-S3 + SX1262
```

The supplied setup has already exchanged messages successfully using the official Meshtastic application. This client is designed to use the existing firmware and configuration without reflashing or changing radio pins.

Intended phones:
- Motorola Moto G3 with Android 8.1 / LineageOS 15.1.
- Motorola g85 with a newer Android release.

The application's minimum SDK is Android 8.0 (API 26). This is a build configuration, not a claim that BLE/USB behavior has been tested on every compatible phone or custom ROM.

## What this version implements

- A single English-language chat screen.
- BLE radio discovery and a first-time local-radio picker.
- Remembered Bluetooth device and foreground reconnect attempts every ten seconds.
- USB serial detection, Android USB permission handling and connection attempts.
- Meshtastic configuration download using a nonzero single-stage nonce.
- Automatic use of the configured primary channel after the matching configuration-complete response.
- Plain text send and receive, UTF-8 support, up to 180 bytes per outgoing message.
- A local history of the latest 200 messages and a clear-history action.
- Honest send status: a successful local transport write is not presented as recipient delivery.
- No Internet permission, account, hosted server or firmware administration writes.

## Important scope limits

1. **Primary-channel broadcasts, not private DMs.** Other nodes with access to the channel may read messages. Direct messages are intentionally not shown or sent in this version. Both radios must share a compatible primary channel; being able to exchange official-app DMs alone does not establish this.
2. **Foreground prototype.** Keep the app visible. On leaving the app it closes the radio connection and reconnects when reopened. There is no foreground service, guaranteed screen-off reception, or incoming-message notification system yet. Messages may be missed; history is not synchronized from the official app.
3. **First use is not permission-free.** Android may require Bluetooth permissions, pairing/PIN approval, or USB access approval. The app cannot bypass these prompts. A user chooses the local BLE radio once so it does not accidentally select the other person's radio.
4. **USB is conditional.** OTG/host support, an appropriate data cable, adequate power and a supported USB serial interface are required. Native CDC, bridge-chip behavior, driver matching, and the firmware's enabled serial API must be checked on the actual board. CH343 support is not claimed merely because other CH34x devices are supported.
5. **No delivery guarantees.** The application does not resend text automatically, bypass radio limits, or claim that an on-air transmission or remote receipt follows from a USB/BLE write. Some routing errors are displayed when received.
6. **Local plaintext history.** Messages are saved in app-private storage without additional app-level encryption. Android backup is disabled. Clear history or uninstall to remove the app's saved messages.
7. **Close the official app's radio connection first.** Do not run two phone clients against the same local radio while testing.

## Get an APK without installing Android Studio

Use a new GitHub repository, for example `tomek-mesh-chat`. Keep this separate from the ultrasonic-scanner project.

1. Extract this ZIP.
2. Upload the **contents** of the `TomekMeshChat` folder, including `.github/workflows/build-apk.yml`, to the new repository. Do not upload just the ZIP and do not add an extra nested folder around the project files.
3. Push/upload to the `main` branch. The included **Build Android APK** workflow is configured to run automatically.
4. Open the repository's **Actions** tab. If required, enable Actions for the repository.
5. Open the build run and inspect the result. A failure is a build issue to investigate, not an APK to install.
6. After a successful run, open **Artifacts** and download **Tomek-Mesh-Chat-debug**.
7. Extract that artifact ZIP; it should contain `app-debug.apk`.
8. Transfer the same APK to both phones. Allow installation from the source application when Android asks, then install it.

This workflow downloads Android SDK packages, Gradle, build plugins and the USB serial dependency on GitHub's runner. Review it before enabling it. It does not upload releases, send messages, or flash radios. It uses a temporary debug signing key. Later CI runs may produce a different key; a signature-mismatch update can require uninstalling the old build, which removes its local history. A persistent private signing key is a later distribution step and is not included in this package.

## Build locally

Requirements: JDK 17, Android SDK platform 35, Gradle 8.7, and network access for build dependencies.

```bash
gradle :app:assembleDebug
```

The APK is produced under `app/build/outputs/apk/debug/`.

The archive contains Gradle configuration but not a Gradle wrapper JAR or downloaded Android tools. Use a local Gradle 8.7 installation or the included CI workflow. When importing in Android Studio, select the local Gradle installation if wrapper files are requested. Do not assume an old Windows installation can run a modern Android Studio toolchain; CI avoids needing that toolchain on the local computer.

## First Bluetooth test

1. Keep the working firmware and antennas in place.
2. Disconnect the official Meshtastic app from the local radio.
3. Open Tomek Mesh Chat, allow Bluetooth access, and enable Bluetooth.
4. On Android 8–11, permit the requested location access and enable the Location switch if scanning requires it. This app does not request phone GPS coordinates.
5. Choose **your own nearby radio** from the picker. Approve system pairing if requested.
6. Wait for configuration to finish. The status should show Bluetooth and the primary channel, and **Send** should become enabled.
7. Repeat on the second phone with the second radio.
8. Send `TEST A`, then reply `TEST B`.

On later openings the saved BLE radio is tried automatically. If it is unavailable, **Connect → Find Bluetooth radio** lets you choose again. **Disconnect** suspends automatic attempts until Connect is used.

## First USB test

1. Connect the local radio using a data-capable OTG arrangement.
2. Open the app and approve Android's USB access dialog.
3. If automatic detection does not connect, choose **Connect → Use USB**.
4. Wait for configuration, then send a short test message.
5. Use **More → USB device information** for detected VID/PID and driver matching.

The application attempts USB before remembered Bluetooth while disconnected. It does not silently replace a live BLE connection just because a cable is plugged in; disconnect and select USB to switch. If no USB driver matches, start with BLE and collect the USB information for a driver-specific fix rather than reflashing the radio.

## Troubleshooting

| Symptom | Check |
|---|---|
| Radio not found | Disconnect the official client, enable Bluetooth, grant permissions, and on older Android enable Location for scanning |
| GATT / pairing error | Accept the system pairing dialog, check the radio's existing PIN, and retry |
| Reading configuration never finishes | Check the correct local device, close other clients, and retain the exact error message |
| USB device not listed | OTG/host support, data cable, power and **More → USB device information** |
| USB opens but no configuration arrives | Correct USB interface, working serial API, driver behavior and port selection need real-device testing |
| Send succeeds locally but other phone is silent | Confirm both primary channels match; this app does not send DMs or change channel settings |
| Long Bluetooth message rejected | The actual negotiated MTU is too small; shorten the message, reconnect or test USB |
| No messages with screen off | Foreground-only limit of this prototype |

Do not publish channel keys, private keys, pairing PINs or other credentials when reporting a problem.

## Protocol implementation

- BLE service `6ba1b218-15a8-461f-9fa8-5dcae273eafd`.
- Serialized GATT operations, a requested MTU of 512, `FromNum` subscription and `FromRadio` draining.
- USB stream framing: `94 c3 LENGTH_HI LENGTH_LO` and up to 512 protobuf payload bytes.
- A small bounded protobuf wire codec for the subset needed by primary-channel text chat.
- The configuration handshake reads local node/channel information; it does not apply configuration changes.
- Periodic empty Heartbeat messages on the local API, not periodic on-air text transmissions.
- Outgoing text: `TEXT_MESSAGE_APP`, broadcast destination `0xffffffff`, configured local channel index, random nonzero packet ID.
- No private-message key handling, firmware updates, full mesh SDK, or telemetry UI.

Protocol references are in `NOTICE.md`. No official-client code has been copied into this project.

## Tests and validation

`tests/ProtoSelfTest.java` contains standalone Java checks for golden wire bytes, UTF-8, uint32 values, truncated input, malformed lengths, and stream resynchronization. The CI workflow runs these before the Android build.

The preparation environment has no Java compiler or Android SDK, so these Java tests and the Android build have **not** been executed here. XML parsing, archive integrity and packaging checks are separate from compilation and hardware testing. See `VALIDATION.md` for the actual preparation checks.

## License

Copyright (c) 2026 Tomasz Abramowicz.

Original code and documentation are available under the MIT License; see `LICENSE`. The USB serial library has its own MIT copyright notices reproduced in `THIRD_PARTY_LICENSES.md`. Meshtastic and Android projects retain their respective rights. This project is not endorsed by those projects.
