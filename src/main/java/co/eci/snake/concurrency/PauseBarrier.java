package co.eci.snake.concurrency;

/**
 * Pause barrier based on Java's monitor pattern ({@code wait}/{@code notifyAll}).
 *
 * <p>Allows all {@link SnakeRunner} threads to be safely suspended and
 * resumed without busy-waiting. The mechanism works as follows:</p>
 * <ol>
 *   <li>Each runner calls {@link #awaitUnpaused()} at the start of each iteration.</li>
 *   <li>If the game is paused, {@code wait()} releases the monitor and puts
 *       the thread to sleep without consuming CPU.</li>
 *   <li>On resume, {@link #resume()} calls {@code notifyAll()} and all runners
 *       wake up and continue.</li>
 * </ol>
 *
 * <p>The barrier starts in <em>paused</em> state so that runners start blocked
 * until the player presses "Start".</p>
 *
 * <p><b>Spurious wakeups:</b> the pattern uses {@code while (paused)} instead of
 * {@code if (paused)} to guard against false wake-ups that the JVM may emit
 * for OS-level reasons.</p>
 *
 * @author Juan Sebastian Guayazan Edilberto
 */
public final class PauseBarrier {

  /** Pause state; {@code true} = runners blocked. */
  private boolean paused = true;

  /**
   * Blocks the calling thread while the game is paused.
   *
   * <p>Should be called at the start of each runner iteration. If the game is
   * running, returns immediately. If paused, the thread suspends with
   * {@code wait()} until someone calls {@link #resume()}.</p>
   *
   * @throws InterruptedException if the thread is interrupted while waiting
   */
  public synchronized void awaitUnpaused() throws InterruptedException {
    while (paused) {
      wait(); // releases the monitor and suspends the thread without burning CPU
    }
  }

  /**
   * Pauses the game. Runners will finish their current {@code step()} and then
   * block in {@link #awaitUnpaused()}.
   */
  public synchronized void pause() {
    paused = true;
  }

  /**
   * Resumes the game. Wakes all blocked runners with {@code notifyAll()}.
   *
   * <p>{@code notifyAll()} is used instead of {@code notify()} because multiple
   * runners may be in {@code wait()}; {@code notify()} would only wake one at
   * random, leaving the rest blocked indefinitely.</p>
   */
  public synchronized void resume() {
    paused = false;
    notifyAll();
  }

  /**
   * Returns whether the game is currently paused.
   *
   * @return {@code true} if paused
   */
  public synchronized boolean isPaused() {
    return paused;
  }
}
