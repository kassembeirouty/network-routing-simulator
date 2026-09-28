package com.netsim.ui;

import com.netsim.model.Link;
import com.netsim.model.Network;
import com.netsim.model.Router;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.text.ParseException;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** The drawing area: shows routers and links, and lets the user edit the network. */
public class NetworkCanvas extends JPanel {

    public enum Mode { SELECT, ADD_ROUTER, ADD_LINK, DELETE }

    /** Lets the main window react to what happens on the canvas. */
    public interface Listener {
        void routerSelected(Router router);

        void topologyChanged(String description);
    }

    private static final double RADIUS = 24;
    private static final Stroke LINK_STROKE =
            new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final Stroke PATH_STROKE =
            new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final Stroke DOWN_STROKE = new BasicStroke(
            2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {8f, 6f}, 0f);
    private static final Stroke PREVIEW_STROKE = new BasicStroke(
            2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {6f, 6f}, 0f);
    private static final Stroke ROUTER_BORDER = new BasicStroke(2f);

    private final Network network;
    private final Listener listener;

    private Mode mode = Mode.SELECT;
    private Router selected;
    private Router linkStart;
    private Router dragging;
    private Point mouse;

    private List<Router> highlightPath = List.of();
    private Color highlightColor = Theme.PATH;

    private Timer packetTimer;
    private List<Router> packetPath;
    private int packetHop;
    private double packetProgress;

