package co.eci.snake.ui.legacy;

import co.eci.snake.concurrency.PauseBarrier;
import co.eci.snake.concurrency.SnakeRunner;
import co.eci.snake.core.Board;
import co.eci.snake.core.Direction;
import co.eci.snake.core.Position;
import co.eci.snake.core.Snake;
import co.eci.snake.core.engine.GameClock;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Ventana principal del juego Snake Race.
 *
 * <h2>Estados del botón de acción</h2>
 * <pre>
 *   "Iniciar"  →  "Pausar"  →  "Reanudar"  →  "Pausar"  → ...
 *   (STOPPED)     (RUNNING)    (PAUSED)        (RUNNING)
 * </pre>
 *
 * <h2>Responsabilidades de concurrencia</h2>
 * <ul>
 *   <li>{@link GameClock} controla únicamente el repaint (cada 60 ms).</li>
 *   <li>{@link PauseBarrier} suspende y reanuda los {@link SnakeRunner}
 *       mediante {@code wait()}/{@code notifyAll()}, sin busy-wait.</li>
 *   <li>Toda manipulación de la UI ocurre en el EDT de Swing.</li>
 * </ul>
 *
 * @author Juan Sebastian Guayazan Edilberto
 */
public final class SnakeApp extends JFrame {

  private final Board    board;
  private final GamePanel gamePanel;
  private final JButton  actionButton;

  /** Etiqueta que muestra estadísticas cuando el juego está pausado. */
  private final JLabel statsLabel;

  private final GameClock clock;

  /**
   * Lista de serpientes activas. Se llena en el constructor y no se modifica
   * posteriormente, por lo que no requiere sincronización adicional.
   */
  private final java.util.List<Snake> snakes = new java.util.ArrayList<>();

  /** Barrera compartida entre la UI y todos los {@link SnakeRunner}. */
  private final PauseBarrier barrier = new PauseBarrier();

