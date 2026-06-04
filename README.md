# Snake Race — Laboratorio 2 (Java 21, Virtual Threads)
Juego de carreras de serpientes donde cada serpiente corre de forma autónoma en su propio hilo. El objetivo del laboratorio es identificar y corregir condiciones de carrera, colecciones inseguras y esperas activas en un programa multihilo, usando mecanismos de sincronización de Java 21.

## Author

JUAN SEBASTIÁN GUAYAZÁN CLAVIJO  
Software Architectures (ISIS ARSW - 101)  
Dean's Office of Systems Engineering
Systems Engineering  
Colombian School of Engineering Julio Garavito  
2026-i

---

## Requisitos

- **JDK 21** (Temurin recomendado)
- **Maven 3.9+**
- SO: Windows, macOS o Linux

---

## Cómo ejecutar

```bash
# Compilar y verificar
mvn clean verify

# Ejecutar con N serpientes (por defecto 2)
mvn -q -DskipTests exec:java -Dsnakes=4

# Prueba de robustez con carga alta
mvn -q -DskipTests exec:java -Dsnakes=20
```

**Controles:**
| Tecla | Acción |
|---|---|
| `Iniciar` (botón) | Arranca el juego |
| `Pausar` / `Reanudar` (botón) | Pausa y reanuda |
| `Espacio` | Igual que el botón |
| Flechas `← ↑ → ↓` | Controla la serpiente #0 |
| `W A S D` | Controla la serpiente #1 |

---

## Arquitectura del proyecto

```
co.eci.snake
├── app/
│   └── Main.java                  → Punto de entrada; lanza SnakeApp
│
├── core/
│   ├── Board.java                 → Tablero: ratones, obstáculos, turbo, teleports
│   ├── Snake.java                 → Cuerpo de la serpiente (deque de posiciones)
│   ├── Direction.java             → Enum: UP, DOWN, LEFT, RIGHT
│   ├── Position.java              → Coordenada (x, y) con wrap-around
│   ├── GameState.java             → Enum: STOPPED, RUNNING, PAUSED
│   └── engine/
│       └── GameClock.java         → Scheduler de repaint (pausa/reanuda la UI)
│
├── concurrency/
│   ├── PauseBarrier.java          → [NUEVO] Barrera wait/notify para pausar runners
│   └── SnakeRunner.java           → Runnable de cada serpiente (un virtual thread cada una)
│
└── ui/legacy/
    └── SnakeApp.java              → Ventana Swing: tablero, botón, estadísticas
```

### Flujo de ejecución

```
main()
  └─ SnakeApp()
       ├─ Crea Board (35×28) con ratones, obstáculos, turbo y teleports
       ├─ Crea N objetos Snake
       ├─ Crea PauseBarrier (inicia en estado pausado)
       ├─ Lanza N SnakeRunner en virtual threads → todos bloquean en barrier.awaitUnpaused()
       └─ Muestra ventana con botón "Iniciar"

Al presionar "Iniciar"
  └─ barrier.resume()  →  notifyAll() desbloquea todos los runners
  └─ clock.start()     →  repaint cada 60 ms

Cada SnakeRunner (bucle):
  1. barrier.awaitUnpaused()  →  bloquea aquí si está pausado
  2. maybeTurn()              →  giro aleatorio con probabilidad 10 %
  3. board.step(snake, allSnakes)  →  calcula y aplica el movimiento
  4. Thread.sleep(80 ms)      →  velocidad base (40 ms en turbo)
```

---

## Reglas del juego

| Elemento | Efecto |
|---|---|
| Ratón (círculo negro) | La serpiente crece; aparece un nuevo obstáculo |
| Obstáculo (naranja) | Rebote: giro aleatorio, no mata |
| Teleport (flecha roja) | La serpiente sale por el portal par |
| Turbo (rayo) | Duplica la velocidad por 100 ticks |
| Cuerpo propio | Auto-colisión → muere |
| Cuerpo de otra serpiente | Colisión cruzada → muere |

