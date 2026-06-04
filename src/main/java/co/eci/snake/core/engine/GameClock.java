package co.eci.snake.core.engine;

import co.eci.snake.core.GameState;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reloj del juego que dispara un {@code tick} periódico para redibujar la pantalla.
 *
 * <p>Internamente usa un {@link ScheduledExecutorService} de un solo hilo.
 * El estado ({@link GameState}) se gestiona con un {@link AtomicReference}
 * para evitar condiciones de carrera al pausar/reanudar desde el EDT.</p>
 *
 * <p><b>Nota:</b> este reloj controla únicamente el repaint de la UI.
 * La pausa real de los {@code SnakeRunner} la gestiona {@code PauseBarrier}.</p>
 *
 * @author Juan Sebastian Guayazan Edilberto
 */
public final class GameClock implements AutoCloseable {

  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private final long periodMillis;
  private final Runnable tick;
  private final AtomicReference<GameState> state = new AtomicReference<>(GameState.STOPPED);

  /**
   * Crea el reloj con el período y la acción indicados.
   *
   * @param periodMillis intervalo entre ticks en milisegundos (debe ser &gt; 0)
   * @param tick         acción a ejecutar en cada tick (normalmente un repaint)
   * @throws IllegalArgumentException si {@code periodMillis} es ≤ 0
   * @throws NullPointerException     si {@code tick} es {@code null}
   */
  public GameClock(long periodMillis, Runnable tick) {
    if (periodMillis <= 0) throw new IllegalArgumentException("periodMillis must be > 0");
    this.periodMillis = periodMillis;
    this.tick = Objects.requireNonNull(tick, "tick");
  }

  /**
   * Arranca el scheduler. Solo tiene efecto la primera vez (estado STOPPED → RUNNING).
   * Llamadas posteriores son ignoradas.
   */
  public void start() {
    if (state.compareAndSet(GameState.STOPPED, GameState.RUNNING)) {
      scheduler.scheduleAtFixedRate(() -> {
        if (state.get() == GameState.RUNNING) tick.run();
      }, 0, periodMillis, TimeUnit.MILLISECONDS);
    }
  }

  /**
   * Pausa el reloj: el scheduler sigue activo pero el tick no se ejecuta.
   */
  public void pause()  { state.set(GameState.PAUSED); }

  /**
   * Reanuda el reloj después de una pausa.
   */
  public void resume() { state.set(GameState.RUNNING); }

  /**
   * Detiene el reloj (sin cerrar el scheduler).
   */
  public void stop()   { state.set(GameState.STOPPED); }

  /**
   * Libera el scheduler. Debe llamarse al cerrar la aplicación.
   */
  @Override
  public void close()  { scheduler.shutdownNow(); }
}
