package co.eci.snake.concurrency;

import co.eci.snake.core.Board;
import co.eci.snake.core.Direction;
import co.eci.snake.core.Snake;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Runnable that governs the autonomous movement of a snake.
 *
 * <p>Each instance runs in its own Java 21 <em>virtual thread</em>.
 * The main loop performs these steps on each iteration:</p>
 * <ol>
 *   <li>Block on {@link PauseBarrier#awaitUnpaused()} if the game is paused
 *       (no busy-wait: the thread sleeps with {@code wait()}).</li>
 *   <li>Randomly turn with 10% probability (5% in turbo mode).</li>
 *   <li>Ask the board to compute and apply the next step.</li>
 *   <li>React to the result: turn on obstacle, activate turbo, or stop on death.</li>
 *   <li>Sleep {@code baseSleepMs} ms (or {@code turboSleepMs} ms in turbo mode).</li>
 * </ol>
 *
 * <p><b>Fix applied:</b> the original code did not check the pause state;
 * runners kept moving even when the repaint clock was stopped.
 * Now {@link PauseBarrier#awaitUnpaused()} guarantees real suspension.</p>
 *
 * @author Juan Sebastian Guayazan Edilberto
 */
public final class SnakeRunner implements Runnable {

  private final Snake        snake;
  private final Board        board;
  private final PauseBarrier barrier;

  /** Full snake list; needed for cross-collision detection. */
  private final List<Snake>  allSnakes;

  /** Milliseconds to wait between steps at normal speed. */
  private final int baseSleepMs  = 80;

  /** Milliseconds to wait between steps in turbo mode. */
  private final int turboSleepMs = 40;

  /** Remaining turbo ticks (0 = no turbo). */
  private int turboTicks = 0;

  /**
   * Creates a runner for the given snake.
   *
   * @param snake     snake this runner will control
   * @param board     shared board where each step is computed
   * @param barrier   pause barrier shared across all runners
   * @param allSnakes list of all snakes (for cross-collision detection)
   */
  public SnakeRunner(Snake snake, Board board, PauseBarrier barrier, List<Snake> allSnakes) {
    this.snake     = snake;
    this.board     = board;
    this.barrier   = barrier;
    this.allSnakes = allSnakes;
  }

  /**
   * Main runner loop. Runs until the thread is interrupted or the snake dies.
   */
  @Override
  public void run() {
    try {
      while (!Thread.currentThread().isInterrupted() && snake.isAlive()) {

        // Block with wait() while the game is paused (no busy-wait).
        barrier.awaitUnpaused();

        // Post-wait check: the snake may have died while blocked.
        if (!snake.isAlive()) break;

        maybeTurn();

        var res = board.step(snake, allSnakes);
        if (res == Board.MoveResult.HIT_OBSTACLE) {
          randomTurn();
        } else if (res == Board.MoveResult.ATE_TURBO) {
          turboTicks = 100;
        } else if (res == Board.MoveResult.DIED) {
          return; // snake dead → runner exits cleanly
        }

        int sleep = (turboTicks > 0) ? turboSleepMs : baseSleepMs;
        if (turboTicks > 0) turboTicks--;
        Thread.sleep(sleep);
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * With probability {@code p}, turns the snake in a random direction.
   * The probability is lower in turbo mode so the snake travels straighter.
   */
  private void maybeTurn() {
    double p = (turboTicks > 0) ? 0.05 : 0.10;
    if (ThreadLocalRandom.current().nextDouble() < p) randomTurn();
  }

  /**
   * Turns the snake in a random direction among the four possible ones.
   * {@link Snake#turn} will ignore the turn if it is a 180° reversal.
   */
  private void randomTurn() {
    var dirs = Direction.values();
    snake.turn(dirs[ThreadLocalRandom.current().nextInt(dirs.length)]);
  }
}
