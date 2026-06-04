package co.eci.snake.concurrency;

import co.eci.snake.core.Board;
import co.eci.snake.core.Direction;
import co.eci.snake.core.Snake;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Runnable que gobierna el movimiento autónomo de una serpiente.
 *
 * <p>Cada instancia se ejecuta en su propio <em>virtual thread</em> de Java 21.
 * El bucle principal realiza estos pasos en cada iteración:</p>
 * <ol>
 *   <li>Bloquear en {@link PauseBarrier#awaitUnpaused()} si el juego está pausado
 *       (sin busy-wait: el hilo duerme con {@code wait()}).</li>
 *   <li>Girar aleatoriamente con probabilidad 10 % (5 % en turbo).</li>
 *   <li>Pedir al tablero que calcule y aplique el siguiente paso.</li>
 *   <li>Reaccionar al resultado: girar en obstáculo, activar turbo, o terminar al morir.</li>
 *   <li>Dormir {@code baseSleepMs} ms (o {@code turboSleepMs} ms en modo turbo).</li>
 * </ol>
 *
 * <p><b>Corrección aplicada:</b> el código original no consultaba el estado de pausa;
 * los runners seguían moviéndose aunque el reloj de repaint estuviera detenido.
 * Ahora {@link PauseBarrier#awaitUnpaused()} garantiza la suspensión real.</p>
 *
 * @author Juan Sebastian Guayazan Edilberto
 */
public final class SnakeRunner implements Runnable {

  private final Snake        snake;
  private final Board        board;
  private final PauseBarrier barrier;

  /** Lista de todas las serpientes; necesaria para la detección de colisión cruzada. */
  private final List<Snake>  allSnakes;

  /** Milisegundos de pausa entre pasos en velocidad normal. */
  private final int baseSleepMs  = 80;

  /** Milisegundos de pausa entre pasos en modo turbo. */
  private final int turboSleepMs = 40;

  /** Ticks restantes de modo turbo (0 = sin turbo). */
  private int turboTicks = 0;

  /**
   * Crea un runner para la serpiente indicada.
   *
   * @param snake     serpiente que controlará este runner
   * @param board     tablero compartido donde se calcula cada paso
   * @param barrier   barrera de pausa compartida entre todos los runners
   * @param allSnakes lista de todas las serpientes (para colisión cruzada)
   */
  public SnakeRunner(Snake snake, Board board, PauseBarrier barrier, List<Snake> allSnakes) {
    this.snake     = snake;
    this.board     = board;
    this.barrier   = barrier;
    this.allSnakes = allSnakes;
  }

  /**
   * Bucle principal del runner. Se ejecuta hasta que el hilo sea interrumpido
   * o la serpiente muera.
   */
  @Override
  public void run() {
    try {
      while (!Thread.currentThread().isInterrupted() && snake.isAlive()) {

        // Bloquea con wait() mientras el juego esté pausado (sin busy-wait).
        barrier.awaitUnpaused();

        // Verificación post-espera: pudo morir mientras estaba bloqueada.
        if (!snake.isAlive()) break;

        maybeTurn();

        var res = board.step(snake, allSnakes);
        if (res == Board.MoveResult.HIT_OBSTACLE) {
          randomTurn();
        } else if (res == Board.MoveResult.ATE_TURBO) {
          turboTicks = 100;
        } else if (res == Board.MoveResult.DIED) {
          return; // serpiente muerta → el runner termina limpiamente
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
   * Con probabilidad {@code p}, gira la serpiente en una dirección aleatoria.
   * La probabilidad se reduce en modo turbo para que la serpiente vaya más recto.
   */
  private void maybeTurn() {
    double p = (turboTicks > 0) ? 0.05 : 0.10;
    if (ThreadLocalRandom.current().nextDouble() < p) randomTurn();
  }

  /**
   * Gira la serpiente en una dirección aleatoria entre las cuatro posibles.
   * El método {@link Snake#turn} ignorará el giro si es un giro de 180°.
   */
  private void randomTurn() {
    var dirs = Direction.values();
    snake.turn(dirs[ThreadLocalRandom.current().nextInt(dirs.length)]);
  }
}
