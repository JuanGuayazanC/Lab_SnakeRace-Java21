package co.eci.snake.core;

import java.util.ArrayDeque;
import java.util.Deque;

public final class Snake {
  // CORRECCIÓN: 'body' era un ArrayDeque sin ninguna sincronización.
  // El EDT (Swing) llama snapshot() para pintar mientras SnakeRunner llama
  // advance() desde board.step(). Eso causaba ConcurrentModificationException.
  // Solución: sincronizar todos los accesos a 'body' sobre 'this'.
  private final Deque<Position> body = new ArrayDeque<>();
  private volatile Direction direction;
  private int maxLength = 5;

  // --- campos para rastrear muerte (necesarios para estadísticas al pausar) ---
  private volatile boolean alive = true;
  private volatile long diedAt = -1;        // System.nanoTime() al morir
  private int peakLength = 1;               // longitud máxima histórica

  private Snake(Position start, Direction dir) {
    body.addFirst(start);
    this.direction = dir;
  }

  public static Snake of(int x, int y, Direction dir) {
    return new Snake(new Position(x, y), dir);
  }

  public synchronized Direction direction() { return direction; }

  // CORRECCIÓN: turn() era un read-check-write sobre 'direction' (volatile).
  // El input del jugador (EDT) y randomTurn() (SnakeRunner) podían llamarlo
  // al mismo tiempo. Con solo 'volatile' el compound check no es atómico.
  // Solución: sincronizar turn() sobre 'this'.
  public synchronized void turn(Direction dir) {
    if (!alive) return;
    if ((direction == Direction.UP && dir == Direction.DOWN) ||
        (direction == Direction.DOWN && dir == Direction.UP) ||
        (direction == Direction.LEFT && dir == Direction.RIGHT) ||
        (direction == Direction.RIGHT && dir == Direction.LEFT)) {
      return;
    }
    this.direction = dir;
  }

  public synchronized Position head() { return body.peekFirst(); }

  public synchronized Deque<Position> snapshot() { return new ArrayDeque<>(body); }

  public synchronized void advance(Position newHead, boolean grow) {
    body.addFirst(newHead);
    if (grow) maxLength++;
    while (body.size() > maxLength) body.removeLast();
    if (body.size() > peakLength) peakLength = body.size();
  }

  // Usado por Board.step() para detectar auto-colisión.
  // Se llama con el monitor de Board ya adquirido → orden Board→Snake, sin deadlock.
  public synchronized boolean containsPosition(Position p) {
    return body.contains(p);
  }

  public synchronized void kill() {
    if (!alive) return;
    alive = false;
    diedAt = System.nanoTime();
  }

  public boolean isAlive()         { return alive; }
  public long diedAt()             { return diedAt; }
  public synchronized int peakLength()   { return peakLength; }
  public synchronized int currentLength(){ return body.size(); }
}
