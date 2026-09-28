package com.netsim.model;

import java.util.List;

/** Ready-made example networks. Positions are given as fractions of the canvas size. */
public final class NetworkPresets {

    public static final List<String> NAMES = List.of(
            "Campus network",
            "Mesh network",
            "Ring network",
            "Count-to-infinity demo");

    private NetworkPresets() {
    }

    public static String description(String name) {
        return switch (name) {
            case "Campus network" -> "7 routers with different link costs.";
            case "Mesh network" -> "8 routers with many alternative paths.";
            case "Ring network" -> "6 routers in a ring with one shortcut.";
            case "Count-to-infinity demo" -> "Switch to Distance-Vector, click Run to Convergence, "
                    + "turn OFF poison reverse, right-click the R2-R3 link, then click Next Round several times.";
            default -> "";
        };
    }

    /** Replaces the network with the chosen example, scaled to the given canvas size. */
    public static void load(Network net, String name, double width, double height) {
        net.clear();
        double w = width > 100 ? width : 900;
        double h = height > 100 ? height : 600;

        switch (name) {
            case "Campus network" -> campus(net, w, h);
            case "Mesh network" -> mesh(net, w, h);
            case "Ring network" -> ring(net, w, h);
            case "Count-to-infinity demo" -> countToInfinity(net, w, h);
            default -> throw new IllegalArgumentException("Unknown example: " + name);
        }
    }

    private static void add(Network net, double w, double h, double fx, double fy) {
        net.addRouter(fx * w, fy * h); // names are R1, R2, R3... in order
    }

    private static void campus(Network net, double w, double h) {
        add(net, w, h, 0.08, 0.50);
        add(net, w, h, 0.28, 0.22);
        add(net, w, h, 0.28, 0.80);
        add(net, w, h, 0.52, 0.22);
        add(net, w, h, 0.52, 0.80);
        add(net, w, h, 0.72, 0.50);
        add(net, w, h, 0.92, 0.50);
        net.addLink("R1", "R2", 2);
        net.addLink("R1", "R3", 4);
        net.addLink("R2", "R3", 1);
        net.addLink("R2", "R4", 5);
        net.addLink("R3", "R5", 3);
        net.addLink("R4", "R5", 2);
        net.addLink("R4", "R6", 1);
        net.addLink("R5", "R6", 6);
        net.addLink("R6", "R7", 2);
        net.addLink("R2", "R5", 8);
    }

    private static void mesh(Network net, double w, double h) {
        add(net, w, h, 0.10, 0.32);
        add(net, w, h, 0.30, 0.15);
        add(net, w, h, 0.55, 0.22);
        add(net, w, h, 0.86, 0.17);
        add(net, w, h, 0.20, 0.78);
        add(net, w, h, 0.45, 0.55);
        add(net, w, h, 0.68, 0.82);
        add(net, w, h, 0.90, 0.56);
        net.addLink("R1", "R2", 3);
        net.addLink("R1", "R5", 2);
        net.addLink("R2", "R3", 4);
        net.addLink("R2", "R6", 2);
        net.addLink("R3", "R4", 6);
        net.addLink("R3", "R6", 1);
        net.addLink("R3", "R8", 7);
        net.addLink("R4", "R8", 2);
        net.addLink("R5", "R6", 5);
        net.addLink("R5", "R7", 9);
        net.addLink("R6", "R7", 3);
        net.addLink("R7", "R8", 2);
        net.addLink("R6", "R8", 8);
    }

    private static void ring(Network net, double w, double h) {
        for (int i = 0; i < 6; i++) {
            double angle = -Math.PI / 2 + i * 2 * Math.PI / 6;
            add(net, w, h, 0.5 + 0.32 * Math.cos(angle), 0.53 + 0.36 * Math.sin(angle));
        }
        int[] costs = {2, 1, 3, 1, 2, 4};
        for (int i = 0; i < 6; i++) {
            net.addLink("R" + (i + 1), "R" + ((i + 1) % 6 + 1), costs[i]);
        }
        net.addLink("R1", "R4", 3);
    }

    private static void countToInfinity(Network net, double w, double h) {
        add(net, w, h, 0.20, 0.50);
        add(net, w, h, 0.50, 0.50);
        add(net, w, h, 0.80, 0.50);
        net.addLink("R1", "R2", 1);
        net.addLink("R2", "R3", 1);
    }
}