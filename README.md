# Auto Screen Locker

An Android app that automatically turns off the screen after a user-defined period of inactivity.

## How it works

- A foreground service polls every 500 ms to check how long ago the last user interaction occurred
- An accessibility service listens for any touch or key event and resets the idle timer
- When the idle timer expires, the screen is turned off using the configured method

## Screen-off methods

| Method | Requires | Password on wake |
|---|---|---|
| **Lock screen** (default) | Device Admin | Yes |
| **Shizuku** | [Shizuku](https://shizuku.rikka.app/) app + permission | No |
| **Root** | Root access (`su`) | No |

The app detects which methods are available and falls back to Lock screen if the selected method becomes unavailable. The method selector is shown as a collapsible section in the UI.

## Requirements

- Android 8.0 (API 26) or higher
- Root is optional; Shizuku is optional

## Setup

1. **Enable Accessibility Service** — required to detect user interaction. Tap the button, find *Auto Screen Locker* in the accessibility settings list, and toggle it on.
2. **Set timeout** — enter minutes and seconds (minimum 20 seconds). Optionally enable *Show warning 5 seconds before locking* to get a toast notification before the screen turns off.
3. **Choose screen-off method** — expand the *Screen-off method* section and pick one:
   - *Lock screen*: tap **Grant Device Admin** and approve the system prompt.
   - *Shizuku*: install and start Shizuku; the app will request permission automatically.
   - *Root*: grant root access when prompted by your root manager.
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
| `BIND_DEVICE_ADMIN` | Lock the screen via `DevicePolicyManager` (Lock screen method) |
| `BIND_ACCESSIBILITY_SERVICE` | Detect user interaction events |
| `INTERACT_ACROSS_USERS_FULL` | Required by `ShizukuProvider` (declared on the provider, not granted to the app) |

## License

[AGPL-3.0](LICENSE)