---

## Conceptos del laboratorio

### Hilo (Thread)

Un hilo es la unidad mínima de ejecución dentro de un proceso. Varios hilos comparten el mismo espacio de memoria (variables, objetos), lo que los hace eficientes pero también propensos a conflictos cuando acceden a los mismos datos al mismo tiempo.

En este proyecto cada serpiente corre en su propio hilo, creado con la API de **virtual threads** de Java 21:
```java
var exec = Executors.newVirtualThreadPerTaskExecutor();
snakes.forEach(s -> exec.submit(new SnakeRunner(s, board, barrier, snakes)));
```

### Virtual Threads (Java 21)

Los virtual threads son hilos ligeros gestionados por la JVM en lugar del sistema operativo. A diferencia de los hilos del SO (platform threads), se pueden crear miles sin problema de memoria. Son ideales para tareas que pasan mucho tiempo esperando (I/O, `sleep`), como es el caso de los `SnakeRunner` que duermen 80 ms entre cada paso.

### Condición de carrera (Race Condition)

Ocurre cuando dos o más hilos acceden a un dato compartido y el resultado depende del orden en que se ejecutan. No es determinista: el programa puede funcionar bien la mayoría de las veces y fallar de forma impredecible.

**Ejemplo en este proyecto:** el `SnakeRunner` escribe en `body` (vía `advance()`) mientras el EDT de Swing lo lee (vía `snapshot()`). Si el SO interrumpe el runner a mitad de la escritura y el EDT entra a leer, puede encontrar el `ArrayDeque` en un estado inconsistente → `ConcurrentModificationException`.

### Región crítica

Es la sección de código que accede a un recurso compartido y que solo debe ejecutarse por **un hilo a la vez**. La clave es que la región crítica debe ser lo más pequeña posible: proteger más de lo necesario reduce el paralelismo sin beneficio.

**Ejemplo en este proyecto:** dentro de `board.step()` se modifican `mice`, `obstacles`, `turbo` y `teleports`. Toda esa lógica es la región crítica protegida por el monitor del `Board`.

### Monitor y `synchronized`

Java implementa el patrón **monitor** con la palabra clave `synchronized`. Cuando un método es `synchronized`, solo un hilo puede ejecutarlo a la vez sobre el mismo objeto. Los demás hilos que intenten entrar quedan bloqueados hasta que el primero salga.

```java
// Solo un hilo a la vez puede ejecutar step() sobre el mismo Board
public synchronized MoveResult step(Snake snake, List<Snake> allSnakes) { ... }
```

El objeto que actúa como llave se llama **monitor** o **lock**. En métodos de instancia `synchronized`, el monitor es `this`.

### `volatile`

La palabra clave `volatile` garantiza que cuando un hilo escribe una variable, el nuevo valor sea inmediatamente visible para todos los demás hilos (sin caché de CPU). Es más ligero que `synchronized` pero **solo es suficiente para lecturas y escrituras simples**. No protege operaciones compuestas como read-check-write.

```java
private volatile boolean alive = true; // lectura/escritura simple → volatile es suficiente
private volatile Direction direction;  // pero turn() es compound → necesita synchronized
```

### `wait()` y `notifyAll()`

Son métodos del objeto `Object` en Java (heredados por todos los objetos) que permiten coordinar hilos sobre un mismo monitor:

- **`wait()`**: el hilo que lo llama **libera el monitor** y se duerme. No consume CPU mientras espera. Solo puede llamarse dentro de un bloque `synchronized`.
- **`notify()`**: despierta a **un** hilo aleatorio que esté en `wait()` sobre ese monitor.
- **`notifyAll()`**: despierta a **todos** los hilos que estén en `wait()`. Se prefiere cuando hay múltiples hilos esperando la misma condición.

