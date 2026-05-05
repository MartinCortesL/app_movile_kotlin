package com.example.proyecto

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "settings")

object DataStoreManager {

    private val TERMS_ACCEPTED = booleanPreferencesKey("terms_accepted")
    private val camera_accepted = booleanPreferencesKey("camara_permission")
    private val location_accepted = booleanPreferencesKey("location_accepted")

// terminos y condiciones aceptados
    fun isTermsAccepted(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[TERMS_ACCEPTED] ?: false
        }
    }
    suspend fun setTermsAccepted(context: Context) {
        context.dataStore.edit { preferences ->
            preferences[TERMS_ACCEPTED] = true
        }
    }

    //Permisos aceptados
    fun isPermissionsGranted(context: Context): Flow<Boolean>{
        return context.dataStore.data.map { preferences ->
            preferences[camera_accepted] ?: false
        }
    }
    suspend fun setPermissionsGranted(context: Context) {
        context.dataStore.edit { preferences ->
            preferences[camera_accepted] = true
        }
    }

    //Ubicación aceptada
    fun isLocacionaccepted(context: Context): Flow<Boolean>{
        return context.dataStore.data.map { preferences ->
            preferences[location_accepted] ?: false
        }
    }
    suspend fun setLocacionAccepted(context: Context) {
        context.dataStore.edit { preferences ->
            preferences[location_accepted] = true
        }
    }
}
