package com.base.aihelperwearos.utils

import android.content.Context
import android.os.Build
import java.util.Locale

fun Context.getCurrentLanguageCode(): String {
    val config = resources.configuration
    val language = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        config.locales[0].language
    } else {
        @Suppress("DEPRECATION")
        config.locale.language
    }
    return language.take(2).lowercase(Locale.ROOT)
}

