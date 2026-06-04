package co.eci.snake.core;

/**
 * Direcciones de movimiento de una serpiente en el tablero.
 *
 * <p>Cada constante almacena el desplazamiento en celdas por paso:
 * {@code dx} para el eje horizontal y {@code dy} para el vertical.
 * El eje Y crece hacia abajo (convención de pantalla).</p>
 *
 * @author Juan Sebastian Guayazan Edilberto
 */
public enum Direction {

  /** Arriba: y disminuye. */
  UP(0, -1),

  /** Abajo: y aumenta. */
  DOWN(0, 1),

  /** Izquierda: x disminuye. */
  LEFT(-1, 0),

  /** Derecha: x aumenta. */
  RIGHT(1, 0);

  /** Desplazamiento horizontal por paso (-1, 0 o 1). */
  public final int dx;

  /** Desplazamiento vertical por paso (-1, 0 o 1). */
  public final int dy;

  Direction(int dx, int dy) {
    this.dx = dx;
    this.dy = dy;
  }
}
