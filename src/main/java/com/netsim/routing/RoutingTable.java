package com.netsim.routing;

import com.netsim.model.Router;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The routing table of one router: destination -> (next hop, cost). */
public class RoutingTable {

    private final Router owner;
    private final Map<Router, RouteEntry> entries = new LinkedHashMap<>();

    public RoutingTable(Router owner) {
        this.owner = owner;
    }

    public Router getOwner() {
        return owner;
    }

    public void put(RouteEntry entry) {
        entries.put(entry.destination(), entry);
    }

    /** The route to a destination, or null if it is unreachable. */
    public RouteEntry get(Router destination) {
        return entries.get(destination);
    }

    public boolean hasRoute(Router destination) {
        return entries.containsKey(destination);
    }

    /** All routes, sorted by router id (R1, R2, R3...). */
    public List<RouteEntry> getEntries() {
        List<RouteEntry> list = new ArrayList<>(entries.values());
        list.sort(Comparator.comparingInt(e -> e.destination().getId()));
        return list;
    }

    public int size() {
        return entries.size();
    }
}