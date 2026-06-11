# Snake Race — Lab 2 (Java 21, Virtual Threads)

A snake racing game where each snake runs autonomously in its own thread. The goal of this lab is to identify and fix race conditions, unsafe collections, and busy-waiting in a multithreaded program using Java 21 synchronization mechanisms.

## Author

JUAN SEBASTIÁN GUAYAZÁN CLAVIJO
Software Architectures (ISIS ARSW - 101)
Dean's Office of Systems Engineering
Systems Engineering
Colombian School of Engineering Julio Garavito
2026-i

---

## Requirements

- **JDK 21** (Temurin recommended)
- **Maven 3.9+**
- OS: Windows, macOS or Linux

---

## How to run

```bash
# Compile and verify
mvn clean verify

# Run with N snakes (default 2)
mvn -q -DskipTests exec:java -Dsnakes=4

# High-load robustness test
mvn -q -DskipTests exec:java -Dsnakes=20
```

**Controls:**
| Key | Action |
|---|---|
| `Start` (button) | Starts the game |
| `Pause` / `Resume` (button) | Pauses and resumes |
| `Space` | Same as the button |
| Arrow keys `← ↑ → ↓` | Controls snake #0 |
| `W A S D` | Controls snake #1 |

---

## Project architecture

```
co.eci.snake
├── app/
│   └── Main.java                  → Entry point; launches SnakeApp
│
├── core/
│   ├── Board.java                 → Board: mice, obstacles, turbo, teleports
│   ├── Snake.java                 → Snake body (deque of positions)
│   ├── Direction.java             → Enum: UP, DOWN, LEFT, RIGHT
│   ├── Position.java              → Coordinate (x, y) with wrap-around
│   ├── GameState.java             → Enum: STOPPED, RUNNING, PAUSED
│   └── engine/
│       └── GameClock.java         → Repaint scheduler (pauses/resumes the UI)
│
├── concurrency/
│   ├── PauseBarrier.java          → [NEW] wait/notify barrier to pause runners
│   └── SnakeRunner.java           → Runnable for each snake (one virtual thread each)
│
└── ui/legacy/
    └── SnakeApp.java              → Swing window: board, button, statistics
```

### Execution flow

```
main()
  └─ SnakeApp()
       ├─ Creates Board (35×28) with mice, obstacles, turbo and teleports
       ├─ Creates N Snake objects
       ├─ Creates PauseBarrier (starts in paused state)
       ├─ Launches N SnakeRunners in virtual threads → all block on barrier.awaitUnpaused()
       └─ Shows window with "Start" button

On "Start" press
  └─ barrier.resume()  →  notifyAll() unblocks all runners
  └─ clock.start()     →  repaint every 60 ms

Each SnakeRunner (loop):
  1. barrier.awaitUnpaused()       →  blocks here if paused
  2. maybeTurn()                   →  random turn with 10% probability
  3. board.step(snake, allSnakes)  →  computes and applies the move
  4. Thread.sleep(80 ms)           →  base speed (40 ms in turbo mode)
```

---

## Game rules

| Element | Effect |
|---|---|
| Mouse (black circle) | Snake grows; a new obstacle appears |
| Obstacle (orange) | Bounce: random turn, does not kill |
| Teleport (red arrow) | Snake exits through the paired portal |
| Turbo (lightning bolt) | Doubles speed for 100 ticks |
| Own body | Self-collision → dies |
| Another snake's body | Cross-collision → dies |

---

## Lab concepts

### Thread

A thread is the smallest unit of execution within a process. Multiple threads share the same memory space (variables, objects), making them efficient but prone to conflicts when accessing the same data simultaneously.

In this project each snake runs in its own thread, created with the Java 21 **virtual threads** API:
```java
var exec = Executors.newVirtualThreadPerTaskExecutor();
snakes.forEach(s -> exec.submit(new SnakeRunner(s, board, barrier, snakes)));
```

### Virtual Threads (Java 21)

Virtual threads are lightweight threads managed by the JVM instead of the operating system. Unlike OS threads (platform threads), thousands can be created without memory issues. They are ideal for tasks that spend most of their time waiting (I/O, `sleep`), such as the `SnakeRunner` instances that sleep 80 ms between each step.

