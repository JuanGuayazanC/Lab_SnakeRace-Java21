package co.eci.snake.core;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Representa una serpiente en el tablero.
 *
 * <p>El cuerpo se almacena como un {@link ArrayDeque} donde el primer elemento
 * es la cabeza y el último es la cola. Todos los métodos que acceden al cuerpo
 * o al estado de vida están sincronizados sobre {@code this} para evitar
 * condiciones de carrera entre:</p>
 * <ul>
 *   <li>El {@code SnakeRunner} (escribe vía {@link #advance} y {@link #kill})</li>
 *   <li>El EDT de Swing (lee vía {@link #snapshot} para pintar)</li>
 *   <li>{@code Board.step()} (lee {@link #head} y {@link #direction},
 *       escribe {@link #advance})</li>
 * </ul>
 *
 * <p><b>Orden de bloqueo:</b> siempre {@code Board → Snake}. Nunca a la
 * inversa, por lo que no hay riesgo de deadlock.</p>
 *
 * @author Juan Sebastian Guayazan Edilberto
 */
public final class Snake {

  /**
   * Cuerpo de la serpiente. No es thread-safe por sí solo; todos los accesos
   * deben realizarse dentro de un bloque {@code synchronized(this)}.
   */
  private final Deque<Position> body = new ArrayDeque<>();

  /** Dirección actual. {@code volatile} garantiza visibilidad entre hilos. */
  private volatile Direction direction;

  /** Longitud máxima permitida del cuerpo; crece al comer ratones. */
  private int maxLength = 5;

  /** {@code true} mientras la serpiente esté viva. */
  private volatile boolean alive = true;

  /**
   * Marca de tiempo ({@link System#nanoTime()}) del momento de muerte.
   * Valor {@code -1} mientras la serpiente esté viva.
   */
  private volatile long diedAt = -1;

  /** Mayor longitud de cuerpo alcanzada durante la partida. */
  private int peakLength = 1;

  private Snake(Position start, Direction dir) {
    body.addFirst(start);
    this.direction = dir;
  }

  /**
   * Crea una serpiente en la posición y dirección indicadas.
   *
   * @param x   columna inicial
   * @param y   fila inicial
   * @param dir dirección inicial de movimiento
   * @return nueva instancia de {@code Snake}
   */
  public static Snake of(int x, int y, Direction dir) {
    return new Snake(new Position(x, y), dir);
  }

  /**
   * Devuelve la dirección actual de movimiento.
   *
   * @return dirección actual
   */
  public synchronized Direction direction() { return direction; }

  /**
   * Cambia la dirección de la serpiente si el giro es válido.
   *
   * <p>Se rechaza cualquier giro de 180° (dirección opuesta a la actual)
   * para evitar que la serpiente se mueva contra sí misma. El método está
   * sincronizado para que el input del jugador (EDT) y el giro aleatorio
   * del {@code SnakeRunner} no generen una condición de carrera en la
   * operación read-check-write sobre {@code direction}.</p>
   *
   * @param dir nueva dirección deseada
   */
  public synchronized void turn(Direction dir) {
    if (!alive) return;
    if ((direction == Direction.UP    && dir == Direction.DOWN)  ||
        (direction == Direction.DOWN  && dir == Direction.UP)    ||
        (direction == Direction.LEFT  && dir == Direction.RIGHT) ||
        (direction == Direction.RIGHT && dir == Direction.LEFT)) {
      return;
    }
    this.direction = dir;
  }

  /**
   * Devuelve la posición actual de la cabeza.
   *
   * @return posición de la cabeza
   */
  public synchronized Position head() { return body.peekFirst(); }

  /**
   * Devuelve una copia instantánea del cuerpo para uso externo (p. ej. renderizado).
   *
   * <p>La copia se realiza mientras se sostiene el monitor, garantizando
   * un snapshot consistente. El llamador puede iterar la copia libremente
   * sin riesgo de {@link java.util.ConcurrentModificationException}.</p>
   *
   * @return copia del deque de posiciones (cabeza en el primer elemento)
   */
  public synchronized Deque<Position> snapshot() { return new ArrayDeque<>(body); }

  /**
   * Avanza la serpiente añadiendo {@code newHead} como nueva cabeza.
   *
   * <p>Si {@code grow} es {@code true}, incrementa la longitud máxima (la serpiente
   * comió un ratón). Se elimina la cola si el cuerpo supera {@code maxLength}.</p>
   *
   * @param newHead posición a la que se mueve la cabeza
   * @param grow    {@code true} si la serpiente debe crecer
   */
  public synchronized void advance(Position newHead, boolean grow) {
    body.addFirst(newHead);
    if (grow) maxLength++;
    while (body.size() > maxLength) body.removeLast();
    if (body.size() > peakLength) peakLength = body.size();
  }

  /**
   * Comprueba si la posición {@code p} está ocupada por algún segmento del cuerpo.
   *
   * <p>Llamado desde {@code Board.step()} mientras se sostiene el monitor de
   * {@code Board}, de modo que el orden de bloqueo es siempre
   * {@code Board → Snake}, sin riesgo de deadlock.</p>
   *
   * @param p posición a verificar
   * @return {@code true} si {@code p} coincide con algún segmento del cuerpo
   */
  public synchronized boolean containsPosition(Position p) {
    return body.contains(p);
  }

  /**
   * Marca la serpiente como muerta registrando el instante de muerte.
   *
   * <p>La guarda {@code if (!alive) return} evita que dos hilos que detecten
   * la misma colisión simultáneamente ejecuten la muerte dos veces.</p>
   */
  public synchronized void kill() {
    if (!alive) return;
    alive = false;
    diedAt = System.nanoTime();
  }

  /**
   * Indica si la serpiente sigue viva.
   *
   * @return {@code true} si está viva
   */
  public boolean isAlive() { return alive; }

  /**
   * Devuelve el instante de muerte en nanosegundos ({@link System#nanoTime()}).
   *
   * @return instante de muerte, o {@code -1} si aún está viva
   */
  public long diedAt() { return diedAt; }

  /**
   * Devuelve la longitud máxima del cuerpo alcanzada durante la partida.
   *
   * @return longitud máxima histórica en segmentos
   */
  public synchronized int peakLength()    { return peakLength; }

  /**
   * Devuelve la longitud actual del cuerpo.
   *
   * @return número de segmentos actuales
   */
  public synchronized int currentLength() { return body.size(); }
}
