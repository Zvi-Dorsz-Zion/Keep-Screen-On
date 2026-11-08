package com.keepscreenon.app

import android.content.Context

/** ההגדרות של המשתמש: מתג ראשי ורשימת האפליקציות שנבחרו. */
object Prefs {
    private const val FILE = "settings"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_SELECTED = "selected"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isEnabled(c: Context): Boolean = sp(c).getBoolean(KEY_ENABLED, true)

    fun setEnabled(c: Context, value: Boolean) {
        sp(c).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun selected(c: Context): Set<String> =
        sp(c).getStringSet(KEY_SELECTED, null)?.toSet() ?: emptySet()

    fun setSelected(c: Context, value: Set<String>) {
        sp(c).edit().putStringSet(KEY_SELECTED, HashSet(value)).apply()
    }

    /** אין טעם להחזיק שירות (והתראה קבועה) כשכבוי או כשלא נבחר כלום. */
    fun shouldRun(c: Context): Boolean = isEnabled(c) && selected(c).isNotEmpty()
}
