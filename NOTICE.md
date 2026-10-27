# Attribution and third-party notices

Copyright (c) 2026 Tomasz Abramowicz. Original application code and documentation: MIT License.

This is an independent prototype client for the Meshtastic PhoneAPI. It is not an official Meshtastic application and does not bundle Meshtastic firmware or the official Android application. Meshtastic names and marks belong to their respective owners.

The application depends on `usb-serial-for-android` version 3.9.0 by mik3y and contributors, licensed under the MIT License. It is fetched during the build, not included as source in this archive. Its license and corresponding source are available at https://github.com/mik3y/usb-serial-for-android/tree/v3.9.0 . The dependency license is reproduced in THIRD_PARTY_LICENSES.md. Retain applicable notices when redistributing the application.

Protocol field definitions and behavior were checked against the Meshtastic public API documentation and protobuf schemas. The minimal wire codec is original application code; it is not a generated complete Meshtastic SDK.

Reference material:
- https://meshtastic.org/docs/development/device/client-api/
- https://github.com/meshtastic/protobufs/blob/master/meshtastic/mesh.proto
- https://github.com/meshtastic/protobufs/blob/master/meshtastic/channel.proto
- https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
