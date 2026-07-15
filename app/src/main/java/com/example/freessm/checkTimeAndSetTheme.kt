package com.example.freessm

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import java.util.Calendar

class SsmThemeHelper(private val context: Context) {

    /**
     * Проверяет текущее время на магнитоле и автоматически
     * переключает дневную/ночную тему оформления приложения.
     */
    fun checkTimeAndSetTheme() {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        // Например, с 8:00 до 20:00 — день, в остальное время — ночь
        if (currentHour in 8..19) {
            if (AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_NO) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        } else {
            if (AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_YES) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
        }
    }

    /**
     * Позволяет быстро проверить из кода, какая тема сейчас активна в системе
     */
    fun isNightModeActive(): Boolean {
        val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES
    }
}