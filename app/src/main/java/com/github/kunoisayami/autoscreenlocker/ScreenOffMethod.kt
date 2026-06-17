package com.github.kunoisayami.autoscreenlocker

enum class ScreenOffMethod {
    LOCK_NOW,    // DevicePolicyManager.lockNow() — always available, requires password on wake
    SHIZUKU,     // PowerManager.goToSleep() via Shizuku — no password on wake
    ROOT,        // PowerManager.goToSleep() via su — no password on wake
}
