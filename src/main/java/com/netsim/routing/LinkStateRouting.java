package com.netsim.routing;

import com.netsim.model.Network;
import com.netsim.model.Router;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Link-State routing (like OSPF): every router knows the full topology and runs
 * Dijkstra's algorithm to find the cheapest path to every other router.
 */
public final class LinkStateRouting {

    private LinkStateRouting() {
    }

    private record QueueItem(Router router, int distance) {
    }

    /** Runs Dijkstra from one router and builds its routing table. */
    public static RoutingTable computeTable(Network network, Router source) {
        Map<Router, Integer> distance = new HashMap<>();
        Map<Router, Router> firstHop = new HashMap<>(); // first router on the path from source
        Set<Router> done = new HashSet<>();

        PriorityQueue<QueueItem> queue = new PriorityQueue<>(
                Comparator.comparingInt(QueueItem::distance)
                        .thenComparingInt(item -> item.router().getId()));

        distance.put(source, 0);
        queue.add(new QueueItem(source, 0));

        while (!queue.isEmpty()) {
            QueueItem item = queue.poll();
            Router current = item.router();
            if (!done.add(current)) {
                continue; // already finalized with a shorter distance
            }

            for (Map.Entry<Router, Integer> neighbor : network.neighbors(current).entrySet()) {
                Router next = neighbor.getKey();
                if (done.contains(next)) {
                    continue;
                }
                int newDistance = item.distance() + neighbor.getValue();
                Router hop = (current == source) ? next : firstHop.get(current);
                Integer oldDistance = distance.get(next);

                boolean better = oldDistance == null || newDistance < oldDistance;
                // Equal cost: prefer the lower router id, so results are always the same
                boolean tieBreak = oldDistance != null && newDistance == oldDistance
                        && hop.getId() < firstHop.get(next).getId();

                if (better || tieBreak) {
                    distance.put(next, newDistance);
                    firstHop.put(next, hop);
                    queue.add(new QueueItem(next, newDistance));
                }
            }
        }

        RoutingTable table = new RoutingTable(source);
        table.put(new RouteEntry(source, null, 0));
        for (Map.Entry<Router, Integer> entry : distance.entrySet()) {
            Router destination = entry.getKey();
            if (destination != source) {
                table.put(new RouteEntry(destination, firstHop.get(destination), entry.getValue()));
            }
        }
        return table;
    }

    /** Routing tables for every router in the network. */
    public static Map<Router, RoutingTable> computeAll(Network network) {
        Map<Router, RoutingTable> tables = new LinkedHashMap<>();
        for (Router router : network.getRouters()) {
            tables.put(router, computeTable(network, router));
        }
        return tables;
    }
}