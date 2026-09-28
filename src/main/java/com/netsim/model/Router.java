package com.netsim.model;

/** A router (node) in the network. The x/y position is only used for drawing. */
public class Router {

    private final int id;
    private final String name;
    private double x;
    private double y;

    public Router(int id, String name, double x, double y) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Router name cannot be empty");
        }
        this.id = id;
        this.name = name;
        this.x = x;
        this.y = y;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public String toString() {
        return name;
    }
}