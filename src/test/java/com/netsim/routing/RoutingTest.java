package com.netsim.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.netsim.model.Network;
import com.netsim.model.Router;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RoutingTest {

    /**
     * A --1-- B --2-- C --1-- D
     *  \_____5_____/  |
     *        B --7----'  (B-D cost 7)
     * Cheapest A->D is A-B-C-D with cost 4.
     */
    private Network sampleNetwork() {
        Network net = new Network();
        for (String name : List.of("A", "B", "C", "D")) {
            net.addRouter(name, 0, 0);
        }
        net.addLink("A", "B", 1);
        net.addLink("B", "C", 2);
        net.addLink("A", "C", 5);
        net.addLink("C", "D", 1);
        net.addLink("B", "D", 7);
        return net;
    }

    /** A --1-- B --1-- C  (a simple line) */
    private Network lineNetwork() {
        Network net = new Network();
        for (String name : List.of("A", "B", "C")) {
            net.addRouter(name, 0, 0);
        }
        net.addLink("A", "B", 1);
        net.addLink("B", "C", 1);
        return net;
    }

    private Network randomNetwork(long seed) {
        Random random = new Random(seed);
        Network net = new Network();
        int size = 8;
        for (int i = 0; i < size; i++) {
            net.addRouter(0, 0);
        }
        List<Router> routers = net.getRouters();
        for (int i = 0; i < size; i++) {
            for (int j = i + 1; j < size; j++) {
                if (random.nextDouble() < 0.35) {
                    net.addLink(routers.get(i), routers.get(j), 1 + random.nextInt(10));
                }
            }
        }
        return net;
    }

    // ---------- Link-State (Dijkstra) ----------

    @Test
    void linkStateFindsCheapestCosts() {
        Network net = sampleNetwork();
        RoutingTable table = LinkStateRouting.computeTable(net, net.findRouter("A"));

        assertEquals(0, table.get(net.findRouter("A")).cost());
        assertEquals(1, table.get(net.findRouter("B")).cost());
        assertEquals(3, table.get(net.findRouter("C")).cost());
        assertEquals(4, table.get(net.findRouter("D")).cost());
    }

    @Test
    void linkStateUsesCorrectNextHop() {
        Network net = sampleNetwork();
        RoutingTable table = LinkStateRouting.computeTable(net, net.findRouter("A"));

        // Everything from A goes through B first (A-C direct costs 5, A-B-C costs 3)
        assertSame(net.findRouter("B"), table.get(net.findRouter("C")).nextHop());
        assertSame(net.findRouter("B"), table.get(net.findRouter("D")).nextHop());
        assertNull(table.get(net.findRouter("A")).nextHop());
    }

    @Test
    void linkStateReroutesWhenLinkGoesDown() {
        Network net = sampleNetwork();
        net.findLink("B", "C").setUp(false);

        RoutingTable table = LinkStateRouting.computeTable(net, net.findRouter("A"));
        RouteEntry toD = table.get(net.findRouter("D"));

        assertEquals(6, toD.cost()); // A-C-D = 5 + 1
        assertSame(net.findRouter("C"), toD.nextHop());
    }

    @Test
    void unreachableRouterHasNoRoute() {
        Network net = sampleNetwork();
        Router isolated = net.addRouter("E", 0, 0);

        RoutingTable table = LinkStateRouting.computeTable(net, net.findRouter("A"));
        assertFalse(table.hasRoute(isolated));
    }

    // ---------- Distance-Vector (Bellman-Ford) ----------

    @Test
    void distanceVectorStartsKnowingOnlyItself() {
        Network net = sampleNetwork();
        DistanceVectorRouting dv = new DistanceVectorRouting(net, true);

        for (RoutingTable table : dv.getTables().values()) {
            assertEquals(1, table.size());
        }
        assertEquals(0, dv.getRound());
    }

    @Test
    void distanceVectorLearnsNeighborsAfterOneRound() {
        Network net = sampleNetwork();
        DistanceVectorRouting dv = new DistanceVectorRouting(net, true);
        dv.step();

        RoutingTable a = dv.getTables().get(net.findRouter("A"));
        assertTrue(a.hasRoute(net.findRouter("B")));
        assertTrue(a.hasRoute(net.findRouter("C")));
        assertFalse(a.hasRoute(net.findRouter("D"))); // two hops away, not known yet
    }

    @Test
    void distanceVectorConvergesToSameCostsAsLinkState() {
        for (long seed = 1; seed <= 25; seed++) {
            Network net = randomNetwork(seed);
            DistanceVectorRouting dv = new DistanceVectorRouting(net, true);
            dv.runUntilConverged(200);
            assertTrue(dv.isConverged(), "seed " + seed);

            Map<Router, RoutingTable> dvTables = dv.getTables();
            Map<Router, RoutingTable> lsTables = LinkStateRouting.computeAll(net);

            for (Router from : net.getRouters()) {
                for (Router to : net.getRouters()) {
                    RouteEntry ls = lsTables.get(from).get(to);
                    RouteEntry dvEntry = dvTables.get(from).get(to);
                    if (ls == null) {
                        assertNull(dvEntry, "seed " + seed + " " + from + "->" + to);
                    } else {
                        assertEquals(ls.cost(), dvEntry.cost(), "seed " + seed + " " + from + "->" + to);
                    }
                }
            }
        }
    }

    @Test
    void countToInfinityWithoutPoisonReverse() {
        Network net = lineNetwork();
        DistanceVectorRouting dv = new DistanceVectorRouting(net, false);
        dv.runUntilConverged(200);

        net.findLink("B", "C").setUp(false);
        int rounds = dv.runUntilConverged(200);

        // A and B keep telling each other they can reach C, counting up to INFINITY
        assertTrue(rounds > 20, "took " + rounds + " rounds");
        assertFalse(dv.getTables().get(net.findRouter("A")).hasRoute(net.findRouter("C")));
    }

    @Test
    void poisonReverseStopsCountToInfinity() {
        Network net = lineNetwork();
        DistanceVectorRouting dv = new DistanceVectorRouting(net, true);
        dv.runUntilConverged(200);

        net.findLink("B", "C").setUp(false);
        int rounds = dv.runUntilConverged(200);

        assertTrue(rounds <= 3, "took " + rounds + " rounds");
        assertFalse(dv.getTables().get(net.findRouter("A")).hasRoute(net.findRouter("C")));
    }

    @Test
    void distanceVectorHandlesRemovedRouter() {
        Network net = sampleNetwork();
        DistanceVectorRouting dv = new DistanceVectorRouting(net, true);
        dv.runUntilConverged(200);

        net.removeRouter(net.findRouter("D"));
        dv.runUntilConverged(200);

        assertEquals(3, dv.getTables().size());
        assertEquals(3, dv.getTables().get(net.findRouter("A")).size());
    }

    // ---------- Forwarding ----------

    @Test
    void packetFollowsTheCheapestPath() {
        Network net = sampleNetwork();
        var tables = LinkStateRouting.computeAll(net);
        var result = Forwarding.trace(net, tables, net.findRouter("A"), net.findRouter("D"));

        assertEquals(Forwarding.Status.DELIVERED, result.status());
        assertEquals(List.of("A", "B", "C", "D"),
                result.path().stream().map(Router::getName).toList());
    }

    @Test
    void packetToIsolatedRouterIsUnreachable() {
        Network net = sampleNetwork();
        Router isolated = net.addRouter("E", 0, 0);
        var tables = LinkStateRouting.computeAll(net);
        var result = Forwarding.trace(net, tables, net.findRouter("A"), isolated);

        assertEquals(Forwarding.Status.UNREACHABLE, result.status());
    }

    @Test
    void packetDroppedWhenTablesPointToDownLink() {
        Network net = lineNetwork();
        DistanceVectorRouting dv = new DistanceVectorRouting(net, true);
        dv.runUntilConverged(200);

        net.findLink("B", "C").setUp(false); // tables are now out of date
        var result = Forwarding.trace(net, dv.getTables(), net.findRouter("A"), net.findRouter("C"));

        assertEquals(Forwarding.Status.LINK_DOWN, result.status());
    }

    @Test
    void routingLoopDetectedDuringCountToInfinity() {
        Network net = lineNetwork();
        DistanceVectorRouting dv = new DistanceVectorRouting(net, false);
        dv.runUntilConverged(200);

        net.findLink("B", "C").setUp(false);
        dv.step(); // B now thinks it can reach C through A, and A through B

        var result = Forwarding.trace(net, dv.getTables(), net.findRouter("A"), net.findRouter("C"));
        assertEquals(Forwarding.Status.LOOP, result.status());
    }
}