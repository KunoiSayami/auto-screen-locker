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

The app detects which methods are available and falls back to Lock screen if the selected method becomes unavailable.

## Requirements

- Android 8.0 (API 26) or higher
- Root is optional; Shizuku is optional

## Setup

1. **Enable Accessibility Service** — required to detect user interaction. Tap the button, find *Auto Screen Locker* in the accessibility settings list, and toggle it on.
2. **Set timeout** — enter minutes and seconds (or switch to seconds-only mode). Minimum is 20 seconds. Optionally enable *Show warning 5 seconds before locking* to get a toast notification before the screen turns off.
3. **Choose screen-off method** — open *Settings* and pick one:
   - *Lock screen*: tap **Grant Device Admin** and approve the system prompt.
   - *Shizuku*: install and start Shizuku; the app will request permission automatically.
   - *Root*: grant root access when prompted by your root manager.
4. **Start Service** — the foreground service begins monitoring inactivity.

The service restarts automatically after a device reboot if it was running when the device was shut down.

## Features

### App blacklist / whitelist

Open the *App List* screen (via the main screen button or Settings) to suppress or limit locking based on the foreground app:

| Mode | Behaviour |
|---|---|
| **Off** | Lock timer always runs regardless of foreground app |
| **Blacklist** | Screen will not lock while a listed app is in the foreground |
| **Whitelist** | Screen will only lock while a listed app is in the foreground |

### Persistent re-lock

Enable *Re-lock after each inactivity period (persistent)* to keep locking the screen repeatedly after each timeout instead of stopping after the first lock.

### Warning toast

Enable *Show warning 5 seconds before locking* to display a brief toast notification before the screen turns off.

### Language

The app ships with translations for English, French, German, Japanese, Traditional Chinese, and Simplified Chinese. The language can be set manually in Settings, or left on *Auto* to follow the system locale.

### Battery optimization

On first launch the app prompts you to exclude it from battery optimization, which is required for reliable background operation.

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
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prompt to exclude the app from battery optimization |
| `QUERY_ALL_PACKAGES` | Enumerate installed apps for the blacklist/whitelist picker |
| `INTERACT_ACROSS_USERS_FULL` | Required by `ShizukuProvider` (declared on the provider, not granted to the app) |

## License

[AGPL-3.0](LICENSE)
