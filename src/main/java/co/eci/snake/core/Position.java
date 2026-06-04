package co.eci.snake.core;

/**
 * Coordenada inmutable (x, y) en el tablero.
 *
 * <p>Al ser un {@code record}, es inmutable por definición: cada operación
 * devuelve una nueva instancia en lugar de modificar la existente, lo que
 * la hace segura para compartir entre hilos sin sincronización adicional.</p>
 *
 * @param x columna (0 = izquierda)
 * @param y fila   (0 = arriba)
 *
 * @author Juan Sebastian Guayazan Edilberto
 */
public record Position(int x, int y) {

  /**
   * Aplica wrap-around (efecto "Pac-Man") para mantener la posición
   * dentro de los límites del tablero.
   *
   * <p>Si la serpiente sale por un borde, reaparece por el borde opuesto.
   * Se usa módulo con signo positivo para manejar coordenadas negativas.</p>
   *
   * @param width  ancho del tablero en celdas
   * @param height alto  del tablero en celdas
   * @return nueva {@code Position} dentro de [0, width) × [0, height)
   */
  public Position wrap(int width, int height) {
    int nx = ((x % width)  + width)  % width;
    int ny = ((y % height) + height) % height;
    return new Position(nx, ny);
  }
}
