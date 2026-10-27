# Tomek Mesh Chat 0.2.0 — Passive Signal Update

Copyright (c) 2026 Tomasz Abramowicz. MIT licensed, under the existing repository LICENSE.

## This is an update, not a complete standalone repository

Prepared against the public `AbramTom1984/tomek-mesh-chat` source read during this conversation. The existing package is `com.tomaszabramowicz.meshchat`, Gradle is 8.7, and the unchanged USB dependency is 3.9.0.

Merge the CONTENTS of this folder into the EXISTING repository root. Replace the files at the matching paths and add the new ones. Do not delete existing BLE/USB/Proto sources, resources, root Gradle files or licenses.

## Included paths

```text
.github/workflows/build-apk.yml                   REPLACE
app/build.gradle                                REPLACE
app/src/main/AndroidManifest.xml                  REPLACE
app/src/main/java/com/tomaszabramowicz/meshchat/
    MainActivity.java                           REPLACE
    MeshSession.java                            REPLACE
    SignalInfo.java                             NEW
tests/SignalSelfTest.java                        NEW
UPDATE_0_2_0.md                                  NEW
```

The `app` folder should merge with the existing `app` folder, not become a nested project directory. Ensure the hidden `.github` folder is uploaded too.

## New UI

The existing chat, Connect/Disconnect/More actions, primary-channel behavior, transport classes and local history format are retained.

A compact receive-signal card displays:

- The author of the last qualifying received chat packet.
- RSSI in dBm and SNR in dB when the radio provides those fields.
- Packet age based on the radio receive timestamp when usable.
- An explicit unknown-age message when no usable radio timestamp is available.
- An old-reading marker after two minutes and a disconnected marker when applicable.
- `last radio hop`: this does not claim that a sender was heard directly or that a route was traced.

The signal card updates only from incoming primary-channel text packets. It is not a live spectrum analyser, does not measure your transmit power, and does not show the other endpoint's reception of your transmissions. A value does not become fresh just because the screen redraws.

No test packets, automatic replies, signal polling over LoRa, GPS acquisition, location sharing, or traceroutes were added. Existing local-API heartbeat and connection maintenance remain unchanged. The on-screen age label is refreshed locally every 15 seconds while visible; this is not a radio transmission.

A collapsible Options panel on the same screen contains:

| Switch | Default |
|---|---|
| Sound | Off |
| Vibrate | Off |
| Keep screen on | Off |
| Show signal | On |

Settings are remembered. Sound and vibration are for newly displayed incoming chat events while the app is visible, with the initial configuration interval suppressed. Sound respects the system ringer mode check. The app never wakes the screen to show a new message. Enabling Keep screen on has a battery cost.

## Protocol handling details

The receive metadata reader uses Meshtastic MeshPacket fields `rx_snr` (8, float), `rx_rssi` (12, signed int32) and `rx_time` (7, fixed32). Packets marked `via_mqtt` are excluded. Missing values are not fabricated: an omitted SNR is displayed as `--`, even though proto3 can omit a genuine zero value. It is preferable to show unavailable rather than infer a measurement.

No RSSI percentage or universal good/bad link threshold is invented. The two-minute old-reading label is a UI freshness choice, not a range or delivery assessment.

## Build on GitHub

1. Retain the previous working APK as a rollback copy.
2. Upload this update's contents to the existing repository and commit to `main`.
3. Open Actions → **Build Android APK - Passive Signal 0.2.0**.
4. Select Run workflow → main → Run workflow.
5. The workflow first compiles/runs `SignalSelfTest`, then builds the Android debug APK.
6. On success download **Tomek-Mesh-Chat-0.2.0-debug** from Artifacts, extract it, and install the APK.

No GitHub files were modified and no workflow was started automatically during preparation. Only source update files were generated.

## Important signing and history warning

The app keeps its original application ID and uses versionCode 2. However, independent GitHub-hosted debug builds can be signed with different generated debug keys. A matching application ID is not sufficient for an in-place Android update: the signing key must also match.

If Android reports a conflicting/incompatible package, do not immediately uninstall if you need your existing local messages. Uninstalling removes app data and saved connections. Keep the old APK, record anything you need from the chat, and decide whether to test the new build on another phone or uninstall/reinstall. This update does not solve persistent signing or add a history-export tool.

## Test on the phones

1. Install on one phone first, preserving the other as a working reference.
2. Connect using the same working BLE/USB path.
3. Send an ordinary chat message from the other phone.
4. Verify that the signal card identifies the sender and shows any available metadata.
5. Wait without sending: its age should increase without any signal-test transmissions.
6. After two minutes it should be marked old, not displayed as a current measurement.
7. Open Options and test each switch.
8. Confirm chat send/receive still works in both directions.

This remains a foreground-only application. GPS sharing, direct messages, remote RSSI reporting, automatic range tests and traceroute are NOT included in this incremental build.

## Validation status

- The update was assembled from the existing source and checked structurally.
- Android manifest XML parsing and archive integrity were checked.
- Java self-tests are included for the CI workflow to run.
- Java/Android compilation was NOT run in the preparation environment.
- No APK is included and no new hardware-testing result is claimed.

References:

- https://github.com/AbramTom1984/tomek-mesh-chat
- https://github.com/meshtastic/protobufs/blob/master/meshtastic/mesh.proto
