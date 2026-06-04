package co.eci.snake.core;

/**
 * Immutable coordinate (x, y) on the board.
 *
 * <p>Being a {@code record}, it is immutable by definition: every operation
 * returns a new instance instead of modifying the existing one, making it
 * safe to share between threads without additional synchronization.</p>
 *
 * @param x column (0 = left)
 * @param y row    (0 = top)
 *
 * @author Juan Sebastian Guayazan Edilberto
 */
public record Position(int x, int y) {

  /**
   * Applies wrap-around (Pac-Man effect) to keep the position
   * within the board boundaries.
   *
   * <p>If a snake exits through one edge, it reappears on the opposite edge.
   * Signed modulo is used to handle negative coordinates correctly.</p>
   *
   * @param width  board width in cells
   * @param height board height in cells
   * @return new {@code Position} within [0, width) × [0, height)
   */
  public Position wrap(int width, int height) {
    int nx = ((x % width)  + width)  % width;
    int ny = ((y % height) + height) % height;
    return new Position(nx, ny);
  }
}
