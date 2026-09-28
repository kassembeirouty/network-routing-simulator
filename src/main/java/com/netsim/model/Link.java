package com.netsim.model;

/** A two-way connection between two routers, with a cost (like OSPF link cost). */
public class Link {

    public static final int MIN_COST = 1;
    public static final int MAX_COST = 20;

    private final Router a;
    private final Router b;
    private int cost;
    private boolean up = true;

    public Link(Router a, Router b, int cost) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("A link needs two routers");
        }
        if (a == b) {
            throw new IllegalArgumentException("A router cannot be linked to itself");
        }
        this.a = a;
        this.b = b;
        setCost(cost);
    }

    public Router getA() {
        return a;
    }

    public Router getB() {
        return b;
    }

    public int getCost() {
        return cost;
    }

    public void setCost(int cost) {
        if (cost < MIN_COST || cost > MAX_COST) {
            throw new IllegalArgumentException(
                    "Link cost must be between " + MIN_COST + " and " + MAX_COST);
        }
        this.cost = cost;
    }

    public boolean isUp() {
        return up;
    }

    public void setUp(boolean up) {
        this.up = up;
    }

    /** True if this link touches the given router. */
    public boolean connects(Router r) {
        return r == a || r == b;
    }

    /** True if this link connects exactly these two routers (in any order). */
    public boolean connects(Router x, Router y) {
        return (x == a && y == b) || (x == b && y == a);
    }

    /** The router on the other end of the link. */
    public Router other(Router r) {
        if (r == a) {
            return b;
        }
        if (r == b) {
            return a;
        }
        throw new IllegalArgumentException(r + " is not part of this link");
    }

    @Override
    public String toString() {
        return a + " - " + b + " (cost " + cost + (up ? "" : ", DOWN") + ")";
    }
}