package com.derekr.snake

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewModelScope
import com.derekr.snake.game.GameSettings
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val prefs: SnakePreferences) : LightViewModel<Unit>() {

    val settings: StateFlow<GameSettings> =
        prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, GameSettings())

    val best: StateFlow<Int> = prefs.bestScore.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun cycleLevel() {
        val levels = GameSettings.LEVELS
        val next = if (settings.value.level >= levels.last) levels.first else settings.value.level + 1
        viewModelScope.launch { prefs.setLevel(next) }
    }

    fun toggleWalls() {
        viewModelScope.launch { prefs.setWalls(!settings.value.walls) }
    }
}

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) : LightScreen<Unit, HomeViewModel>(sealedActivity) {

    override val viewModelClass: Class<HomeViewModel>
        get() = HomeViewModel::class.java

    override fun createViewModel() = HomeViewModel(SnakePreferences(lightContext.dataStore))

    @Composable
    override fun Content() {
        val settings by viewModel.settings.collectAsState()
        val best by viewModel.best.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()

        LightTheme(colors = themeColors) {
            HomeContent(
                settings = settings,
                best = best,
                onPlay = { navigateTo(::GameScreen) },
                onLevel = viewModel::cycleLevel,
                onWalls = viewModel::toggleWalls,
            )
        }
    }
}

@Composable
private fun HomeContent(
    settings: GameSettings,
    best: Int,
    onPlay: () -> Unit,
    onLevel: () -> Unit,
    onWalls: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightThemeTokens.colors.background)
            .padding(horizontal = 2f.gridUnitsAsDp()),
    ) {
        Spacer(Modifier.weight(1f))
        LightText(text = "Snake", variant = LightTextVariant.Subtitle)
        Spacer(Modifier.padding(top = 2f.gridUnitsAsDp()))
        MenuRow(label = "Play", onClick = onPlay)
        MenuRow(label = "Level", value = settings.level.toString(), onClick = onLevel)
        MenuRow(label = "Walls", value = if (settings.walls) "On" else "Off", onClick = onWalls)
        Spacer(Modifier.weight(1f))
        LightText(
            text = if (best > 0) "Best  $best" else "No high score yet",
            variant = LightTextVariant.Fine,
            lighten = true,
            modifier = Modifier.padding(bottom = 2f.gridUnitsAsDp()),
        )
    }
}

@Composable
private fun MenuRow(label: String, value: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(vertical = 0.75f.gridUnitsAsDp()),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        LightText(text = label, variant = LightTextVariant.Copy)
        if (value != null) {
            LightText(text = value, variant = LightTextVariant.Copy, lighten = true)
        }
    }
}

@Preview(widthDp = 1080 / 3, heightDp = 1240 / 3, showBackground = true)
@Composable
private fun HomeContentPreview() {
    LightTheme {
        HomeContent(settings = GameSettings(), best = 42, onPlay = {}, onLevel = {}, onWalls = {})
    }
}