### Race Condition

Occurs when two or more threads access shared data and the result depends on the order in which they execute. It is non-deterministic: the program may work correctly most of the time and fail unpredictably.

**Example in this project:** the `SnakeRunner` writes to `body` (via `advance()`) while the Swing EDT reads it (via `snapshot()`) in `paintComponent()`. If the OS interrupts the runner mid-write and the EDT enters to read, it may find the `ArrayDeque` in an inconsistent state → `ConcurrentModificationException`.

### Critical Section

The section of code that accesses a shared resource and must only be executed by **one thread at a time**. The key is to keep the critical section as small as possible: protecting more than necessary reduces parallelism without benefit.

**Example in this project:** inside `board.step()`, `mice`, `obstacles`, `turbo` and `teleports` are modified. All that logic is the critical section protected by the `Board` monitor.

### Monitor and `synchronized`

Java implements the **monitor** pattern with the `synchronized` keyword. When a method is `synchronized`, only one thread can execute it at a time on the same object. Other threads that try to enter are blocked until the first one exits.

```java
// Only one thread at a time can execute step() on the same Board
public synchronized MoveResult step(Snake snake, List<Snake> allSnakes) { ... }
```

The object acting as the key is called the **monitor** or **lock**. In `synchronized` instance methods, the monitor is `this`.

### `volatile`

The `volatile` keyword guarantees that when a thread writes a variable, the new value is immediately visible to all other threads (bypassing CPU cache). It is lighter than `synchronized` but **only sufficient for simple reads and writes**. It does not protect compound operations like read-check-write.

```java
private volatile boolean alive = true; // simple read/write → volatile is sufficient
private volatile Direction direction;  // but turn() is compound → needs synchronized
```

### `wait()` and `notifyAll()`

These are methods of Java's `Object` class (inherited by all objects) that allow threads to coordinate on the same monitor:

- **`wait()`**: the calling thread **releases the monitor** and goes to sleep. It does not consume CPU while waiting. Can only be called inside a `synchronized` block.
- **`notify()`**: wakes up **one** random thread that is in `wait()` on that monitor.
- **`notifyAll()`**: wakes up **all** threads in `wait()`. Preferred when multiple threads are waiting for the same condition.

The correct pattern always uses `while` (not `if`) to guard against spurious wakeups:
```java
synchronized void awaitUnpaused() throws InterruptedException {
    while (paused) {   // while, not if
        wait();
    }
}
```

### Busy-wait

An anti-pattern where a thread repeatedly checks a condition in a loop without yielding control:
```java
// BAD: wastes CPU doing nothing useful
while (paused) { } // busy-wait
```
Consumes 100% of a CPU core while waiting. The correct solution is to use `wait()` / `notifyAll()`, which suspend the thread without wasting CPU.

### Deadlock

A situation where two or more threads block each other waiting for a resource that the other holds, and neither can continue.

**Classic example:**
- Thread A holds lock 1 and waits for lock 2
- Thread B holds lock 2 and waits for lock 1
→ Neither ever makes progress.

**How it is avoided in this project:** a **fixed lock acquisition order** is established: always `Board → Snake`. The reverse (`Snake → Board`) never occurs, so no dependency cycle can form.

### EDT — Event Dispatch Thread

In Swing applications, all UI operations (painting, responding to keyboard/mouse events) must run on a special thread called the **EDT**. Modifying the UI from another thread leads to unpredictable behavior.

In this project, repainting is always scheduled from the EDT using:
```java
SwingUtilities.invokeLater(gamePanel::repaint);
```
And the `Snake` methods called by the EDT (`snapshot()`, `isAlive()`, etc.) are `synchronized` to be safe when called concurrently with the `SnakeRunner` threads.

---

## Concurrency analysis

### Bugs found in the original code

#### Bug 1 — Data race on `Snake.body` (ConcurrentModificationException)

**Problem:** `body` was an `ArrayDeque` with no synchronization. Two threads accessed it simultaneously:
- The `SnakeRunner` wrote to it via `advance()` (called from `board.step()`)
- The Swing EDT read it via `snapshot()` in `paintComponent()`

An `ArrayDeque` is not thread-safe. If one thread modifies the collection while another iterates it, Java throws `ConcurrentModificationException`. In the worst case, corrupted data can be read without any exception.

