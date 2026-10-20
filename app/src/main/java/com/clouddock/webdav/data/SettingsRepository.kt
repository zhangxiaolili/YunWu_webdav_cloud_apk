package com.clouddock.webdav.data

import android.content.Context
import android.content.SharedPreferences
import com.clouddock.webdav.data.model.AppSettings
import com.clouddock.webdav.data.model.SortKey
import com.clouddock.webdav.data.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        prefs.edit()
            .putString(KEY_THEME, updated.themeMode.name)
            .putBoolean(KEY_DYNAMIC, updated.dynamicColor)
            .putBoolean(KEY_GRID, updated.gridMode)
            .putString(KEY_SORT, updated.sortKey.name)
            .putBoolean(KEY_SORT_ASC, updated.sortAscending)
            .putString(KEY_LAST_ACCOUNT, updated.lastAccountId)
            .apply()
        _settings.value = updated
    }

    private fun load(): AppSettings {
        val theme = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name)!!)
        }.getOrDefault(ThemeMode.SYSTEM)
        val sort = runCatching {
            SortKey.valueOf(prefs.getString(KEY_SORT, SortKey.NAME.name)!!)
        }.getOrDefault(SortKey.NAME)
        return AppSettings(
            themeMode = theme,
            dynamicColor = prefs.getBoolean(KEY_DYNAMIC, false),
            gridMode = prefs.getBoolean(KEY_GRID, false),
            sortKey = sort,
            sortAscending = prefs.getBoolean(KEY_SORT_ASC, true),
            lastAccountId = prefs.getString(KEY_LAST_ACCOUNT, null)
        )
    }

    private companion object {
        const val PREFS_NAME = "clouddock_settings"
        const val KEY_THEME = "theme_mode"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_GRID = "grid_mode"
        const val KEY_SORT = "sort_key"
        const val KEY_SORT_ASC = "sort_asc"
        const val KEY_LAST_ACCOUNT = "last_account"
    }
}
