package com.derekr.snake

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.derekr.snake.game.Cell
import com.derekr.snake.game.Direction
import com.derekr.snake.game.GameSettings
import com.derekr.snake.game.GameState
import com.derekr.snake.game.GameStatus
import com.derekr.snake.game.SnakeGame
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.LocalHapticsEnabled
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlin.math.abs

class GameScreen(private val sealedActivity: SealedLightActivity) :
    LightScreen<Unit, GameViewModel>(sealedActivity) {

    override val viewModelClass: Class<GameViewModel>
        get() = GameViewModel::class.java

    override fun createViewModel() = GameViewModel(
        prefs = SnakePreferences(lightContext.dataStore),
        vibrator = sealedActivity.vibrator,
    )

    @Composable
    override fun Content() {
        val state by viewModel.state.collectAsState()
        val best by viewModel.best.collectAsState()
        val newBest by viewModel.newBest.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()
        val hapticsEnabled = LocalHapticsEnabled.current
        SideEffect { viewModel.hapticsEnabled = hapticsEnabled }

        LightTheme(colors = themeColors) {
            GameContent(
                state = state,
                best = best,
                newBest = newBest,
                onBack = { goBack() },
                onPause = viewModel::togglePause,
                onSwipe = viewModel::onSwipe,
                onTap = viewModel::onTap,
            )
        }
    }
}

@Composable
private fun GameContent(
    state: GameState?,
    best: Int,
    newBest: Boolean,
    onBack: () -> Unit,
    onPause: () -> Unit,
    onSwipe: (Direction) -> Unit,
    onTap: () -> Unit,
) {
    val colors = LightThemeTokens.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        val status = state?.status
        LightTopBar(
            leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = onBack),
            center = LightTopBarCenter.Text(formatScore(state?.score ?: 0)),
            rightButton = when (status) {
                GameStatus.Running -> LightBarButton.Text("PAUSE", onClick = onPause)
                GameStatus.Paused -> LightBarButton.Text("PLAY", onClick = onPause)
                else -> null
            },
        )

        // The whole area under the top bar takes swipes, not just the board, so a thumb near
        // the edge still steers.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .swipeAndTap(onSwipe = onSwipe, onTap = onTap)
                .padding(horizontal = 1f.gridUnitsAsDp()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (state != null) {
                    Board(
                        state = state,
                        color = colors.content,
                        background = colors.background,
                        modifier = Modifier
                            .aspectRatio(state.columns.toFloat() / state.rows)
                            .border(2.dp, colors.content)
                            .padding(4.dp),
                    )
                }
            }
            LightText(
                text = statusLine(status, newBest, best),
                variant = LightTextVariant.Fine,
                lighten = status == GameStatus.Running,
                align = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.5f.gridUnitsAsDp()),
            )
        }
    }
}

private fun formatScore(score: Int) = score.toString().padStart(4, '0')

private fun statusLine(status: GameStatus?, newBest: Boolean, best: Int): String = when (status) {
    null, GameStatus.Ready -> "Swipe to start"
    GameStatus.Running -> if (best > 0) "Best ${formatScore(best)}" else " "
    GameStatus.Paused -> "Paused. Tap to resume"
    GameStatus.Over -> if (newBest) "New best! Tap to play again" else "Game over. Tap to play again"
    GameStatus.Won -> "You filled the board! Tap to play again"
}

private fun Modifier.swipeAndTap(onSwipe: (Direction) -> Unit, onTap: () -> Unit): Modifier =
    this
        .pointerInput(Unit) {
            val threshold = 20.dp.toPx()
            var drag = Offset.Zero
            detectDragGestures(
                onDragStart = { drag = Offset.Zero },
                onDrag = { change, amount ->
                    change.consume()
                    drag += amount
                    if (drag.getDistance() >= threshold) {
                        // One long drag can steer several times: emit a turn, then measure afresh.
                        onSwipe(
                            if (abs(drag.x) > abs(drag.y)) {
                                if (drag.x > 0) Direction.Right else Direction.Left
                            } else {
                                if (drag.y > 0) Direction.Down else Direction.Up
                            }
                        )
                        drag = Offset.Zero
                    }
                },
            )
        }
        .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }

@Composable
private fun Board(state: GameState, color: Color, background: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cell = size.width / state.columns
        drawSnake(state, cell, color, background)
        state.food?.let { drawFood(it, cell, color) }
    }
}

private fun DrawScope.drawSnake(state: GameState, cell: Float, color: Color, background: Color) {
    val gap = cell * 0.12f
    val body = state.body
    body.forEachIndexed { i, segment ->
        drawRect(
            color = color,
            topLeft = Offset(segment.x * cell + gap, segment.y * cell + gap),
            size = Size(cell - 2 * gap, cell - 2 * gap),
        )
        // Bridge to the next segment so the snake reads as one body rather than loose beads.
        val next = body.getOrNull(i + 1) ?: return@forEachIndexed
        val dx = next.x - segment.x
        val dy = next.y - segment.y
        if (abs(dx) + abs(dy) != 1) return@forEachIndexed // wrapped around the edge
        val left = minOf(segment.x, next.x) * cell + gap
        val top = minOf(segment.y, next.y) * cell + gap
        val bridge = if (dx != 0) {
            Size(cell * 2 - 2 * gap, cell - 2 * gap)
        } else {
            Size(cell - 2 * gap, cell * 2 - 2 * gap)
        }
        drawRect(color = color, topLeft = Offset(left, top), size = bridge)
    }
    drawEye(state.head, state.direction, cell, gap, background)
}

private fun DrawScope.drawEye(head: Cell, direction: Direction, cell: Float, gap: Float, color: Color) {
    val eye = cell * 0.18f
    val center = Offset(head.x * cell + cell / 2, head.y * cell + cell / 2)
    // Nudge the eye forward and to one side so it reads as a face.
    val forward = Offset(direction.dx.toFloat(), direction.dy.toFloat()) * (cell * 0.15f)
    val side = Offset(direction.dy.toFloat(), -direction.dx.toFloat()) * (cell * 0.15f)
    val eyeCenter = center + forward + side
    val inner = cell - 2 * gap
    if (inner <= eye) return
    drawRect(
        color = color,
        topLeft = eyeCenter - Offset(eye / 2, eye / 2),
        size = Size(eye, eye),
    )
}

private fun DrawScope.drawFood(food: Cell, cell: Float, color: Color) {
    val cx = food.x * cell + cell / 2
    val cy = food.y * cell + cell / 2
    val r = cell * 0.36f
    val diamond = Path().apply {
        moveTo(cx, cy - r)
        lineTo(cx + r, cy)
        lineTo(cx, cy + r)
        lineTo(cx - r, cy)
        close()
    }
    drawPath(diamond, color)
}

@Preview(widthDp = 1080 / 3, heightDp = 1240 / 3, showBackground = true)
@Composable
private fun GameContentPreview() {
    val game = SnakeGame()
    var state = game.start(game.newGame(GameSettings()))
    state = game.turn(state, Direction.Up)
    repeat(3) { state = game.step(state).first }
    LightTheme {
        GameContent(
            state = state.copy(score = 25),
            best = 120,
            newBest = false,
            onBack = {},
            onPause = {},
            onSwipe = {},
            onTap = {},
        )
    }
}
