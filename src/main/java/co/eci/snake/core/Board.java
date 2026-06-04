package co.eci.snake.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Tablero de juego. Contiene y gestiona todos los elementos del escenario:
 * ratones, obstáculos, ítems turbo y teletransportadores.
 *
 * <h2>Seguridad en concurrencia</h2>
 * <p>Las colecciones internas ({@code mice}, {@code obstacles}, {@code turbo},
 * {@code teleports}) son {@link HashSet}/{@link HashMap} estándar, que no son
 * thread-safe. Todas las lecturas y escrituras sobre ellas ocurren dentro del
 * método {@link #step}, que está declarado {@code synchronized} sobre {@code this}.
 * Los getters públicos ({@link #mice()}, etc.) también son {@code synchronized}
 * y devuelven copias defensivas.</p>
 *
 * <h2>Orden de bloqueo</h2>
 * <p>Dentro de {@link #step} se llaman métodos {@code synchronized} de {@link Snake}.
 * El orden de adquisición es siempre {@code Board → Snake}; nunca a la inversa,
 * por lo que no hay riesgo de deadlock.</p>
 *
 * @author Juan Sebastian Guayazan Edilberto
 */
public final class Board {

  private final int width;
  private final int height;

  private final Set<Position>          mice      = new HashSet<>();
  private final Set<Position>          obstacles = new HashSet<>();
  private final Set<Position>          turbo     = new HashSet<>();
  private final Map<Position,Position> teleports = new HashMap<>();

  /**
   * Resultado de un paso de movimiento calculado por {@link #step}.
   */
  public enum MoveResult {
    /** La serpiente se movió sin ningún evento especial. */
    MOVED,
    /** La serpiente comió un ratón y creció. */
    ATE_MOUSE,
    /** La cabeza chocó con un obstáculo; el runner debe girar aleatoriamente. */
    HIT_OBSTACLE,
    /** La serpiente pisó un ítem turbo; su velocidad se duplica temporalmente. */
    ATE_TURBO,
    /** La serpiente entró en un teletransportador y salió por el portal par. */
    TELEPORTED,
    /** La serpiente murió por auto-colisión o colisión cruzada. */
    DIED
  }

  /**
   * Crea un tablero de las dimensiones indicadas e inicializa aleatoriamente
   * ratones, obstáculos, ítems turbo y pares de teletransportadores.
   *
   * @param width  ancho en celdas (debe ser &gt; 0)
   * @param height alto  en celdas (debe ser &gt; 0)
   * @throws IllegalArgumentException si alguna dimensión es ≤ 0
   */
  public Board(int width, int height) {
    if (width <= 0 || height <= 0) throw new IllegalArgumentException("Board dimensions must be positive");
    this.width  = width;
    this.height = height;
    for (int i = 0; i < 6; i++) mice.add(randomEmpty());
    for (int i = 0; i < 4; i++) obstacles.add(randomEmpty());
    for (int i = 0; i < 3; i++) turbo.add(randomEmpty());
    createTeleportPairs(2);
  }

  /** @return ancho del tablero en celdas */
  public int width()  { return width; }

  /** @return alto del tablero en celdas */
  public int height() { return height; }

  /**
   * Devuelve una copia de las posiciones de los ratones.
   *
   * @return conjunto de posiciones con ratones
   */
  public synchronized Set<Position> mice()      { return new HashSet<>(mice); }

  /**
   * Devuelve una copia de las posiciones de los obstáculos.
   *
   * @return conjunto de posiciones con obstáculos
   */
  public synchronized Set<Position> obstacles() { return new HashSet<>(obstacles); }

  /**
   * Devuelve una copia de las posiciones de los ítems turbo.
   *
   * @return conjunto de posiciones con turbo
   */
  public synchronized Set<Position> turbo()     { return new HashSet<>(turbo); }

  /**
   * Devuelve una copia del mapa de teletransportadores.
   *
   * @return mapa origen → destino de cada portal
   */
  public synchronized Map<Position,Position> teleports() { return new HashMap<>(teleports); }

  /**
   * Calcula y aplica un paso de movimiento para {@code snake}.
   *
   * <p>El método está sincronizado en {@code Board}, por lo que solo un
   * {@code SnakeRunner} puede ejecutarlo a la vez. El orden de eventos es:</p>
   * <ol>
   *   <li>Calcular la posición siguiente con wrap-around.</li>
   *   <li>Verificar colisión con obstáculo → {@link MoveResult#HIT_OBSTACLE}.</li>
   *   <li>Verificar auto-colisión → {@link MoveResult#DIED}.</li>
   *   <li>Verificar colisión cruzada con otras serpientes → {@link MoveResult#DIED}.</li>
   *   <li>Aplicar teletransporte si corresponde.</li>
   *   <li>Consumir ratón o turbo si los hay.</li>
   *   <li>Avanzar la serpiente con {@link Snake#advance}.</li>
   *   <li>Reponer elementos consumidos aleatoriamente.</li>
   * </ol>
   *
   * @param snake     serpiente que realiza el paso
   * @param allSnakes lista completa de serpientes (para colisión cruzada)
   * @return resultado del movimiento
   * @throws NullPointerException si {@code snake} es {@code null}
   */
  public synchronized MoveResult step(Snake snake, List<Snake> allSnakes) {
    Objects.requireNonNull(snake, "snake");
    var head = snake.head();
    var dir  = snake.direction();
    Position next = new Position(head.x() + dir.dx, head.y() + dir.dy).wrap(width, height);

    if (obstacles.contains(next)) return MoveResult.HIT_OBSTACLE;

    // Auto-colisión: la cabeza entra en el propio cuerpo.
    if (snake.containsPosition(next)) {
      snake.kill();
      return MoveResult.DIED;
    }

    // Colisión cruzada: la cabeza entra en el cuerpo de otra serpiente viva.
    // board.step() es synchronized en Board → solo un runner a la vez puede
    // estar aquí, así que adquirir varios monitores de Snake es seguro (no hay
    // dos hilos compitiendo por ellos simultáneamente desde dentro de step()).
    for (Snake other : allSnakes) {
      if (other != snake && other.isAlive() && other.containsPosition(next)) {
        snake.kill();
        return MoveResult.DIED;
      }
    }

    boolean teleported = false;
    if (teleports.containsKey(next)) {
      next = teleports.get(next);
      teleported = true;
    }

    boolean ateMouse = mice.remove(next);
    boolean ateTurbo = turbo.remove(next);

    snake.advance(next, ateMouse);

    if (ateMouse) {
      mice.add(randomEmpty());
      obstacles.add(randomEmpty());
      if (ThreadLocalRandom.current().nextDouble() < 0.2) turbo.add(randomEmpty());
    }

    if (ateTurbo)   return MoveResult.ATE_TURBO;
    if (ateMouse)   return MoveResult.ATE_MOUSE;
    if (teleported) return MoveResult.TELEPORTED;
    return MoveResult.MOVED;
  }

  /**
   * Crea {@code pairs} pares de portales de teletransporte en posiciones aleatorias.
   *
   * @param pairs número de pares a crear
   */
  private void createTeleportPairs(int pairs) {
    for (int i = 0; i < pairs; i++) {
      Position a = randomEmpty();
      Position b = randomEmpty();
      teleports.put(a, b);
      teleports.put(b, a);
    }
  }

  /**
   * Elige una posición aleatoria del tablero que no esté ocupada por ningún
   * elemento existente. Incluye un contador de seguridad para evitar bucles
   * infinitos en tableros muy llenos.
   *
   * @return posición libre aleatoria
   */
  private Position randomEmpty() {
    var rnd = ThreadLocalRandom.current();
    Position p;
    int guard = 0;
    do {
      p = new Position(rnd.nextInt(width), rnd.nextInt(height));
      if (++guard > width * height * 2) break;
    } while (mice.contains(p) || obstacles.contains(p)
          || turbo.contains(p) || teleports.containsKey(p));
    return p;
  }
}
