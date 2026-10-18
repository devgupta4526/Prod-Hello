package com.example.romear.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settings by preferencesDataStore("hello_settings")

@Singleton
class OnboardingPreferences @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun preferredAvailability(value: String) {
        require(value in setOf("OFF", "OPEN", "BROWSING"))
        context.settings.edit { it[stringPreferencesKey("preferred_availability")] = value }
    }
}
