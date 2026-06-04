package co.eci.snake.core.engine;

import co.eci.snake.core.GameState;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Game clock that fires a periodic {@code tick} to repaint the screen.
 *
 * <p>Internally uses a single-thread {@link ScheduledExecutorService}.
 * State ({@link GameState}) is managed with an {@link AtomicReference}
 * to avoid race conditions when pausing/resuming from the EDT.</p>
 *
 * <p><b>Note:</b> this clock only controls UI repainting.
 * The actual pause of the {@code SnakeRunner} threads is handled by
 * {@code PauseBarrier}.</p>
 *
 * @author Juan Sebastian Guayazan Edilberto
 */
public final class GameClock implements AutoCloseable {

  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private final long periodMillis;
  private final Runnable tick;
  private final AtomicReference<GameState> state = new AtomicReference<>(GameState.STOPPED);

  /**
   * Creates the clock with the given period and tick action.
   *
   * @param periodMillis interval between ticks in milliseconds (must be &gt; 0)
   * @param tick         action to execute on each tick (typically a repaint)
   * @throws IllegalArgumentException if {@code periodMillis} is ≤ 0
   * @throws NullPointerException     if {@code tick} is {@code null}
   */
  public GameClock(long periodMillis, Runnable tick) {
    if (periodMillis <= 0) throw new IllegalArgumentException("periodMillis must be > 0");
    this.periodMillis = periodMillis;
    this.tick = Objects.requireNonNull(tick, "tick");
  }

  /**
   * Starts the scheduler. Only takes effect the first time (STOPPED → RUNNING).
   * Subsequent calls are ignored.
   */
  public void start() {
    if (state.compareAndSet(GameState.STOPPED, GameState.RUNNING)) {
      scheduler.scheduleAtFixedRate(() -> {
        if (state.get() == GameState.RUNNING) tick.run();
      }, 0, periodMillis, TimeUnit.MILLISECONDS);
    }
  }

  /**
   * Pauses the clock: the scheduler keeps running but the tick is not executed.
   */
  public void pause()  { state.set(GameState.PAUSED); }

  /**
   * Resumes the clock after a pause.
   */
  public void resume() { state.set(GameState.RUNNING); }

  /**
   * Stops the clock (without shutting down the scheduler).
   */
  public void stop()   { state.set(GameState.STOPPED); }

  /**
   * Shuts down the scheduler. Should be called when the application closes.
   */
  @Override
  public void close()  { scheduler.shutdownNow(); }
}
