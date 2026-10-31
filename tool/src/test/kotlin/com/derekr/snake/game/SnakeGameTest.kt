package com.derekr.snake.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SnakeGameTest {
    private val game = SnakeGame(columns = 10, rows = 10, random = Random(1))

    private fun running(settings: GameSettings = GameSettings()) = game.start(game.newGame(settings))

    private fun GameState.withFood(cell: Cell?) = copy(food = cell)

    @Test
    fun newGameStartsCenteredHeadingRightWithFoodOffTheSnake() {
        val state = game.newGame(GameSettings())
        assertEquals(Cell(5, 5), state.head)
        assertEquals(SnakeGame.START_LENGTH, state.length)
        assertEquals(Direction.Right, state.direction)
        assertEquals(GameStatus.Ready, state.status)
        val food = assertNotNull(state.food)
        assertFalse(food in state.body)
    }

    @Test
    fun stepDoesNothingUntilStarted() {
        val state = game.newGame(GameSettings())
        val (next, event) = game.step(state)
        assertEquals(state, next)
        assertNull(event)
    }

    @Test
    fun stepMovesHeadAndKeepsLength() {
        val state = running().withFood(Cell(0, 0))
        val (next, event) = game.step(state)
        assertEquals(StepEvent.Moved, event)
        assertEquals(Cell(6, 5), next.head)
        assertEquals(state.length, next.length)
    }

    @Test
    fun eatingGrowsSnakeAndScoresByLevel() {
        val state = running(GameSettings(level = 7)).withFood(Cell(6, 5))
        val (next, event) = game.step(state)
        assertEquals(StepEvent.Ate, event)
        assertEquals(state.length + 1, next.length)
        assertEquals(7, next.score)
        val food = assertNotNull(next.food)
        assertFalse(food in next.body)
    }

    @Test
    fun reversingIsIgnored() {
        val state = game.turn(running(), Direction.Left)
        assertTrue(state.queuedTurns.isEmpty())
    }

    @Test
    fun quickTurnsAreQueuedAndAppliedOnePerTick() {
        var state = running().withFood(Cell(0, 0))
        state = game.turn(state, Direction.Up)
        state = game.turn(state, Direction.Left)
        assertEquals(listOf(Direction.Up, Direction.Left), state.queuedTurns)

        state = game.step(state).first
        assertEquals(Cell(5, 4), state.head)
        state = game.step(state).first
        assertEquals(Cell(4, 4), state.head)
        assertEquals(Direction.Left, state.direction)
    }

    @Test
    fun secondQueuedTurnCannotReverseTheFirst() {
        var state = game.turn(running(), Direction.Up)
        state = game.turn(state, Direction.Down)
        assertEquals(listOf(Direction.Up), state.queuedTurns)
    }

    @Test
    fun hittingWallEndsGame() {
        var state = running(GameSettings(walls = true)).withFood(Cell(0, 0))
        repeat(4) { state = game.step(state).first }
        assertEquals(Cell(9, 5), state.head)
        val (over, event) = game.step(state)
        assertEquals(StepEvent.Died, event)
        assertEquals(GameStatus.Over, over.status)
    }

    @Test
    fun withoutWallsSnakeWrapsAround() {
        var state = running(GameSettings(walls = false)).withFood(Cell(0, 0))
        repeat(5) { state = game.step(state).first }
        assertEquals(GameStatus.Running, state.status)
        assertEquals(Cell(0, 5), state.head)
    }

    @Test
    fun runningIntoOwnBodyEndsGame() {
        val body = listOf(Cell(5, 5), Cell(4, 5), Cell(4, 4), Cell(5, 4), Cell(6, 4), Cell(6, 5), Cell(6, 6))
        val state = running().copy(body = body, direction = Direction.Down, food = Cell(0, 0))
        val curled = game.turn(state, Direction.Right)
        val (over, event) = game.step(curled)
        assertEquals(StepEvent.Died, event)
        assertEquals(GameStatus.Over, over.status)
    }

    @Test
    fun movingIntoTheVacatingTailIsAllowed() {
        // A 2x2 loop: head chases its own tail.
        val body = listOf(Cell(5, 5), Cell(5, 6), Cell(4, 6), Cell(4, 5))
        val state = running().copy(body = body, direction = Direction.Up, food = Cell(0, 0))
        val (next, event) = game.step(game.turn(state, Direction.Left))
        assertEquals(StepEvent.Moved, event)
        assertEquals(Cell(4, 5), next.head)
    }

    @Test
    fun fillingTheBoardWins() {
        val tiny = SnakeGame(columns = 6, rows = 6, random = Random(3))
        // Snake snakes through every cell except the last one, which holds the food.
        val path = (0 until 6).flatMap { y ->
            val xs = if (y % 2 == 0) (0 until 6) else (5 downTo 0)
            xs.map { x -> Cell(x, y) }
        }
        val body = path.dropLast(1).reversed()
        val state = tiny.start(tiny.newGame(GameSettings())).copy(
            body = body,
            direction = Direction.Left,
            food = path.last(),
        )
        val (won, event) = tiny.step(state)
        assertEquals(StepEvent.Won, event)
        assertEquals(GameStatus.Won, won.status)
        assertNull(won.food)
    }

    @Test
    fun pauseAndResume() {
        val paused = game.pause(running())
        assertEquals(GameStatus.Paused, paused.status)
        assertNull(game.step(paused).second)
        assertEquals(GameStatus.Running, game.start(paused).status)
    }

    @Test
    fun higherLevelsTickFaster() {
        val ticks = GameSettings.LEVELS.map { GameSettings(level = it).tickMillis }
        assertEquals(ticks.sortedDescending(), ticks)
        assertTrue(ticks.last() >= 60)
    }
}
