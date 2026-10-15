package com.salroumi.smscleaner.sms

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.provider.Telephony

/**
 * Android only lets the *default* SMS app delete messages. This helper switches
 * this app to default temporarily and helps the user switch back afterwards.
 */
object DefaultSmsHelper {

    private const val PREFS = "default_sms"
    private const val KEY_PREVIOUS = "previous_package"

    fun isDefault(context: Context): Boolean =
        Telephony.Sms.getDefaultSmsPackage(context) == context.packageName

    fun previousDefault(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PREVIOUS, null)

    /** Intent asking the user to make this app the default SMS app. */
    fun requestDefaultIntent(context: Context): Intent {
        val current = Telephony.Sms.getDefaultSmsPackage(context)
        if (current != null && current != context.packageName) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_PREVIOUS, current).apply()
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
        } else {
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
        }
    }

    /**
     * Intent to give the default SMS role back to the previous app.
     * On Android 10+ apps can't hand the role to another app, so the system
     * "Default apps" screen is opened instead.
     */
    fun restoreIntent(context: Context): Intent {
        val previous = previousDefault(context)
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && previous != null) {
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, previous)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
    }

    fun previousAppLabel(context: Context): String? {
        val pkg = previousDefault(context) ?: return null
        return try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
    }
}