El patrón correcto siempre usa `while` (no `if`) para evitar spurious wakeups:
```java
synchronized void awaitUnpaused() throws InterruptedException {
    while (paused) {   // while, no if
        wait();
    }
}
```

### Espera activa (Busy-wait)

Es un antipatrón donde un hilo comprueba repetidamente una condición en un bucle sin ceder el control:
```java
// MAL: gasta CPU sin hacer nada útil
while (paused) { } // busy-wait
```
Consume el 100 % de un núcleo de CPU mientras espera. La solución correcta es usar `wait()` / `notifyAll()`, que suspenden el hilo sin gastar CPU.

### Deadlock

Es una situación donde dos o más hilos se bloquean mutuamente esperando un recurso que el otro tiene, y ninguno puede continuar.

**Ejemplo clásico:**
- Hilo A tiene el lock 1 y espera el lock 2
- Hilo B tiene el lock 2 y espera el lock 1
→ Ninguno avanza jamás.

**Cómo se evita en este proyecto:** se establece un **orden fijo de adquisición de locks**: siempre `Board → Snake`. Nunca ocurre la situación inversa (`Snake → Board`), por lo que no puede formarse un ciclo de dependencia.

### EDT — Event Dispatch Thread

En aplicaciones Swing, todas las operaciones de UI (pintar, responder a eventos del teclado/ratón) deben ejecutarse en un hilo especial llamado **EDT**. Si se modifica la UI desde otro hilo, el comportamiento es impredecible.

En este proyecto, el repaint se programa siempre desde el EDT usando:
```java
SwingUtilities.invokeLater(gamePanel::repaint);
```
Y los métodos de `Snake` que el EDT llama (`snapshot()`, `isAlive()`, etc.) son `synchronized` para que sean seguros al llamarse concurrentemente con los `SnakeRunner`.

---

## Análisis de concurrencia

### Bugs encontrados en el código original

#### Bug 1 — Data race en `Snake.body` (ConcurrentModificationException)

**Problema:** `body` era un `ArrayDeque` sin ninguna sincronización. Dos hilos accedían a él al mismo tiempo:
- El `SnakeRunner` escribía a través de `advance()` (llamado desde `board.step()`)
- El EDT de Swing leía a través de `snapshot()` en `paintComponent()`

Un `ArrayDeque` no es thread-safe. Si un hilo modifica la colección mientras otro la itera, Java lanza `ConcurrentModificationException`. En el peor caso, puede leer datos corruptos sin excepción.

**Solución:** se agregó `synchronized` a todos los métodos que acceden a `body`:
```java
public synchronized Position head()           { return body.peekFirst(); }
public synchronized Deque<Position> snapshot() { return new ArrayDeque<>(body); }
public synchronized void advance(...)          { ... }
public synchronized boolean containsPosition() { return body.contains(p); }
```
`snapshot()` devuelve una copia del cuerpo, así el EDT puede pintar sin mantener el lock durante todo el repaint.

---

#### Bug 2 — `turn()` no era atómica (data race en `direction`)

**Problema:** `direction` era `volatile`, lo que garantiza visibilidad pero NO atomicidad en operaciones compuestas. `turn()` hacía un read-check-write:
```java
// No atómico con solo volatile:
if (direction == Direction.UP && dir == Direction.DOWN) { return; } // LEE
this.direction = dir;                                                // ESCRIBE
```
El EDT (input del jugador) y el `SnakeRunner` (`randomTurn()`) podían llamar `turn()` al mismo tiempo. Un cambio de contexto entre la lectura y la escritura permitía que la serpiente girara en sentido opuesto, ignorando la validación anti-reversa.

**Solución:** se sincronizó `turn()` sobre `this`:
```java
public synchronized void turn(Direction dir) { ... }
```
Ahora el read-check-write completo es atómico.

---

#### Bug 3 — La pausa no suspendía los `SnakeRunner` (espera activa implícita)

