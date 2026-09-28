package com.netsim.ui;

import com.netsim.model.Link;
import com.netsim.model.Network;
import com.netsim.model.NetworkPresets;
import com.netsim.model.Router;
import com.netsim.routing.DistanceVectorRouting;
import com.netsim.routing.Forwarding;
import com.netsim.routing.LinkStateRouting;
import com.netsim.routing.RouteEntry;
import com.netsim.routing.RoutingTable;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

/** The main window: toolbar on top, network and log in the center, controls on the right. */
public class MainWindow extends JFrame implements NetworkCanvas.Listener {

    private static final String LINK_STATE = "Link-State (OSPF · Dijkstra)";
    private static final String DISTANCE_VECTOR = "Distance-Vector (RIP · Bellman-Ford)";

    private final Network network = new Network();
    private final NetworkCanvas canvas = new NetworkCanvas(network, this);
    private DistanceVectorRouting distanceVector;

    // Protocol controls
    private final JComboBox<String> protocolBox =
            new JComboBox<>(new String[] {LINK_STATE, DISTANCE_VECTOR});
    private final JCheckBox poisonReverseBox =
            new JCheckBox("Poison reverse (prevents count-to-infinity)", true);
    private final JButton nextRoundButton = new JButton("Next Round");
    private final JButton convergeButton = new JButton("Run to Convergence");
    private final JButton resetTablesButton = new JButton("Reset Tables");
    private final JLabel protocolStatus = new JLabel(" ");

    // Routing table
    private final JLabel tableTitle = new JLabel(" ");
    private final JLabel tableNote = new JLabel(" ");
    private final DefaultTableModel tableModel =
            new DefaultTableModel(new Object[] {"Destination", "Next Hop", "Cost"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };

    // Packet
    private final DefaultComboBoxModel<Router> fromModel = new DefaultComboBoxModel<>();
    private final DefaultComboBoxModel<Router> toModel = new DefaultComboBoxModel<>();
    private final JButton sendButton = new JButton("Send Packet");

    // Other
    private final JComboBox<String> presetBox =
            new JComboBox<>(NetworkPresets.NAMES.toArray(new String[0]));
    private final JTextArea log = new JTextArea();

    public MainWindow() {
        super("Network Routing Simulator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        add(buildToolbar(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildSidePanel(), BorderLayout.EAST);
        add(buildStatusBar(), BorderLayout.SOUTH);
        wireActions();

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setSize((int) (screen.width * 0.85), (int) (screen.height * 0.85));
        setMinimumSize(new Dimension(1000, 650));
        setLocationRelativeTo(null);

        // Load the first example once the window is shown (so the canvas has its real size)
        SwingUtilities.invokeLater(() -> {
            loadPreset(NetworkPresets.NAMES.get(0));
            log("Welcome! Click a router to see its routing table, or send a packet.");
        });
    }

    // ---------- Layout ----------

    private JToolBar buildToolbar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setBorder(new EmptyBorder(6, 8, 6, 8));

        ButtonGroup group = new ButtonGroup();
        addModeButton(bar, group, "Select / Move", NetworkCanvas.Mode.SELECT, true);
        addModeButton(bar, group, "Add Router", NetworkCanvas.Mode.ADD_ROUTER, false);
        addModeButton(bar, group, "Add Link", NetworkCanvas.Mode.ADD_LINK, false);
        addModeButton(bar, group, "Delete", NetworkCanvas.Mode.DELETE, false);

        bar.addSeparator(new Dimension(24, 0));
        bar.add(new JLabel("Example: "));
        presetBox.setMaximumSize(presetBox.getPreferredSize());
        bar.add(presetBox);
        bar.add(Box.createHorizontalStrut(6));

        JButton loadButton = new JButton("Load");
        loadButton.addActionListener(e -> loadPreset((String) presetBox.getSelectedItem()));
        bar.add(loadButton);
        bar.add(Box.createHorizontalStrut(6));

        JButton clearButton = new JButton("Clear All");
        clearButton.addActionListener(e -> clearAll());
        bar.add(clearButton);
        return bar;
    }

    private void addModeButton(JToolBar bar, ButtonGroup group, String text,
                               NetworkCanvas.Mode mode, boolean selected) {
        JToggleButton button = new JToggleButton(text, selected);
        button.addActionListener(e -> canvas.setMode(mode));
        group.add(button);
        bar.add(button);
        bar.add(Box.createHorizontalStrut(4));
    }

    /** The network drawing with the event log underneath. */
    private JPanel buildCenter() {
        log.setEditable(false);
        log.setLineWrap(true);
        log.setWrapStyleWord(true);
        log.setFont(Theme.FONT_SMALL);
        JScrollPane logScroll = new JScrollPane(log);
        logScroll.setPreferredSize(new Dimension(0, 130));
        logScroll.setBorder(BorderFactory.createTitledBorder("Event Log"));

        JPanel center = new JPanel(new BorderLayout());
        center.add(canvas, BorderLayout.CENTER);
        center.add(logScroll, BorderLayout.SOUTH);
        return center;
    }

    private JPanel buildSidePanel() {
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        JPanel protocol = section("Routing Protocol");
        protocol.add(row(protocolBox));
        protocol.add(row(poisonReverseBox));
        protocol.add(row(nextRoundButton, convergeButton));
        protocol.add(row(resetTablesButton));
        protocol.add(row(protocolStatus));
        top.add(protocol);

        JPanel tableSection = section("Routing Table");
        tableSection.add(row(tableTitle));
        JTable table = new JTable(tableModel);
        table.setRowHeight(24);
        table.setFillsViewportHeight(true);
        table.setFocusable(false);
        table.setRowSelectionAllowed(false);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(340, 210));
        tableScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        tableSection.add(tableScroll);
        tableSection.add(row(tableNote));
        top.add(tableSection);

        JPanel packet = section("Send a Packet");
        packet.add(row(new JLabel("From"), new JComboBox<>(fromModel),
                new JLabel("To"), new JComboBox<>(toModel)));
        packet.add(row(sendButton));
        top.add(packet);

        JPanel side = new JPanel(new BorderLayout());
        side.setBorder(new EmptyBorder(8, 8, 8, 10));
        side.setPreferredSize(new Dimension(380, 0));
        side.add(top, BorderLayout.NORTH);
        return side;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.setBorder(new EmptyBorder(2, 6, 4, 6));
        bar.add(new JLabel("Tip: right-click a link to take it DOWN or bring it back UP  ·  "
                + "double-click a link to change its cost  ·  drag routers to move them"));
        return bar;
    }

