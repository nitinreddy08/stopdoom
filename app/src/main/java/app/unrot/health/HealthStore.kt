package app.unrot.health

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Service health timestamps (SPEC §7.1): connect/disconnect times and the 60 s heartbeat. */
class HealthStore(private val dataStore: DataStore<Preferences>) {
    private val data: Flow<Preferences> = dataStore.data.catch { emit(emptyPreferences()) }

    val connectedAt: Flow<Long?> = data.map { it[CONNECTED_AT] }
    val disconnectedAt: Flow<Long?> = data.map { it[DISCONNECTED_AT] }
    val lastAliveAt: Flow<Long?> = data.map { it[LAST_ALIVE_AT] }

    suspend fun recordConnected(nowMs: Long) = write(CONNECTED_AT, nowMs)

    suspend fun recordDisconnected(nowMs: Long) = write(DISCONNECTED_AT, nowMs)

    suspend fun recordAlive(nowMs: Long) = write(LAST_ALIVE_AT, nowMs)

    private suspend fun write(key: Preferences.Key<Long>, value: Long) {
        dataStore.edit { it[key] = value }
    }

    private companion object {
        val CONNECTED_AT = longPreferencesKey("service_connected_at")
        val DISCONNECTED_AT = longPreferencesKey("service_disconnected_at")
        val LAST_ALIVE_AT = longPreferencesKey("last_alive_at")
    }
}