**Problema:** `togglePause()` llamaba `clock.pause()`, que solo detenía el repaint de la pantalla. Los `SnakeRunner` corrían en virtual threads independientes con su propio `Thread.sleep(80)` y **nunca consultaban el estado de pausa**. Las serpientes seguían moviéndose aunque la pantalla estuviera congelada.

**Solución:** se creó `PauseBarrier` usando `wait()` / `notifyAll()` sobre un monitor propio:
```java
public synchronized void awaitUnpaused() throws InterruptedException {
    while (paused) {
        wait(); // libera el monitor y suspende el hilo sin consumir CPU
    }
}
```
Cada runner llama `barrier.awaitUnpaused()` al inicio de cada iteración. Al pausar, `barrier.pause()` pone `paused = true`; los runners terminan su `step()` actual y luego se bloquean con `wait()`. Al reanudar, `barrier.resume()` llama `notifyAll()` y todos los runners continúan.

Se usa `while (paused)` en lugar de `if (paused)` para protegerse contra **spurious wakeups** (despertares falsos que Java permite por razones del sistema operativo).

Se usa `notifyAll()` en lugar de `notify()` porque hay múltiples runners bloqueados; `notify()` solo despertaría uno aleatorio y los demás quedarían bloqueados indefinidamente.

---

#### Bug 4 — Sin detección de muerte ni estadísticas

**Problema:** las serpientes nunca morían (al chocar con un obstáculo simplemente rebotaban). No había forma de mostrar la serpiente más larga ni la primera en morir al pausar.

**Solución:**
- Se agregaron `alive`, `diedAt` y `peakLength` a `Snake`
- Se implementó `kill()` con guarda `if (!alive) return` para evitar doble muerte
- Se detecta **auto-colisión** en `Board.step()`: si la nueva cabeza entra en el propio cuerpo → muere
- Se detecta **colisión cruzada**: si la nueva cabeza entra en el cuerpo de otra serpiente viva → muere
- Al pausar, `updateStats()` muestra la serpiente viva más larga y la primera en morir

---

### Regiones críticas y orden de bloqueo

El código usa dos monitores: el del `Board` y el de cada `Snake`.

| Monitor | Lo que protege |
|---|---|
| `Board.this` | `mice`, `obstacles`, `turbo`, `teleports` (HashSet/HashMap no thread-safe) |
| `Snake.this` | `body` (ArrayDeque no thread-safe), `direction`, `alive`, `peakLength` |

**Orden de adquisición siempre: `Board → Snake`.**

Dentro de `board.step()` (que sostiene el lock de Board), se llaman métodos sincronizados de Snake. Desde el EDT, los métodos de Board y de Snake se llaman por separado (no anidados). Nunca existe el orden inverso `Snake → Board`, por lo tanto **no hay riesgo de deadlock**.

---

### Colecciones identificadas como no seguras

| Colección | Clase | Problema | Solución |
|---|---|---|---|
| `ArrayDeque body` | `Snake` | Escritura concurrente con lectura del EDT | `synchronized` en todos sus métodos de acceso |
| `HashSet mice` | `Board` | Modificación concurrente entre runners | Ya estaba protegida por `synchronized step()` |
| `HashSet obstacles` | `Board` | Ídem | Ídem |
| `HashMap teleports` | `Board` | Ídem | Ídem |

---

## Capturas del juego

> *(Agregar screenshots o GIFs aquí)*

**Estado inicial — esperando "Iniciar":**

![Estado inicial](docs/screenshot-inicio.png)

**Juego en ejecución con 20 serpientes:**

![20 serpientes](docs/screenshot-20snakes.png)

**Estadísticas al pausar:**

![Pausado con stats](docs/screenshot-pausa.png)

---

## Créditos

Base original construida por el **Ing. Javier Toquica** como ejercicio del curso ARSW.
Solución de concurrencia implementada por **Juan Sebastian Guayazan Edilberto**.
