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
 * Main window of the Snake Race game.
 *
 * <h2>Button state machine</h2>
 * <pre>
 *   "Start"  →  "Pause"  →  "Resume"  →  "Pause"  → ...
 *   (STOPPED)   (RUNNING)   (PAUSED)      (RUNNING)
 * </pre>
 *
 * <h2>Concurrency responsibilities</h2>
 * <ul>
 *   <li>{@link GameClock} controls only the repaint (every 60 ms).</li>
 *   <li>{@link PauseBarrier} suspends and resumes the {@link SnakeRunner}
 *       threads via {@code wait()}/{@code notifyAll()}, without busy-wait.</li>
 *   <li>All UI manipulation happens on the Swing EDT.</li>
 * </ul>
 *
 * @author Juan Sebastian Guayazan Edilberto
 */
public final class SnakeApp extends JFrame {

  private final Board     board;
  private final GamePanel gamePanel;
  private final JButton   actionButton;

  /** Label that shows statistics when the game is paused. */
  private final JLabel statsLabel;

  private final GameClock clock;

  /**
   * List of active snakes. Populated in the constructor and never modified
   * afterwards, so no additional synchronization is needed.
   */
  private final java.util.List<Snake> snakes = new java.util.ArrayList<>();

  /** Barrier shared between the UI and all {@link SnakeRunner} threads. */
  private final PauseBarrier barrier = new PauseBarrier();

  /**
   * Builds the window, initializes the board and launches the runners.
   *
   * <p>Runners start immediately but block in {@link PauseBarrier#awaitUnpaused()}
   * until the player presses "Start". The {@link GameClock} is also not started
   * here; it starts in {@link #togglePause()}.</p>
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
    this.actionButton = new JButton("Start");
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
    // clock.start() is NOT called here: the game starts when "Start" is pressed
  }

  /**
   * Handles the action button state transitions.
   *
   * <ul>
   *   <li><b>Start:</b> unblocks all runners and starts the clock.</li>
   *   <li><b>Pause:</b> blocks runners with {@code wait()}, stops the repaint
   *       and shows statistics.</li>
   *   <li><b>Resume:</b> unblocks runners with {@code notifyAll()} and resumes
   *       the repaint.</li>
   * </ul>
   */
  private void togglePause() {
    if ("Start".equals(actionButton.getText())) {
      barrier.resume();
      clock.start();
      actionButton.setText("Pause");
      statsLabel.setText(" ");
    } else if ("Pause".equals(actionButton.getText())) {
      barrier.pause();
      clock.pause();
      updateStats();
      actionButton.setText("Resume");
    } else {
      statsLabel.setText(" ");
      barrier.resume();
      clock.resume();
      actionButton.setText("Pause");
    }
  }

  /**
   * Computes and displays game statistics when pausing.
   *
   * <p>Reads {@link Snake#currentLength()}, {@link Snake#isAlive()} and
   * {@link Snake#diedAt()} — all synchronized — guaranteeing a consistent
   * read even if some runner has not blocked yet.</p>
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

    StringBuilder sb = new StringBuilder("[ PAUSED ]  ");
    if (longestAlive != null) {
      sb.append("Longest alive: Snake #")
        .append(snakes.indexOf(longestAlive))
        .append(" (").append(longestAlive.currentLength()).append(" segments)");
    } else {
      sb.append("No snakes alive");
    }
    sb.append("   |   ");
    if (firstDead != null) {
      sb.append("First to die: Snake #")
        .append(snakes.indexOf(firstDead))
        .append(" (peak ").append(firstDead.peakLength()).append(" segments)");
    } else {
      sb.append("None have died yet");
    }
    statsLabel.setText(sb.toString());
  }

  // ---------------------------------------------------------------------------
  // Game panel
  // ---------------------------------------------------------------------------

  /**
   * Swing panel that renders the board state and the snakes.
   *
   * <p>Repainted every 60 ms from the EDT at the request of {@link GameClock}.
   * All state reads are performed via synchronized methods of {@link Board}
   * and {@link Snake}, so rendering is thread-safe.</p>
   */
  public static final class GamePanel extends JPanel {

    private final Board    board;
    private final Supplier snakesSupplier;

    /** Size in pixels of each board cell. */
    private final int cell = 20;

    /**
     * Functional supplier of the snake list.
     * Allows the panel to access the list without being directly coupled to it.
     */
    @FunctionalInterface
    public interface Supplier {
      List<Snake> get();
    }

    /**
     * Creates the game panel.
     *
     * @param board          board from which elements are read for drawing
     * @param snakesSupplier provider of the snake list
     */
    public GamePanel(Board board, Supplier snakesSupplier) {
      this.board          = board;
      this.snakesSupplier = snakesSupplier;
      setPreferredSize(new Dimension(board.width() * cell + 1, board.height() * cell + 40));
      setBackground(Color.WHITE);
    }

    /**
     * Draws the full board: grid, obstacles, mice, teleports,
     * turbo items and living snakes.
     *
     * <p>Dead snakes are not drawn (they disappear from the board).</p>
     *
     * @param g graphics context provided by Swing
     */
    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      var g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      // Grid
      g2.setColor(new Color(220, 220, 220));
      for (int x = 0; x <= board.width();  x++)
        g2.drawLine(x * cell, 0, x * cell, board.height() * cell);
      for (int y = 0; y <= board.height(); y++)
        g2.drawLine(0, y * cell, board.width() * cell, y * cell);

      // Obstacles
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

      // Mice
      g2.setColor(Color.BLACK);
      for (var p : board.mice()) {
        int x = p.x() * cell, y = p.y() * cell;
        g2.fillOval(x + 4, y + 4, cell - 8, cell - 8);
        g2.setColor(Color.WHITE);
        g2.fillOval(x + 8, y + 8, cell - 16, cell - 16);
        g2.setColor(Color.BLACK);
      }

      // Teleporters
      Map<Position,Position> tp = board.teleports();
      g2.setColor(Color.RED);
      for (var entry : tp.entrySet()) {
        Position from = entry.getKey();
        int x = from.x() * cell, y = from.y() * cell;
        int[] xs = { x + 4, x + cell - 4, x + cell - 10, x + cell - 10, x + 4 };
        int[] ys = { y + cell / 2, y + cell / 2, y + 4, y + cell - 4, y + cell / 2 };
        g2.fillPolygon(xs, ys, xs.length);
      }

      // Turbo items
      g2.setColor(Color.BLACK);
      for (var p : board.turbo()) {
        int x = p.x() * cell, y = p.y() * cell;
        int[] xs = { x + 8, x + 12, x + 10, x + 14, x + 6, x + 10 };
        int[] ys = { y + 2,  y + 2,  y + 8,  y + 8,  y + 16, y + 10 };
        g2.fillPolygon(xs, ys, xs.length);
      }

      // Snakes (living only)
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
   * Creates and shows the window on the Event Dispatch Thread (EDT).
   * Must be called from the main thread ({@code main}).
   */
  public static void launch() {
    SwingUtilities.invokeLater(SnakeApp::new);
  }
}