  /**
   * Construye la ventana, inicializa el tablero y lanza los runners.
   *
   * <p>Los runners arrancan inmediatamente pero quedan bloqueados en
   * {@link PauseBarrier#awaitUnpaused()} hasta que el jugador presione "Iniciar".
   * El {@link GameClock} tampoco se inicia aquí; se inicia en {@link #togglePause()}.</p>
   */
  public SnakeApp() {
    super("The Snake Race");
    this.board = new Board(35, 28);

    int N = Integer.getInteger("snakes", 2);
    for (int i = 0; i < N; i++) {
      int x = 2 + (i * 3) % board.width();
      int y = 2 + (i * 2) % board.height();
      var dir = Direction.values()[i % Direction.values().length];
      snakes.add(Snake.of(x, y, dir));
    }

    this.gamePanel    = new GamePanel(board, () -> snakes);
    this.actionButton = new JButton("Iniciar");
    this.statsLabel   = new JLabel(" ");
    statsLabel.setHorizontalAlignment(SwingConstants.CENTER);

    JPanel bottomPanel = new JPanel(new BorderLayout());
    bottomPanel.add(actionButton, BorderLayout.NORTH);
    bottomPanel.add(statsLabel,   BorderLayout.SOUTH);

    setLayout(new BorderLayout());
    add(gamePanel,   BorderLayout.CENTER);
    add(bottomPanel, BorderLayout.SOUTH);

    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    pack();
    setLocationRelativeTo(null);

    this.clock = new GameClock(60, () -> SwingUtilities.invokeLater(gamePanel::repaint));

    var exec = Executors.newVirtualThreadPerTaskExecutor();
    snakes.forEach(s -> exec.submit(new SnakeRunner(s, board, barrier, snakes)));

    actionButton.addActionListener((ActionEvent e) -> togglePause());

    gamePanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("SPACE"), "pause");
    gamePanel.getActionMap().put("pause", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) { togglePause(); }
    });

    var player = snakes.get(0);
    InputMap  im = gamePanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
    ActionMap am = gamePanel.getActionMap();
    im.put(KeyStroke.getKeyStroke("LEFT"),  "left");
    im.put(KeyStroke.getKeyStroke("RIGHT"), "right");
    im.put(KeyStroke.getKeyStroke("UP"),    "up");
    im.put(KeyStroke.getKeyStroke("DOWN"),  "down");
    am.put("left",  new AbstractAction() {
      @Override public void actionPerformed(ActionEvent e) { player.turn(Direction.LEFT); }
    });
    am.put("right", new AbstractAction() {
      @Override public void actionPerformed(ActionEvent e) { player.turn(Direction.RIGHT); }
    });
    am.put("up", new AbstractAction() {
      @Override public void actionPerformed(ActionEvent e) { player.turn(Direction.UP); }
    });
    am.put("down", new AbstractAction() {
      @Override public void actionPerformed(ActionEvent e) { player.turn(Direction.DOWN); }
    });

    if (snakes.size() > 1) {
      var p2 = snakes.get(1);
      im.put(KeyStroke.getKeyStroke('A'), "p2-left");
      im.put(KeyStroke.getKeyStroke('D'), "p2-right");
      im.put(KeyStroke.getKeyStroke('W'), "p2-up");
      im.put(KeyStroke.getKeyStroke('S'), "p2-down");
      am.put("p2-left",  new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) { p2.turn(Direction.LEFT); }
      });
      am.put("p2-right", new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) { p2.turn(Direction.RIGHT); }
      });
      am.put("p2-up", new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) { p2.turn(Direction.UP); }
      });
      am.put("p2-down", new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) { p2.turn(Direction.DOWN); }
      });
    }

    setVisible(true);
    // clock.start() NO va aquí: el juego arranca al presionar "Iniciar"
  }

  /**
   * Maneja la transición de estados del botón de acción.
   *
   * <ul>
   *   <li><b>Iniciar:</b> desbloquea todos los runners y arranca el reloj.</li>
   *   <li><b>Pausar:</b> bloquea runners con {@code wait()}, detiene el repaint
   *       y muestra estadísticas.</li>
   *   <li><b>Reanudar:</b> desbloquea runners con {@code notifyAll()} y reanuda
   *       el repaint.</li>
   * </ul>
   */
  private void togglePause() {
    if ("Iniciar".equals(actionButton.getText())) {
      barrier.resume();
      clock.start();
      actionButton.setText("Pausar");
      statsLabel.setText(" ");
    } else if ("Pausar".equals(actionButton.getText())) {
      barrier.pause();
      clock.pause();
      updateStats();
      actionButton.setText("Reanudar");
    } else {
      statsLabel.setText(" ");
      barrier.resume();
      clock.resume();
      actionButton.setText("Pausar");
    }
  }

  /**
   * Calcula y muestra las estadísticas del juego al pausar.
   *
   * <p>Lee {@link Snake#currentLength()}, {@link Snake#isAlive()} y
   * {@link Snake#diedAt()} — todos sincronizados — garantizando una
   * lectura consistente aunque algún runner no haya bloqueado todavía.</p>
   */
  private void updateStats() {
    Snake longestAlive = snakes.stream()
        .filter(Snake::isAlive)
        .max(Comparator.comparingInt(Snake::currentLength))
        .orElse(null);

    Snake firstDead = snakes.stream()
        .filter(s -> !s.isAlive())
        .min(Comparator.comparingLong(Snake::diedAt))
        .orElse(null);

    StringBuilder sb = new StringBuilder("[ PAUSADO ]  ");
    if (longestAlive != null) {
      sb.append("Más larga viva: Serpiente #")
        .append(snakes.indexOf(longestAlive))
        .append(" (").append(longestAlive.currentLength()).append(" seg)");
    } else {
      sb.append("Ninguna serpiente viva");
    }
    sb.append("   |   ");
    if (firstDead != null) {
      sb.append("Primera en morir: Serpiente #")
        .append(snakes.indexOf(firstDead))
        .append(" (max ").append(firstDead.peakLength()).append(" seg)");
    } else {
      sb.append("Ninguna ha muerto aún");
    }
    statsLabel.setText(sb.toString());
  }

  // ---------------------------------------------------------------------------
  // Panel de juego
  // ---------------------------------------------------------------------------

  /**
   * Panel Swing que renderiza el estado del tablero y las serpientes.
   *
   * <p>Se repinta cada 60 ms desde el EDT a petición del {@link GameClock}.
   * Todas las lecturas de estado se realizan mediante métodos sincronizados
   * de {@link Board} y {@link Snake}, por lo que el renderizado es thread-safe.</p>
   */
  public static final class GamePanel extends JPanel {

    private final Board    board;
    private final Supplier snakesSupplier;

    /** Tamaño en píxeles de cada celda del tablero. */
    private final int cell = 20;

    /**
     * Proveedor funcional de la lista de serpientes.
     * Permite al panel acceder a la lista sin acoplarse directamente a ella.
     */
    @FunctionalInterface
    public interface Supplier {
      List<Snake> get();
    }

    /**
     * Crea el panel de juego.
     *
     * @param board          tablero del que se leen los elementos a dibujar
     * @param snakesSupplier proveedor de la lista de serpientes
     */
    public GamePanel(Board board, Supplier snakesSupplier) {
      this.board          = board;
      this.snakesSupplier = snakesSupplier;
      setPreferredSize(new Dimension(board.width() * cell + 1, board.height() * cell + 40));
      setBackground(Color.WHITE);
    }

    /**
     * Dibuja el tablero completo: grilla, obstáculos, ratones, teleports,
     * ítems turbo y serpientes vivas.
     *
     * <p>Las serpientes muertas no se dibujan (desaparecen del tablero).</p>
     *
     * @param g contexto gráfico proporcionado por Swing
     */
    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      var g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      // Grilla
      g2.setColor(new Color(220, 220, 220));
      for (int x = 0; x <= board.width();  x++)
        g2.drawLine(x * cell, 0, x * cell, board.height() * cell);
      for (int y = 0; y <= board.height(); y++)
        g2.drawLine(0, y * cell, board.width() * cell, y * cell);

      // Obstáculos
      g2.setColor(new Color(255, 102, 0));
      for (var p : board.obstacles()) {
        int x = p.x() * cell, y = p.y() * cell;
        g2.fillRect(x + 2, y + 2, cell - 4, cell - 4);
        g2.setColor(Color.RED);
        g2.drawLine(x + 4, y + 4,  x + cell - 6, y + 4);
        g2.drawLine(x + 4, y + 8,  x + cell - 6, y + 8);
        g2.drawLine(x + 4, y + 12, x + cell - 6, y + 12);
        g2.setColor(new Color(255, 102, 0));
      }

      // Ratones
      g2.setColor(Color.BLACK);
      for (var p : board.mice()) {
        int x = p.x() * cell, y = p.y() * cell;
        g2.fillOval(x + 4, y + 4, cell - 8, cell - 8);
        g2.setColor(Color.WHITE);
        g2.fillOval(x + 8, y + 8, cell - 16, cell - 16);
        g2.setColor(Color.BLACK);
      }

      // Teletransportadores
      Map<Position,Position> tp = board.teleports();
      g2.setColor(Color.RED);
      for (var entry : tp.entrySet()) {
        Position from = entry.getKey();
        int x = from.x() * cell, y = from.y() * cell;
        int[] xs = { x + 4, x + cell - 4, x + cell - 10, x + cell - 10, x + 4 };
        int[] ys = { y + cell / 2, y + cell / 2, y + 4, y + cell - 4, y + cell / 2 };
        g2.fillPolygon(xs, ys, xs.length);
      }

      // Ítems turbo
      g2.setColor(Color.BLACK);
      for (var p : board.turbo()) {
        int x = p.x() * cell, y = p.y() * cell;
        int[] xs = { x + 8, x + 12, x + 10, x + 14, x + 6, x + 10 };
        int[] ys = { y + 2,  y + 2,  y + 8,  y + 8,  y + 16, y + 10 };
        g2.fillPolygon(xs, ys, xs.length);
      }

      // Serpientes (solo las vivas)
      var snakes = snakesSupplier.get();
      int idx = 0;
      for (Snake s : snakes) {
        if (!s.isAlive()) { idx++; continue; }
        var body = s.snapshot().toArray(new Position[0]);
        for (int i = 0; i < body.length; i++) {
          var p = body[i];
          Color base = (idx == 0) ? new Color(0, 170, 0) : new Color(0, 160, 180);
          int shade = Math.max(0, 40 - i * 4);
          g2.setColor(new Color(
              Math.min(255, base.getRed()   + shade),
              Math.min(255, base.getGreen() + shade),
              Math.min(255, base.getBlue()  + shade)));
          g2.fillRect(p.x() * cell + 2, p.y() * cell + 2, cell - 4, cell - 4);
        }
        idx++;
      }
      g2.dispose();
    }
  }

  /**
   * Crea y muestra la ventana en el Event Dispatch Thread (EDT).
   * Debe llamarse desde el hilo principal ({@code main}).
   */
  public static void launch() {
    SwingUtilities.invokeLater(SnakeApp::new);
  }
}
