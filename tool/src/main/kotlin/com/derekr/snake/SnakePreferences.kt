package com.derekr.snake

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.derekr.snake.game.GameSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SnakePreferences(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<GameSettings> = dataStore.data.map { prefs ->
        GameSettings(
            level = (prefs[LEVEL] ?: GameSettings.DEFAULT_LEVEL).coerceIn(GameSettings.LEVELS),
            walls = prefs[WALLS] ?: true,
        )
    }

    val bestScore: Flow<Int> = dataStore.data.map { it[BEST] ?: 0 }

    suspend fun currentSettings(): GameSettings = settings.first()

    suspend fun setLevel(level: Int) {
        dataStore.edit { it[LEVEL] = level.coerceIn(GameSettings.LEVELS) }
    }

    suspend fun setWalls(walls: Boolean) {
        dataStore.edit { it[WALLS] = walls }
    }

    /** Returns true if [score] beat the stored best. */
    suspend fun recordScore(score: Int): Boolean {
        var isBest = false
        dataStore.edit { prefs ->
            if (score > (prefs[BEST] ?: 0)) {
                prefs[BEST] = score
                isBest = true
            }
        }
        return isBest
    }

    private companion object {
        val LEVEL = intPreferencesKey("snake_level")
        val WALLS = booleanPreferencesKey("snake_walls")
        val BEST = intPreferencesKey("snake_best")
    }
}
