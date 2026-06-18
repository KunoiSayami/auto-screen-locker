package com.github.kunoisayami.autoscreenlocker

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {

    fun wrap(context: Context): Context {
        val tag = Prefs.language(context)
        if (tag == "auto") return context
        val locale = tagToLocale(tag)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    fun tagToLocale(tag: String): Locale = when (tag) {
        "zh-TW" -> Locale.TRADITIONAL_CHINESE
        "zh-CN" -> Locale.SIMPLIFIED_CHINESE
        else -> Locale.forLanguageTag(tag)
    }
}
