package com.netsim.routing;

import com.netsim.model.Link;
import com.netsim.model.Network;
import com.netsim.model.Router;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Simulates forwarding a packet: at each router, look up the destination in that
 * router's table and send the packet to the next hop, like a real router does.
 */
public final class Forwarding {

    private Forwarding() {
    }

    public enum Status {
        DELIVERED,    // reached the destination
        UNREACHABLE,  // a router had no route to the destination
        LOOP,         // the packet came back to a router it already visited
        LINK_DOWN     // the table pointed to a link that is down (tables not updated yet)
    }

    public record Result(List<Router> path, Status status) {
    }

    public static Result trace(Network network, Map<Router, RoutingTable> tables,
                               Router source, Router destination) {
        List<Router> path = new ArrayList<>();
        Set<Router> visited = new HashSet<>();
        Router current = source;

        while (true) {
            path.add(current);
            if (current == destination) {
                return new Result(path, Status.DELIVERED);
            }
            if (!visited.add(current)) {
                return new Result(path, Status.LOOP);
            }

            RoutingTable table = tables.get(current);
            RouteEntry entry = (table == null) ? null : table.get(destination);
            if (entry == null || entry.nextHop() == null) {
                return new Result(path, Status.UNREACHABLE);
            }

            Link link = network.findLink(current, entry.nextHop());
            if (link == null || !link.isUp()) {
                return new Result(path, Status.LINK_DOWN);
            }
            current = entry.nextHop();
        }
    }
}