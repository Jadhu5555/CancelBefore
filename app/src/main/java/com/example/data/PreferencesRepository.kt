package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cancelbefore_prefs")

class PreferencesRepository(private val context: Context) {

    private val KEY_THEME = stringPreferencesKey("app_theme")
    private val KEY_CURRENCY = stringPreferencesKey("preferred_currency")
    private val KEY_DEFAULT_REMINDERS = stringPreferencesKey("default_reminder_offsets")
    private val KEY_NOTIF_LISTENER_ENABLED = booleanPreferencesKey("notif_listener_enabled")
    private val KEY_EMAIL_SYNC_ENABLED = booleanPreferencesKey("email_sync_enabled")
    private val KEY_MONITORED_PACKAGES = stringPreferencesKey("monitored_packages")
    private val KEY_IGNORED_PACKAGES = stringPreferencesKey("ignored_packages")
    private val KEY_AUTO_PURGE_DAYS = stringPreferencesKey("auto_purge_days")
    private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

    val themeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME] ?: "SYSTEM"
    }

    val currencyFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_CURRENCY] ?: "USD"
    }

    val defaultRemindersFlow: Flow<List<Int>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_DEFAULT_REMINDERS] ?: "7,3,1,0"
        raw.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    val notifListenerEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_NOTIF_LISTENER_ENABLED] ?: false
    }

    val emailSyncEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_EMAIL_SYNC_ENABLED] ?: false
    }

    val monitoredPackagesFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_MONITORED_PACKAGES] ?: DEFAULT_MONITORED_PACKAGES
        raw.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }

    val ignoredPackagesFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_IGNORED_PACKAGES] ?: ""
        raw.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }

    val autoPurgeDaysFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_PURGE_DAYS]?.toIntOrNull() ?: 30
    }

    val onboardingCompletedFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDING_COMPLETED] ?: false
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { it[KEY_THEME] = theme }
    }

    suspend fun setCurrency(currency: String) {
        context.dataStore.edit { it[KEY_CURRENCY] = currency }
    }

    suspend fun setDefaultReminders(offsets: List<Int>) {
        context.dataStore.edit { it[KEY_DEFAULT_REMINDERS] = offsets.joinToString(",") }
    }

    suspend fun setNotifListenerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIF_LISTENER_ENABLED] = enabled }
    }

    suspend fun setEmailSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_EMAIL_SYNC_ENABLED] = enabled }
    }

    suspend fun setMonitoredPackages(packages: Set<String>) {
        context.dataStore.edit { it[KEY_MONITORED_PACKAGES] = packages.joinToString(",") }
    }

    suspend fun setIgnoredPackages(packages: Set<String>) {
        context.dataStore.edit { it[KEY_IGNORED_PACKAGES] = packages.joinToString(",") }
    }

    suspend fun setAutoPurgeDays(days: Int) {
        context.dataStore.edit { it[KEY_AUTO_PURGE_DAYS] = days.toString() }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = completed }
    }

    companion object {
        const val DEFAULT_MONITORED_PACKAGES =
            "com.google.android.apps.nbu.paisa.user," + // Google Pay
            "net.one97.paytm," + // Paytm
            "com.phonepe.app," + // PhonePe
            "com.dreamplug.androidapp," + // CRED
            "com.amazon.mShop.android.shopping," + // Amazon
            "com.netflix.mediaclient," + // Netflix
            "com.spotify.music," + // Spotify
            "com.android.vending" // Google Play Store
    }
}