**Fix:** `synchronized` was added to all methods that access `body`:
```java
public synchronized Position head()           { return body.peekFirst(); }
public synchronized Deque<Position> snapshot() { return new ArrayDeque<>(body); }
public synchronized void advance(...)          { ... }
public synchronized boolean containsPosition() { return body.contains(p); }
```
`snapshot()` returns a copy of the body so the EDT can paint without holding the lock during the entire repaint.

---

#### Bug 2 — `turn()` was not atomic (data race on `direction`)

**Problem:** `direction` was `volatile`, which guarantees visibility but NOT atomicity for compound operations. `turn()` performed a read-check-write:
```java
// Not atomic with volatile alone:
if (direction == Direction.UP && dir == Direction.DOWN) { return; } // READ
this.direction = dir;                                                // WRITE
```
The EDT (player input) and the `SnakeRunner` (`randomTurn()`) could call `turn()` simultaneously. A context switch between the read and the write allowed the snake to turn in the opposite direction, bypassing the anti-reversal check.

**Fix:** `turn()` was synchronized on `this`:
```java
public synchronized void turn(Direction dir) { ... }
```
Now the entire read-check-write is atomic.

---

#### Bug 3 — Pause did not suspend the `SnakeRunner` threads (implicit busy-wait)

**Problem:** `togglePause()` called `clock.pause()`, which only stopped the screen repaint. The `SnakeRunner` threads ran in independent virtual threads with their own `Thread.sleep(80)` and **never checked the pause state**. Snakes kept moving even though the screen was frozen.

**Fix:** `PauseBarrier` was created using `wait()` / `notifyAll()` on its own monitor:
```java
public synchronized void awaitUnpaused() throws InterruptedException {
    while (paused) {
        wait(); // releases the monitor and suspends the thread without burning CPU
    }
}
```
Each runner calls `barrier.awaitUnpaused()` at the start of each iteration. On pause, `barrier.pause()` sets `paused = true`; runners finish their current `step()` and then block with `wait()`. On resume, `barrier.resume()` calls `notifyAll()` and all runners continue.

`while (paused)` is used instead of `if (paused)` to guard against **spurious wakeups** (false wake-ups that the JVM may emit for OS-level reasons).

`notifyAll()` is used instead of `notify()` because multiple runners may be blocked; `notify()` would only wake one at random, leaving the rest blocked indefinitely.

---

#### Bug 4 — No death detection or statistics

**Problem:** snakes never died (on obstacle collision they simply bounced). There was no way to display the longest snake or the first to die when pausing.

**Fix:**
- `alive`, `diedAt` and `peakLength` were added to `Snake`
- `kill()` was implemented with an `if (!alive) return` guard to prevent double-kill
- **Self-collision** is detected in `Board.step()`: if the new head enters its own body → dies
- **Cross-collision** is detected: if the new head enters another living snake's body → dies
- On pause, `updateStats()` displays the longest living snake and the first to die

---

### Critical sections and lock ordering

The code uses two monitors: the `Board` monitor and each `Snake` monitor.

| Monitor | What it protects |
|---|---|
| `Board.this` | `mice`, `obstacles`, `turbo`, `teleports` (non-thread-safe HashSet/HashMap) |
| `Snake.this` | `body` (non-thread-safe ArrayDeque), `direction`, `alive`, `peakLength` |

**Acquisition order is always: `Board → Snake`.**

Inside `board.step()` (which holds the Board lock), synchronized Snake methods are called. From the EDT, Board and Snake methods are called separately (not nested). The reverse order `Snake → Board` never occurs, therefore **there is no deadlock risk**.

---

### Collections identified as unsafe

| Collection | Class | Problem | Fix |
|---|---|---|---|
| `ArrayDeque body` | `Snake` | Concurrent write and EDT read | `synchronized` on all accessor methods |
| `HashSet mice` | `Board` | Concurrent modification between runners | Already protected by `synchronized step()` |
| `HashSet obstacles` | `Board` | Same | Same |
| `HashMap teleports` | `Board` | Same | Same |

---

## Credits

Original base built by **Eng. Javier Toquica** as an ARSW course exercise.
