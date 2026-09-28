package com.netsim.routing;

import com.netsim.model.Network;
import com.netsim.model.Router;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Distance-Vector routing (like RIP): routers only know their neighbors.
 * In every round, each router receives its neighbors' tables and updates its own
 * using the Bellman-Ford rule: cost(dest) = min over neighbors (link cost + neighbor's cost).
 *
 * Supports "poison reverse": a router tells a neighbor that a destination is at
 * INFINITY if it reaches that destination THROUGH that neighbor. This prevents the
 * "count to infinity" problem after a link failure.
 */
public class DistanceVectorRouting {

    /** Any cost at or above this value means "unreachable". */
    public static final int INFINITY = 64;

    private final Network network;
    private boolean poisonReverse;
    private Map<Router, Map<Router, RouteEntry>> vectors = new LinkedHashMap<>();
    private int round;
    private boolean converged;

    public DistanceVectorRouting(Network network, boolean poisonReverse) {
        this.network = network;
        this.poisonReverse = poisonReverse;
        reset();
    }

    /** Every router forgets everything except itself. */
    public void reset() {
        vectors.clear();
        round = 0;
        converged = false;
        syncRouters();
    }

    /**
     * Runs one synchronous round: all routers exchange tables at the same time.
     * Returns true if at least one table changed.
     */
    public boolean step() {
        syncRouters();
        List<Router> routers = network.getRouters();
        Map<Router, Map<Router, RouteEntry>> next = new LinkedHashMap<>();

        for (Router router : routers) {
            Map<Router, RouteEntry> table = new LinkedHashMap<>();
            table.put(router, new RouteEntry(router, null, 0));
            Map<Router, Integer> neighbors = network.neighbors(router);

            for (Router destination : routers) {
                if (destination == router) {
                    continue;
                }
                int bestCost = INFINITY;
                Router bestHop = null;

                for (Map.Entry<Router, Integer> neighbor : neighbors.entrySet()) {
                    Router via = neighbor.getKey();
                    int advertised = advertisedCost(via, destination, router);
                    int total = Math.min(INFINITY, neighbor.getValue() + advertised);

                    boolean better = total < bestCost;
                    boolean tieBreak = total == bestCost && total < INFINITY
                            && via.getId() < bestHop.getId();
                    if (better || tieBreak) {
                        bestCost = total;
                        bestHop = via;
                    }
                }

                if (bestCost < INFINITY) {
                    table.put(destination, new RouteEntry(destination, bestHop, bestCost));
                }
            }
            next.put(router, table);
        }

        boolean changed = !next.equals(vectors);
        vectors = next;
        round++;
        converged = !changed;
        return changed;
    }

    /**
     * Runs rounds until nothing changes (or maxRounds is reached).
     * Returns how many rounds changed something.
     */
    public int runUntilConverged(int maxRounds) {
        for (int i = 0; i < maxRounds; i++) {
            if (!step()) {
                return i;
            }
        }
        return maxRounds;
    }

    /** What neighbor {@code via} tells {@code receiver} about its cost to {@code destination}. */
    private int advertisedCost(Router via, Router destination, Router receiver) {
        RouteEntry entry = vectors.getOrDefault(via, Map.of()).get(destination);
        if (entry == null) {
            return INFINITY;
        }
        if (poisonReverse && entry.nextHop() == receiver) {
            return INFINITY; // "I reach it through you, so don't use me for it"
        }
        return entry.cost();
    }

    /** Keeps the tables in sync when routers are added or removed. */
    private void syncRouters() {
        List<Router> routers = network.getRouters();
        vectors.keySet().retainAll(routers);
        for (Router router : routers) {
            vectors.computeIfAbsent(router, r -> {
                Map<Router, RouteEntry> table = new LinkedHashMap<>();
                table.put(r, new RouteEntry(r, null, 0));
                return table;
            });
        }
        for (Map<Router, RouteEntry> table : vectors.values()) {
            table.values().removeIf(e -> !routers.contains(e.destination())
                    || (e.nextHop() != null && !routers.contains(e.nextHop())));
        }
    }

    /** Call this after the topology changes, so the simulator knows it must re-converge. */
    public void markTopologyChanged() {
        converged = false;
    }

    public Map<Router, RoutingTable> getTables() {
        syncRouters();
        Map<Router, RoutingTable> tables = new LinkedHashMap<>();
        for (Map.Entry<Router, Map<Router, RouteEntry>> entry : vectors.entrySet()) {
            RoutingTable table = new RoutingTable(entry.getKey());
            entry.getValue().values().forEach(table::put);
            tables.put(entry.getKey(), table);
        }
        return tables;
    }

    public int getRound() {
        return round;
    }

    public boolean isConverged() {
        return converged;
    }

    public boolean isPoisonReverse() {
        return poisonReverse;
    }

    public void setPoisonReverse(boolean poisonReverse) {
        this.poisonReverse = poisonReverse;
        converged = false;
    }
}