    public NetworkCanvas(Network network, Listener listener) {
        this.network = network;
        this.listener = listener;
        setBackground(Theme.BG);
        setPreferredSize(new Dimension(900, 650));

        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                onPress(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragging = null;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                onDrag(e);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                mouse = e.getPoint();
                if (linkStart != null) {
                    repaint();
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    onDoubleClick(e);
                }
            }
        };
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
    }

    // ---------- Public API ----------

    public void setMode(Mode mode) {
        this.mode = mode;
        linkStart = null;
        repaint();
    }

    public Router getSelected() {
        return selected;
    }

    public void select(Router router) {
        selected = router;
        listener.routerSelected(router);
        repaint();
    }

    public boolean isAnimating() {
        return packetPath != null;
    }

    public void clearHighlight() {
        stopPacket();
        highlightPath = List.of();
        repaint();
    }

    public void resetState() {
        clearHighlight();
        selected = null;
        linkStart = null;
        dragging = null;
    }

    /** Moves a packet along the path, hop by hop, then calls onDone. */
    public void animatePacket(List<Router> path, boolean success, Runnable onDone) {
        stopPacket();
        highlightPath = path;
        highlightColor = success ? Theme.PATH : Theme.LINK_DOWN;
        if (path.size() < 2) {
            repaint();
            onDone.run();
            return;
        }

        packetPath = path;
        packetHop = 0;
        packetProgress = 0;
        packetTimer = new Timer(16, e -> {
            Router a = packetPath.get(packetHop);
            Router b = packetPath.get(packetHop + 1);
            double length = Math.max(1, Math.hypot(b.getX() - a.getX(), b.getY() - a.getY()));
            packetProgress += 7.0 / length; // about 7 pixels per frame
            if (packetProgress >= 1) {
                packetProgress = 0;
                packetHop++;
                if (packetHop >= packetPath.size() - 1) {
                    stopPacket();
                    onDone.run();
                }
            }
            repaint();
        });
        packetTimer.start();
    }

    // ---------- Mouse handling ----------

    private void onPress(MouseEvent e) {
        Point p = e.getPoint();
        Router router = routerAt(p);
        Link link = (router == null) ? linkAt(p) : null;

        if (SwingUtilities.isRightMouseButton(e)) {
            if (link != null) {
                link.setUp(!link.isUp());
                listener.topologyChanged("Link " + link.getA() + " - " + link.getB()
                        + " is now " + (link.isUp() ? "UP" : "DOWN") + ".");
            }
            return;
        }
        if (!SwingUtilities.isLeftMouseButton(e)) {
            return;
        }

        switch (mode) {
            case SELECT -> {
                if (router != null) {
                    dragging = router;
                    select(router);
                } else if (link == null) {
                    select(null);
                }
            }
            case ADD_ROUTER -> {
                if (router != null) {
                    select(router);
                } else if (link == null) {
                    Router added = network.addRouter(p.x, p.y);
                    selected = added;
                    listener.topologyChanged("Added router " + added + ".");
                    listener.routerSelected(added);
                }
            }
            case ADD_LINK -> {
                if (router != null) {
                    handleAddLink(router);
                }
            }
            case DELETE -> {
                if (router != null) {
                    network.removeRouter(router);
                    if (selected == router) {
                        selected = null;
                    }
                    listener.topologyChanged("Deleted router " + router + " and its links.");
                } else if (link != null) {
                    network.removeLink(link);
                    listener.topologyChanged("Deleted link " + link.getA() + " - " + link.getB() + ".");
                }
            }
        }
        repaint();
    }

    private void handleAddLink(Router router) {
        if (linkStart == null) {
            linkStart = router;
            return;
        }
        if (linkStart == router) {
            linkStart = null; // clicked the same router again: cancel
            return;
        }
        Router a = linkStart;
        linkStart = null;

        if (network.findLink(a, router) != null) {
            JOptionPane.showMessageDialog(this, a + " and " + router + " are already linked.",
                    "Add Link", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Integer cost = askCost("Cost of link " + a + " - " + router, 1);
        if (cost != null) {
            network.addLink(a, router, cost);
            listener.topologyChanged("Added link " + a + " - " + router + " (cost " + cost + ").");
        }
    }

    private void onDrag(MouseEvent e) {
        mouse = e.getPoint();
        if (dragging != null) {
            double x = Math.max(RADIUS, Math.min(getWidth() - RADIUS, e.getX()));
            double y = Math.max(RADIUS, Math.min(getHeight() - RADIUS, e.getY()));
            dragging.setPosition(x, y);
            repaint();
        }
    }

    private void onDoubleClick(MouseEvent e) {
        if (mode != Mode.SELECT || routerAt(e.getPoint()) != null) {
            return;
        }
        Link link = linkAt(e.getPoint());
        if (link == null) {
            return;
        }
        Integer cost = askCost("New cost for link " + link.getA() + " - " + link.getB(), link.getCost());
        if (cost != null && cost != link.getCost()) {
            link.setCost(cost);
            listener.topologyChanged("Link " + link.getA() + " - " + link.getB()
                    + " cost changed to " + cost + ".");
        }
    }

    private Integer askCost(String title, int initial) {
        JSpinner spinner = new JSpinner(
                new SpinnerNumberModel(initial, Link.MIN_COST, Link.MAX_COST, 1));
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(new JLabel("Cost (" + Link.MIN_COST + "-" + Link.MAX_COST + "):"), BorderLayout.WEST);
        panel.add(spinner, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(this, panel, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }
        try {
            spinner.commitEdit(); // accept a typed value
        } catch (ParseException ignored) {
            // keep the last valid value
        }
        return (Integer) spinner.getValue();
    }

    private Router routerAt(Point p) {
        List<Router> routers = network.getRouters();
        for (int i = routers.size() - 1; i >= 0; i--) {
            Router r = routers.get(i);
            if (p.distance(r.getX(), r.getY()) <= RADIUS) {
                return r;
            }
        }
        return null;
    }

    private Link linkAt(Point p) {
        for (Link link : network.getLinks()) {
            double distance = Line2D.ptSegDist(
                    link.getA().getX(), link.getA().getY(),
                    link.getB().getX(), link.getB().getY(), p.x, p.y);
            if (distance <= 7) {
                return link;
            }
        }
        return null;
    }

    private void stopPacket() {
        if (packetTimer != null) {
            packetTimer.stop();
            packetTimer = null;
        }
        packetPath = null;
    }

    // ---------- Drawing ----------

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(Theme.BG);
        g.fillRect(0, 0, getWidth(), getHeight());
        drawGrid(g);
        drawLinks(g);
        drawLinkPreview(g);
        drawRouters(g);
        drawPacket(g);
        drawHint(g);
        g.dispose();
    }

    private void drawGrid(Graphics2D g) {
        g.setColor(Theme.GRID);
        for (int x = 15; x < getWidth(); x += 30) {
            for (int y = 15; y < getHeight(); y += 30) {
                g.fillRect(x, y, 2, 2);
            }
        }
    }

    private boolean isOnPath(Router a, Router b) {
        for (int i = 0; i + 1 < highlightPath.size(); i++) {
            Router x = highlightPath.get(i);
            Router y = highlightPath.get(i + 1);
            if ((x == a && y == b) || (x == b && y == a)) {
                return true;
            }
        }
        return false;
    }

    private void drawLinks(Graphics2D g) {
        FontMetrics fm = g.getFontMetrics(Theme.FONT_BOLD);
        for (Link link : network.getLinks()) {
            Router a = link.getA();
            Router b = link.getB();

            if (!link.isUp()) {
                g.setColor(Theme.LINK_DOWN);
                g.setStroke(DOWN_STROKE);
            } else if (isOnPath(a, b)) {
                g.setColor(highlightColor);
                g.setStroke(PATH_STROKE);
            } else {
                g.setColor(Theme.LINK);
                g.setStroke(LINK_STROKE);
            }
            g.draw(new Line2D.Double(a.getX(), a.getY(), b.getX(), b.getY()));

            // Cost label in the middle of the link
            String text = link.isUp() ? String.valueOf(link.getCost()) : "DOWN";
            double mx = (a.getX() + b.getX()) / 2;
            double my = (a.getY() + b.getY()) / 2;
            int w = fm.stringWidth(text) + 14;
            int h = fm.getHeight() + 2;
            g.setColor(Theme.PANEL);
            g.fill(new RoundRectangle2D.Double(mx - w / 2.0, my - h / 2.0, w, h, 10, 10));
            g.setColor(link.isUp() ? Theme.TEXT : Theme.LINK_DOWN);
            g.setFont(Theme.FONT_BOLD);
            g.drawString(text, (float) (mx - fm.stringWidth(text) / 2.0),
                    (float) (my + fm.getAscent() / 2.0 - 2));
        }
    }

    private void drawLinkPreview(Graphics2D g) {
        if (linkStart != null && mouse != null) {
            g.setColor(Theme.ROUTER_LINK_START);
            g.setStroke(PREVIEW_STROKE);
            g.draw(new Line2D.Double(linkStart.getX(), linkStart.getY(), mouse.x, mouse.y));
        }
    }

    private void drawRouters(Graphics2D g) {
        FontMetrics fm = g.getFontMetrics(Theme.FONT_BOLD);
        g.setFont(Theme.FONT_BOLD);
        for (Router r : network.getRouters()) {
            Color fill;
            if (r == linkStart) {
                fill = Theme.ROUTER_LINK_START;
            } else if (r == selected) {
                fill = Theme.ROUTER_SELECTED;
            } else {
                fill = Theme.ROUTER;
            }
            double x = r.getX() - RADIUS;
            double y = r.getY() - RADIUS;
            Ellipse2D circle = new Ellipse2D.Double(x, y, RADIUS * 2, RADIUS * 2);

            g.setColor(Theme.SHADOW);
            g.fill(new Ellipse2D.Double(x + 3, y + 4, RADIUS * 2, RADIUS * 2));
            g.setColor(fill);
            g.fill(circle);
            g.setColor(fill.darker());
            g.setStroke(ROUTER_BORDER);
            g.draw(circle);

            g.setColor(Theme.BG);
            String name = r.getName();
            g.drawString(name, (float) (r.getX() - fm.stringWidth(name) / 2.0),
                    (float) (r.getY() + fm.getAscent() / 2.0 - 2));
        }
    }

    private void drawPacket(Graphics2D g) {
        if (packetPath == null) {
            return;
        }
        Router a = packetPath.get(packetHop);
        Router b = packetPath.get(packetHop + 1);
        double x = a.getX() + (b.getX() - a.getX()) * packetProgress;
        double y = a.getY() + (b.getY() - a.getY()) * packetProgress;
        g.setColor(Theme.PACKET_GLOW);
        g.fill(new Ellipse2D.Double(x - 16, y - 16, 32, 32));
        g.setColor(Theme.PACKET);
        g.fill(new Ellipse2D.Double(x - 9, y - 9, 18, 18));
    }

    private void drawHint(Graphics2D g) {
        g.setFont(Theme.FONT);
        g.setColor(Theme.SUBTEXT);
        g.drawString(hintText(), 14, 24);

        if (network.getRouters().isEmpty()) {
            String message = "Empty network. Choose \"Add Router\" or load an example.";
            FontMetrics fm = g.getFontMetrics();
            g.drawString(message, (getWidth() - fm.stringWidth(message)) / 2, getHeight() / 2);
        }
    }

    private String hintText() {
        return switch (mode) {
            case SELECT -> "Click a router to see its table  ·  Drag to move  ·  "
                    + "Right-click a link: down/up  ·  Double-click a link: change cost";
            case ADD_ROUTER -> "Click on an empty space to add a router";
            case ADD_LINK -> linkStart == null
                    ? "Click the first router of the new link"
                    : "Now click the second router (click " + linkStart + " again to cancel)";
            case DELETE -> "Click a router or a link to delete it";
        };
    }
}