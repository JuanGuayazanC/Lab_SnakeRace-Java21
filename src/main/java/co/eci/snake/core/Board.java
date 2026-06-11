package co.eci.snake.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Game board. Contains and manages all scenario elements:
 * mice, obstacles, turbo items and teleporters.
 *
 * <h2>Thread safety</h2>
 * <p>The internal collections ({@code mice}, {@code obstacles}, {@code turbo},
 * {@code teleports}) are standard {@link HashSet}/{@link HashMap}, which are
 * not thread-safe. All reads and writes on them occur inside {@link #step},
 * which is declared {@code synchronized} on {@code this}.
 * The public getters ({@link #mice()}, etc.) are also {@code synchronized}
 * and return defensive copies.</p>
 *
 * <h2>Lock ordering</h2>
 * <p>Inside {@link #step}, synchronized {@link Snake} methods are called.
 * The acquisition order is always {@code Board → Snake}; never the reverse,
 * so there is no deadlock risk.</p>
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
   * Result of a movement step computed by {@link #step}.
   */
  public enum MoveResult {
    /** The snake moved with no special event. */
    MOVED,
    /** The snake ate a mouse and grew. */
    ATE_MOUSE,
    /** The head hit an obstacle; the runner should turn randomly. */
    HIT_OBSTACLE,
    /** The snake stepped on a turbo item; its speed doubles temporarily. */
    ATE_TURBO,
    /** The snake entered a teleporter and exited through the paired portal. */
    TELEPORTED,
    /** The snake died from self-collision or cross-collision. */
    DIED
  }

  /**
   * Creates a board with the given dimensions and randomly initializes
   * mice, obstacles, turbo items and teleporter pairs.
   *
   * @param width  width in cells (must be &gt; 0)
   * @param height height in cells (must be &gt; 0)
   * @throws IllegalArgumentException if any dimension is ≤ 0
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

  /** @return board width in cells */
  public int width()  { return width; }

  /** @return board height in cells */
  public int height() { return height; }

  /**
   * Returns a copy of the mouse positions.
   *
   * @return set of positions containing mice
   */
  public synchronized Set<Position> mice()      { return new HashSet<>(mice); }

  /**
   * Returns a copy of the obstacle positions.
   *
   * @return set of positions containing obstacles
   */
  public synchronized Set<Position> obstacles() { return new HashSet<>(obstacles); }

  /**
   * Returns a copy of the turbo item positions.
   *
   * @return set of positions containing turbo items
   */
  public synchronized Set<Position> turbo()     { return new HashSet<>(turbo); }

  /**
   * Returns a copy of the teleporter map.
   *
   * @return map from source to destination for each portal
   */
  public synchronized Map<Position,Position> teleports() { return new HashMap<>(teleports); }

  /**
   * Computes and applies a movement step for {@code snake}.
   *
   * <p>The method is synchronized on {@code Board}, so only one
   * {@code SnakeRunner} can execute it at a time. The event sequence is:</p>
   * <ol>
   *   <li>Compute the next position with wrap-around.</li>
   *   <li>Check obstacle collision → {@link MoveResult#HIT_OBSTACLE}.</li>
   *   <li>Check self-collision → {@link MoveResult#DIED}.</li>
   *   <li>Check cross-collision with other snakes → {@link MoveResult#DIED}.</li>
   *   <li>Apply teleport if applicable.</li>
   *   <li>Consume mouse or turbo item if present.</li>
   *   <li>Advance the snake with {@link Snake#advance}.</li>
   *   <li>Randomly replenish consumed elements.</li>
   * </ol>
   *
   * @param snake     snake performing the step
   * @param allSnakes full list of snakes (for cross-collision detection)
   * @return result of the movement
   * @throws NullPointerException if {@code snake} is {@code null}
   */
  public synchronized MoveResult step(Snake snake, List<Snake> allSnakes) {
    Objects.requireNonNull(snake, "snake");
    var head = snake.head();
    var dir  = snake.direction();
    Position next = new Position(head.x() + dir.dx, head.y() + dir.dy).wrap(width, height);

    if (obstacles.contains(next)) return MoveResult.HIT_OBSTACLE;

    // Self-collision: the head enters its own body.
    if (snake.containsPosition(next)) {
      snake.kill();
      return MoveResult.DIED;
    }

    // Cross-collision: the head enters the body of another living snake.
    // board.step() is synchronized on Board → only one runner at a time can
    // be here, so acquiring multiple Snake monitors is safe (no two threads
    // compete for them simultaneously from inside step()).
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
   * Creates {@code pairs} teleporter pairs at random empty positions.
   *
   * @param pairs number of pairs to create
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
   * Picks a random board position that is not occupied by any existing element.
   * Includes a safety counter to avoid infinite loops on very full boards.
   *
   * @return a random empty position
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
