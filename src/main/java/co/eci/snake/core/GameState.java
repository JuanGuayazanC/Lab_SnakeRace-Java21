package co.eci.snake.core;

/**
 * Estados posibles del juego.
 *
 * <ul>
 *   <li>{@link #STOPPED}  – el juego no ha arrancado todavía.</li>
 *   <li>{@link #RUNNING}  – los runners están activos y la pantalla se repinta.</li>
 *   <li>{@link #PAUSED}   – los runners están bloqueados en {@code PauseBarrier}
 *       y el repaint está detenido.</li>
 * </ul>
 *
 * @author Juan Sebastian Guayazan Edilberto
 */
public enum GameState {

  /** Estado inicial; ningún runner ha sido desbloqueado aún. */
  STOPPED,

  /** Juego en marcha; serpientes moviéndose y pantalla actualizándose. */
  RUNNING,

  /** Juego suspendido; runners bloqueados con {@code wait()}, sin consumir CPU. */
  PAUSED
}
