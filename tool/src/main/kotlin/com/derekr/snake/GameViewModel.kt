package com.derekr.snake

import android.view.KeyEvent
import androidx.lifecycle.viewModelScope
import com.derekr.snake.game.Direction
import com.derekr.snake.game.GameState
import com.derekr.snake.game.GameStatus
import com.derekr.snake.game.SnakeGame
import com.derekr.snake.game.StepEvent
import com.thelightphone.sdk.LightVibrationUsage
import com.thelightphone.sdk.LightVibrationWaveform
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.LightVibrator
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.vibrationAmplitudesOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class GameViewModel(
    private val prefs: SnakePreferences,
    private val vibrator: LightVibrator,
    private val game: SnakeGame = SnakeGame(),
) : LightViewModel<Unit>() {

    private val _state = MutableStateFlow<GameState?>(null)
    val state: StateFlow<GameState?> = _state

    private val _newBest = MutableStateFlow(false)
    val newBest: StateFlow<Boolean> = _newBest

    val best: StateFlow<Int> = prefs.bestScore.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Mirrors the user's LightOS haptics preference; set from the screen's composition. */
    var hapticsEnabled: Boolean = false

    private var loop: Job? = null

    init {
        viewModelScope.launch { _state.value = game.newGame(prefs.currentSettings()) }
    }

    fun onSwipe(direction: Direction) {
        val current = _state.value ?: return
        val active = when (current.status) {
            GameStatus.Ready, GameStatus.Paused -> game.start(current)
            GameStatus.Running -> current
            GameStatus.Over, GameStatus.Won -> return
        }
        update(game.turn(active, direction))
    }

    /** Tap on the board: start, resume, or begin a new round after game over. */
    fun onTap() {
        val current = _state.value ?: return
        when (current.status) {
            GameStatus.Ready, GameStatus.Paused -> update(game.start(current))
            GameStatus.Over, GameStatus.Won -> restart()
            GameStatus.Running -> Unit
        }
    }

    fun togglePause() {
        val current = _state.value ?: return
        when (current.status) {
            GameStatus.Running -> update(game.pause(current))
            GameStatus.Paused -> update(game.start(current))
            else -> Unit
        }
    }

    private fun restart() {
        loop?.cancel()
        _newBest.value = false
        viewModelScope.launch { _state.value = game.newGame(prefs.currentSettings()) }
    }

    private fun update(next: GameState) {
        _state.value = next
        if (next.status == GameStatus.Running && loop?.isActive != true) {
            loop = viewModelScope.launch { runLoop() }
        }
    }

    private suspend fun runLoop() {
        while (true) {
            val before = _state.value ?: return
            if (before.status != GameStatus.Running) return
            delay(before.settings.tickMillis)
            // State may have been paused or replaced while we waited.
            val current = _state.value ?: return
            if (current.status != GameStatus.Running) return
            val (next, event) = game.step(current)
            _state.value = next
            when (event) {
                StepEvent.Ate -> buzz { vibrator.vibrate(18.milliseconds) }
                StepEvent.Died, StepEvent.Won -> {
                    buzz { vibrator.vibrate(GAME_OVER_BUZZ, LightVibrationUsage.Touch) }
                    _newBest.value = prefs.recordScore(next.score)
                    return
                }
                StepEvent.Moved, null -> Unit
            }
        }
    }

    private inline fun buzz(block: () -> Unit) {
        if (hapticsEnabled) block()
    }

    override fun onAppPause() {
        _state.value?.let { _state.value = game.pause(it) }
    }

    override fun onScreenHide(screen: SimpleLightScreen<Unit>) {
        _state.value?.let { _state.value = game.pause(it) }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val direction = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_W -> Direction.Up
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_S -> Direction.Down
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_A -> Direction.Left
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_D -> Direction.Right
            else -> null
        }
        if (direction != null) {
            onSwipe(direction)
            return true
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_SPACE -> {
                if (_state.value?.status == GameStatus.Running) togglePause() else onTap()
                true
            }
            else -> false
        }
    }

    override fun onCleared() {
        loop?.cancel()
        super.onCleared()
    }

    private companion object {
        val GAME_OVER_BUZZ = LightVibrationWaveform(
            durations = listOf(60.milliseconds, 60.milliseconds, 140.milliseconds),
            amplitudes = vibrationAmplitudesOf(255, 0, 200),
        )
    }
}
