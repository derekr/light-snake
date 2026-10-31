package com.derekr.snake.game

import kotlin.random.Random

enum class Direction(val dx: Int, val dy: Int) {
    Up(0, -1),
    Down(0, 1),
    Left(-1, 0),
    Right(1, 0);

    val opposite: Direction
        get() = when (this) {
            Up -> Down
            Down -> Up
            Left -> Right
            Right -> Left
        }
}

data class Cell(val x: Int, val y: Int)

enum class GameStatus { Ready, Running, Paused, Over, Won }

data class GameSettings(
    val level: Int = DEFAULT_LEVEL,
    val walls: Boolean = true,
) {
    init {
        require(level in LEVELS) { "level must be in $LEVELS" }
    }

    /** Milliseconds between moves. Level 1 is a stroll, level 9 is frantic. */
    val tickMillis: Long get() = 380L - (level - 1) * 36L

    val pointsPerFood: Int get() = level

    companion object {
        val LEVELS = 1..9
        const val DEFAULT_LEVEL = 5
    }
}

/**
 * Immutable snapshot of a game. [body] is ordered head first.
 * [queuedTurns] buffers quick successive swipes so a fast "up, left" isn't lost between ticks.
 */
data class GameState(
    val columns: Int,
    val rows: Int,
    val settings: GameSettings,
    val body: List<Cell>,
    val direction: Direction,
    val food: Cell?,
    val score: Int = 0,
    val status: GameStatus = GameStatus.Ready,
    val queuedTurns: List<Direction> = emptyList(),
) {
    val head: Cell get() = body.first()
    val length: Int get() = body.size
}

/** What happened during a [SnakeGame.step], so the UI can react (haptics, saving a high score). */
enum class StepEvent { Moved, Ate, Died, Won }

class SnakeGame(
    val columns: Int = DEFAULT_COLUMNS,
    val rows: Int = DEFAULT_ROWS,
    private val random: Random = Random.Default,
) {
    init {
        require(columns >= MIN_SIZE && rows >= MIN_SIZE) { "board must be at least ${MIN_SIZE}x$MIN_SIZE" }
    }

    fun newGame(settings: GameSettings): GameState {
        val y = rows / 2
        val headX = columns / 2
        val body = List(START_LENGTH) { i -> Cell(headX - i, y) }
        val state = GameState(
            columns = columns,
            rows = rows,
            settings = settings,
            body = body,
            direction = Direction.Right,
            food = null,
        )
        return state.copy(food = placeFood(state.body))
    }

    /**
     * Queues a turn. Turns that reverse into the snake or repeat the current heading are dropped,
     * judged against the last queued heading so two quick swipes behave as the player expects.
     */
    fun turn(state: GameState, direction: Direction): GameState {
        if (state.status != GameStatus.Running && state.status != GameStatus.Ready) return state
        val heading = state.queuedTurns.lastOrNull() ?: state.direction
        if (direction == heading || direction == heading.opposite) return state
        if (state.queuedTurns.size >= MAX_QUEUED_TURNS) return state
        return state.copy(queuedTurns = state.queuedTurns + direction)
    }

    fun start(state: GameState): GameState = when (state.status) {
        GameStatus.Ready, GameStatus.Paused -> state.copy(status = GameStatus.Running)
        else -> state
    }

    fun pause(state: GameState): GameState =
        if (state.status == GameStatus.Running) state.copy(status = GameStatus.Paused) else state

    fun step(state: GameState): Pair<GameState, StepEvent?> {
        if (state.status != GameStatus.Running) return state to null

        val direction = state.queuedTurns.firstOrNull() ?: state.direction
        val remainingTurns = state.queuedTurns.drop(1)
        val next = advance(state.head, direction, state.settings.walls)
            ?: return state.copy(status = GameStatus.Over, queuedTurns = emptyList()) to StepEvent.Died

        val eating = next == state.food
        // The tail moves out of the way this tick unless we're growing, so it's safe to move into.
        val obstacles = if (eating) state.body else state.body.dropLast(1)
        if (next in obstacles) {
            return state.copy(status = GameStatus.Over, direction = direction, queuedTurns = emptyList()) to StepEvent.Died
        }

        val body = listOf(next) + obstacles
        if (!eating) {
            return state.copy(body = body, direction = direction, queuedTurns = remainingTurns) to StepEvent.Moved
        }

        val score = state.score + state.settings.pointsPerFood
        val food = placeFood(body)
        val moved = state.copy(
            body = body,
            direction = direction,
            queuedTurns = remainingTurns,
            score = score,
            food = food,
        )
        return if (food == null) {
            moved.copy(status = GameStatus.Won) to StepEvent.Won
        } else {
            moved to StepEvent.Ate
        }
    }

    private fun advance(from: Cell, direction: Direction, walls: Boolean): Cell? {
        val x = from.x + direction.dx
        val y = from.y + direction.dy
        if (x in 0 until columns && y in 0 until rows) return Cell(x, y)
        if (walls) return null
        return Cell(Math.floorMod(x, columns), Math.floorMod(y, rows))
    }

    private fun placeFood(body: List<Cell>): Cell? {
        val occupied = body.toHashSet()
        val free = (0 until rows).flatMap { y -> (0 until columns).map { x -> Cell(x, y) } }
            .filterNot { it in occupied }
        return if (free.isEmpty()) null else free[random.nextInt(free.size)]
    }

    companion object {
        const val DEFAULT_COLUMNS = 17
        const val DEFAULT_ROWS = 19
        const val START_LENGTH = 4
        const val MAX_QUEUED_TURNS = 2
        private const val MIN_SIZE = 6
    }
}
