# Preparation validation

## Completed

- Parsed 3 Android XML files successfully.
- Checked balanced Java delimiters in 7 Java source files, ignoring comments and literals. This is not a compiler or a type checker.
- Checked the manifest has no Internet permission.
- Checked the configured minimum SDK is 26.
- Checked application Java package declarations match the configured namespace.
- Parsed the GitHub Actions YAML and checked the build job exists.
- Checked the final ZIP for archive integrity.

## Not completed

- No Java compiler is available in the preparation environment.
- The standalone Java protocol tests have not been executed here.
- No Android SDK build, Android lint run, emulator test, or phone/radio integration test has been performed.
- BLE pairing, MTU negotiation, reconnection, USB driver matching, and actual text delivery require tests on both phones and radios.

The included CI workflow is intended to execute the Java tests and compile the Android APK. Do not describe the app as hardware-tested until those tests are actually performed.
