package co.eci.snake.core;

/**
 * Movement directions for a snake on the board.
 *
 * <p>Each constant stores the per-step displacement in cells:
 * {@code dx} for the horizontal axis and {@code dy} for the vertical axis.
 * The Y axis grows downward (screen convention).</p>
 *
 * @author Juan Sebastian Guayazan Edilberto
 */
public enum Direction {

  /** Up: y decreases. */
  UP(0, -1),

  /** Down: y increases. */
  DOWN(0, 1),

  /** Left: x decreases. */
  LEFT(-1, 0),

  /** Right: x increases. */
  RIGHT(1, 0);

  /** Horizontal displacement per step (-1, 0 or 1). */
  public final int dx;

  /** Vertical displacement per step (-1, 0 or 1). */
  public final int dy;

  Direction(int dx, int dy) {
    this.dx = dx;
    this.dy = dy;
  }
}
