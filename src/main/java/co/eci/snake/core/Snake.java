package co.eci.snake.core;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Represents a snake on the board.
 *
 * <p>The body is stored as an {@link ArrayDeque} where the first element
 * is the head and the last is the tail. All methods that access the body
 * or the life state are synchronized on {@code this} to prevent race
 * conditions between:</p>
 * <ul>
 *   <li>The {@code SnakeRunner} (writes via {@link #advance} and {@link #kill})</li>
 *   <li>The Swing EDT (reads via {@link #snapshot} to paint)</li>
 *   <li>{@code Board.step()} (reads {@link #head} and {@link #direction},
 *       writes {@link #advance})</li>
 * </ul>
 *
 * <p><b>Lock ordering:</b> always {@code Board → Snake}. Never the reverse,
 * so there is no deadlock risk.</p>
 *
 * @author Juan Sebastian Guayazan Edilberto
 */
public final class Snake {

  /**
   * Snake body. Not thread-safe on its own; all accesses must be performed
   * inside a {@code synchronized(this)} block.
   */
  private final Deque<Position> body = new ArrayDeque<>();

  /** Current direction. {@code volatile} guarantees visibility across threads. */
  private volatile Direction direction;

  /** Maximum allowed body length; grows when mice are eaten. */
  private int maxLength = 5;

  /** {@code true} while the snake is alive. */
  private volatile boolean alive = true;

  /**
   * Timestamp ({@link System#nanoTime()}) of the moment of death.
   * Value {@code -1} while the snake is alive.
   */
  private volatile long diedAt = -1;

  /** Greatest body length reached during the game. */
  private int peakLength = 1;

  private Snake(Position start, Direction dir) {
    body.addFirst(start);
    this.direction = dir;
  }

  /**
   * Creates a snake at the given position and direction.
   *
   * @param x   starting column
   * @param y   starting row
   * @param dir initial movement direction
   * @return new {@code Snake} instance
   */
  public static Snake of(int x, int y, Direction dir) {
    return new Snake(new Position(x, y), dir);
  }

  /**
   * Returns the current movement direction.
   *
   * @return current direction
   */
  public synchronized Direction direction() { return direction; }

  /**
   * Changes the snake's direction if the turn is valid.
   *
   * <p>Any 180° turn (opposite to the current direction) is rejected
   * to prevent the snake from moving into itself. The method is
   * synchronized so that player input (EDT) and the random turn from
   * {@code SnakeRunner} do not create a race condition on the
   * read-check-write over {@code direction}.</p>
   *
   * @param dir desired new direction
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
   * Returns the current position of the head.
   *
   * @return head position
   */
  public synchronized Position head() { return body.peekFirst(); }

  /**
   * Returns an instant snapshot of the body for external use (e.g. rendering).
   *
   * <p>The copy is made while holding the monitor, guaranteeing a consistent
   * snapshot. The caller can iterate the copy freely without risk of
   * {@link java.util.ConcurrentModificationException}.</p>
   *
   * @return copy of the position deque (head at the first element)
   */
  public synchronized Deque<Position> snapshot() { return new ArrayDeque<>(body); }

  /**
   * Advances the snake by adding {@code newHead} as the new head.
   *
   * <p>If {@code grow} is {@code true}, the maximum length is incremented
   * (the snake ate a mouse). The tail is removed if the body exceeds
   * {@code maxLength}.</p>
   *
   * @param newHead position to which the head moves
   * @param grow    {@code true} if the snake should grow
   */
  public synchronized void advance(Position newHead, boolean grow) {
    body.addFirst(newHead);
    if (grow) maxLength++;
    while (body.size() > maxLength) body.removeLast();
    if (body.size() > peakLength) peakLength = body.size();
  }

  /**
   * Checks whether position {@code p} is occupied by any body segment.
   *
   * <p>Called from {@code Board.step()} while holding the {@code Board}
   * monitor, so the lock ordering is always {@code Board → Snake},
   * with no deadlock risk.</p>
   *
   * @param p position to check
   * @return {@code true} if {@code p} matches any body segment
   */
  public synchronized boolean containsPosition(Position p) {
    return body.contains(p);
  }

  /**
   * Marks the snake as dead, recording the time of death.
   *
   * <p>The guard {@code if (!alive) return} prevents two threads that detect
   * the same collision simultaneously from killing the snake twice.</p>
   */
  public synchronized void kill() {
    if (!alive) return;
    alive = false;
    diedAt = System.nanoTime();
  }

  /**
   * Returns whether the snake is still alive.
   *
   * @return {@code true} if alive
   */
  public boolean isAlive() { return alive; }

  /**
   * Returns the time of death in nanoseconds ({@link System#nanoTime()}).
   *
   * @return time of death, or {@code -1} if still alive
   */
  public long diedAt() { return diedAt; }

  /**
   * Returns the greatest body length reached during the game.
   *
   * @return peak length in segments
   */
  public synchronized int peakLength()    { return peakLength; }

  /**
   * Returns the current body length.
   *
   * @return current number of segments
   */
  public synchronized int currentLength() { return body.size(); }
}
