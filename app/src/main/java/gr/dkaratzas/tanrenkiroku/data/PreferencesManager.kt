package gr.dkaratzas.tanrenkiroku.data

import android.content.Context
import androidx.core.content.edit

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class UnitSystem { KG, LB }

// Application settings manager with shared preferences
class PreferencesManager(context: Context) {
    private val prefs = context.getSharedPreferences("tanren_prefs", Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = ThemeMode.entries.firstOrNull { it.name == prefs.getString("theme_mode", null) }
            ?: ThemeMode.SYSTEM
        set(value) { prefs.edit { putString("theme_mode", value.name) } }

    var unitSystem: UnitSystem
        get() = UnitSystem.entries.firstOrNull { it.name == prefs.getString("unit_system", null) }
            ?: UnitSystem.KG
        set(value) { prefs.edit { putString("unit_system", value.name) } }

    var skipEmptyWorkouts: Boolean
        get() = prefs.getBoolean("skip_empty_workouts", false)
        set(value) { prefs.edit { putBoolean("skip_empty_workouts", value) } }
}