    private JPanel section(String title) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createCompoundBorder(
                new EmptyBorder(0, 0, 8, 0), BorderFactory.createTitledBorder(title)));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private JPanel row(Component... components) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        for (Component c : components) {
            row.add(c);
        }
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    // ---------- Actions ----------

    private void wireActions() {
        protocolBox.addActionListener(e -> onProtocolChanged());
        poisonReverseBox.addActionListener(e -> {
            if (distanceVector != null) {
                distanceVector.setPoisonReverse(poisonReverseBox.isSelected());
                log("Poison reverse is now " + (poisonReverseBox.isSelected() ? "ON" : "OFF") + ".");
            }
            refresh();
        });
        nextRoundButton.addActionListener(e -> nextRound());
        convergeButton.addActionListener(e -> runToConvergence());
        resetTablesButton.addActionListener(e -> {
            distanceVector.reset();
            canvas.clearHighlight();
            log("Tables reset: every router only knows itself.");
            refresh();
        });
        sendButton.addActionListener(e -> sendPacket());
    }

    private boolean isDistanceVector() {
        return DISTANCE_VECTOR.equals(protocolBox.getSelectedItem());
    }

    private Map<Router, RoutingTable> currentTables() {
        if (isDistanceVector() && distanceVector != null) {
            return distanceVector.getTables();
        }
        return LinkStateRouting.computeAll(network);
    }

    private void onProtocolChanged() {
        canvas.clearHighlight();
        if (isDistanceVector()) {
            distanceVector = new DistanceVectorRouting(network, poisonReverseBox.isSelected());
            log("Switched to Distance-Vector. Each router starts knowing only itself. "
                    + "Click \"Next Round\" to let neighbors exchange their tables.");
        } else {
            distanceVector = null;
            log("Switched to Link-State. Every router knows the full map and runs Dijkstra, "
                    + "so tables are always up to date.");
        }
        refresh();
    }

    private void nextRound() {
        boolean changed = distanceVector.step();
        log("Round " + distanceVector.getRound() + ": "
                + (changed ? "routers exchanged tables, some routes changed."
                           : "no changes. The network has converged ✓"));
        canvas.clearHighlight();
        refresh();
    }

    private void runToConvergence() {
        int rounds = distanceVector.runUntilConverged(500);
        if (distanceVector.isConverged()) {
            log("Converged after " + rounds + " more round(s) with changes "
                    + "(total rounds: " + distanceVector.getRound() + ").");
        } else {
            log("Stopped after 500 rounds without converging.");
        }
        canvas.clearHighlight();
        refresh();
    }

    private void sendPacket() {
        Router from = (Router) fromModel.getSelectedItem();
        Router to = (Router) toModel.getSelectedItem();
        if (from == null || to == null) {
            return;
        }
        if (from == to) {
            log("Choose two different routers to send a packet.");
            return;
        }

        Forwarding.Result result = Forwarding.trace(network, currentTables(), from, to);
        boolean delivered = result.status() == Forwarding.Status.DELIVERED;
        sendButton.setEnabled(false);
        canvas.animatePacket(result.path(), delivered, () -> {
            sendButton.setEnabled(true);
            log(describe(result, from, to));
        });
    }

    private String describe(Forwarding.Result result, Router from, Router to) {
        List<Router> path = result.path();
        String route = path.stream().map(Router::getName).collect(Collectors.joining(" → "));
        Router last = path.get(path.size() - 1);

        return switch (result.status()) {
            case DELIVERED -> "Packet " + from + " → " + to + " delivered: " + route
                    + "  (" + (path.size() - 1) + " hops, cost " + pathCost(path) + ")";
            case UNREACHABLE -> "Packet dropped at " + last + ": no route to " + to
                    + ". Path so far: " + route;
            case LOOP -> "Routing loop detected: " + route
                    + ". The tables have not converged yet.";
            case LINK_DOWN -> "Packet dropped at " + last + ": its next-hop link is down. "
                    + "The tables are out of date, run more rounds. Path so far: " + route;
        };
    }

    private int pathCost(List<Router> path) {
        int cost = 0;
        for (int i = 0; i + 1 < path.size(); i++) {
            Link link = network.findLink(path.get(i), path.get(i + 1));
            cost += link.getCost();
        }
        return cost;
    }

    private void loadPreset(String name) {
        NetworkPresets.load(network, name, canvas.getWidth(), canvas.getHeight());
        canvas.resetState();
        if (distanceVector != null) {
            distanceVector.reset();
        }
        log("Loaded example \"" + name + "\". " + NetworkPresets.description(name));
        fromModel.removeAllElements();
        toModel.removeAllElements();
        refresh();
        if (!network.getRouters().isEmpty()) {
            canvas.select(network.getRouters().get(0));
        }
    }

    private void clearAll() {
        network.clear();
        canvas.resetState();
        if (distanceVector != null) {
            distanceVector.reset();
        }
        log("Network cleared.");
        refresh();
    }

    // ---------- Canvas events ----------

    @Override
    public void routerSelected(Router router) {
        if (router != null) {
            fromModel.setSelectedItem(router);
        }
        refreshTable();
    }

    @Override
    public void topologyChanged(String description) {
        log(description);
        canvas.clearHighlight();
        if (isDistanceVector() && distanceVector != null) {
            distanceVector.markTopologyChanged();
            log("Routers have not exchanged tables since this change. "
                    + "Use \"Next Round\" or \"Run to Convergence\".");
        }
        refresh();
    }

    // ---------- Refreshing the side panel ----------

    private void refresh() {
        boolean dv = isDistanceVector() && distanceVector != null;
        poisonReverseBox.setEnabled(dv);
        nextRoundButton.setEnabled(dv);
        convergeButton.setEnabled(dv);
        resetTablesButton.setEnabled(dv);
        sendButton.setEnabled(!canvas.isAnimating());

        if (dv) {
            protocolStatus.setText("Round " + distanceVector.getRound() + "   ·   "
                    + (distanceVector.isConverged() ? "Converged ✓" : "Not converged yet"));
        } else {
            protocolStatus.setText("Tables are recalculated instantly after every change");
        }

        refreshRouterLists();
        refreshTable();
        canvas.repaint();
    }

    private void refreshRouterLists() {
        Router from = (Router) fromModel.getSelectedItem();
        Router to = (Router) toModel.getSelectedItem();
        List<Router> routers = network.getRouters();

        fromModel.removeAllElements();
        toModel.removeAllElements();
        for (Router r : routers) {
            fromModel.addElement(r);
            toModel.addElement(r);
        }
        if (from != null && routers.contains(from)) {
            fromModel.setSelectedItem(from);
        }
        if (to != null && routers.contains(to)) {
            toModel.setSelectedItem(to);
        } else if (toModel.getSize() > 1) {
            toModel.setSelectedItem(toModel.getElementAt(toModel.getSize() - 1));
        }
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        Router selected = canvas.getSelected();
        if (selected == null || !network.getRouters().contains(selected)) {
            tableTitle.setText("Click a router to see its routing table");
            tableNote.setText(" ");
            return;
        }

        RoutingTable table = currentTables().get(selected);
        tableTitle.setText("Routing table of " + selected.getName());
        for (RouteEntry entry : table.getEntries()) {
            tableModel.addRow(new Object[] {
                    entry.destination().getName(),
                    entry.nextHop() == null ? "(this router)" : entry.nextHop().getName(),
                    entry.cost()
            });
        }
        int missing = network.getRouters().size() - table.size();
        tableNote.setText(missing == 0
                ? "Knows a route to every router"
                : missing + " router(s) unreachable or not learned yet");
    }

    private void log(String message) {
        log.append("• " + message + "\n");
        log.setCaretPosition(log.getDocument().getLength());
    }
}