package co.eci.snake.concurrency;

/**
 * Barrera de pausa basada en el patrón monitor de Java ({@code wait}/{@code notifyAll}).
 *
 * <p>Permite suspender y reanudar de forma segura todos los {@link SnakeRunner}
 * sin incurrir en espera activa (busy-wait). El mecanismo funciona así:</p>
 * <ol>
 *   <li>Cada runner llama a {@link #awaitUnpaused()} al inicio de cada iteración.</li>
 *   <li>Si el juego está pausado, {@code wait()} libera el monitor y duerme el hilo
 *       sin consumir CPU.</li>
 *   <li>Al reanudar, {@link #resume()} llama {@code notifyAll()} y todos los runners
 *       se despiertan y continúan.</li>
 * </ol>
 *
 * <p>La barrera inicia en estado <em>pausado</em> para que los runners arranquen
 * bloqueados hasta que el jugador presione "Iniciar".</p>
 *
 * <p><b>Spurious wakeups:</b> el patrón usa {@code while (paused)} en lugar de
 * {@code if (paused)} para protegerse contra despertares falsos que la JVM
 * puede emitir por razones del sistema operativo.</p>
 *
 * @author Juan Sebastian Guayazan Edilberto
 */
public final class PauseBarrier {

  /** Estado de pausa; {@code true} = runners bloqueados. */
  private boolean paused = true;

  /**
   * Bloquea el hilo llamante mientras el juego esté en pausa.
   *
   * <p>Debe llamarse al inicio de cada iteración del runner. Si el juego está
   * corriendo, retorna inmediatamente. Si está pausado, el hilo se suspende con
   * {@code wait()} hasta que alguien llame {@link #resume()}.</p>
   *
   * @throws InterruptedException si el hilo es interrumpido mientras espera
   */
  public synchronized void awaitUnpaused() throws InterruptedException {
    while (paused) {
      wait(); // libera el monitor y suspende el hilo sin consumir CPU
    }
  }

  /**
   * Pausa el juego. Los runners terminarán su {@code step()} actual y luego
   * quedarán bloqueados en {@link #awaitUnpaused()}.
   */
  public synchronized void pause() {
    paused = true;
  }

  /**
   * Reanuda el juego. Despierta a todos los runners bloqueados con {@code notifyAll()}.
   *
   * <p>Se usa {@code notifyAll()} en lugar de {@code notify()} porque puede haber
   * múltiples runners en {@code wait()}; {@code notify()} solo despertaría uno
   * aleatorio, dejando los demás bloqueados indefinidamente.</p>
   */
  public synchronized void resume() {
    paused = false;
    notifyAll();
  }

  /**
   * Indica si el juego está actualmente pausado.
   *
   * @return {@code true} si está pausado
   */
  public synchronized boolean isPaused() {
    return paused;
  }
}
