package co.eci.snake.app;

import co.eci.snake.ui.legacy.SnakeApp;

/**
 * Punto de entrada de la aplicación Snake Race.
 *
 * <p>Delega el arranque a {@link SnakeApp#launch()}, que crea la ventana
 * Swing en el Event Dispatch Thread (EDT).</p>
 *
 * @author Juan Sebastian Guayazan Edilberto
 */
public final class Main {

  private Main() {}

  /**
   * Método principal. Lanza la interfaz gráfica del juego.
   *
   * @param args argumentos de línea de comandos (no utilizados directamente;
   *             usar la propiedad del sistema {@code -Dsnakes=N} para definir
   *             el número de serpientes)
   */
  public static void main(String[] args) {
    SnakeApp.launch();
  }
}
