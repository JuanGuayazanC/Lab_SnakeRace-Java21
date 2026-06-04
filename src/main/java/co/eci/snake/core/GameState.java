package co.eci.snake.core;

/**
 * Possible states of the game.
 *
 * <ul>
 *   <li>{@link #STOPPED} – the game has not started yet.</li>
 *   <li>{@link #RUNNING} – runners are active and the screen is being repainted.</li>
 *   <li>{@link #PAUSED}  – runners are blocked in {@code PauseBarrier}
 *       and the repaint is stopped.</li>
 * </ul>
 *
 * @author Juan Sebastian Guayazan Edilberto
 */
public enum GameState {

  /** Initial state; no runner has been unblocked yet. */
  STOPPED,

  /** Game in progress; snakes moving and screen updating. */
  RUNNING,

  /** Game suspended; runners blocked with {@code wait()}, not consuming CPU. */
  PAUSED
}
