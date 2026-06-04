package co.eci.snake.app;

import co.eci.snake.ui.legacy.SnakeApp;

/**
 * Application entry point for Snake Race.
 *
 * <p>Delegates startup to {@link SnakeApp#launch()}, which creates the
 * Swing window on the Event Dispatch Thread (EDT).</p>
 *
 * @author Juan Sebastian Guayazan Edilberto
 */
public final class Main {

  private Main() {}

  /**
   * Main method. Launches the game's graphical interface.
   *
   * @param args command-line arguments (not used directly;
   *             use the system property {@code -Dsnakes=N} to set
   *             the number of snakes)
   */
  public static void main(String[] args) {
    SnakeApp.launch();
  }
}
