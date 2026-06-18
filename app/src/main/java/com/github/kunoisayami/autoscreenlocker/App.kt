package com.github.kunoisayami.autoscreenlocker

import android.app.Application
import android.content.Context
import java.util.Locale

class App : Application() {

    override fun attachBaseContext(base: Context) {
        systemLocale = base.resources.configuration.locales[0]
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    companion object {
        lateinit var systemLocale: Locale
            private set
    }
}
