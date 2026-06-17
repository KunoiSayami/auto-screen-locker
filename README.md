# Auto Screen Locker

An Android app that automatically locks the screen after a user-defined period of inactivity.

## How it works

- A foreground service polls every 500 ms to check how long ago the last user interaction occurred
- An accessibility service listens for any touch or key event and resets the idle timer
- When the idle timer expires, `DevicePolicyManager.lockNow()` is called to lock the screen

## Requirements

- Android 8.0 (API 26) or higher
- No root required

## Setup

1. **Grant Device Admin** — required to call `lockNow()`. Tap the button in the app and approve the system prompt.
2. **Enable Accessibility Service** — required to detect user interaction. Tap the button, find *Auto Screen Locker* in the accessibility settings list, and toggle it on.
3. **Set timeout** — enter minutes and seconds (minimum 60 seconds).
4. **Start Service** — the foreground service begins monitoring inactivity.

The service restarts automatically after a device reboot if it was running when the device was shut down.

## Build

Requires the Android SDK (set in `local.properties`) and Java 11+.

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or to build and install in one step:

```bash
./gradlew installDebug
```

## Permissions

| Permission | Purpose |
|---|---|
| `FOREGROUND_SERVICE` | Run the lock monitor service in the background |
| `RECEIVE_BOOT_COMPLETED` | Restart the service after reboot |
| `BIND_DEVICE_ADMIN` | Lock the screen via `DevicePolicyManager` |
| `BIND_ACCESSIBILITY_SERVICE` | Detect user interaction events |

## License

[AGPL-3.0](LICENSE)
