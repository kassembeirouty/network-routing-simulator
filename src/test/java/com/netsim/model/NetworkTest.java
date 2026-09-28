package com.netsim.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class NetworkTest {

    @Test
    void routersGetAutomaticNames() {
        Network net = new Network();
        assertEquals("R1", net.addRouter(0, 0).getName());
        assertEquals("R2", net.addRouter(0, 0).getName());
        assertEquals(2, net.getRouters().size());
    }

    @Test
    void automaticNamesSkipNamesAlreadyUsed() {
        Network net = new Network();
        net.addRouter("R1", 0, 0);
        assertEquals("R2", net.addRouter(0, 0).getName());
    }

    @Test
    void duplicateRouterNameIsRejected() {
        Network net = new Network();
        net.addRouter("A", 0, 0);
        assertThrows(IllegalArgumentException.class, () -> net.addRouter("A", 5, 5));
    }

    @Test
    void linksWorkInBothDirections() {
        Network net = new Network();
        Router a = net.addRouter("A", 0, 0);
        Router b = net.addRouter("B", 0, 0);
        net.addLink(a, b, 4);

        assertEquals(Map.of(b, 4), net.neighbors(a));
        assertEquals(Map.of(a, 4), net.neighbors(b));
        assertSame(net.findLink(a, b), net.findLink(b, a));
    }

    @Test
    void downLinksAreNotNeighbors() {
        Network net = new Network();
        Router a = net.addRouter("A", 0, 0);
        Router b = net.addRouter("B", 0, 0);
        Link link = net.addLink(a, b, 1);

        link.setUp(false);
        assertTrue(net.neighbors(a).isEmpty());

        link.setUp(true);
        assertFalse(net.neighbors(a).isEmpty());
    }

    @Test
    void duplicateLinkIsRejectedInEitherDirection() {
        Network net = new Network();
        Router a = net.addRouter("A", 0, 0);
        Router b = net.addRouter("B", 0, 0);
        net.addLink(a, b, 1);
        assertThrows(IllegalArgumentException.class, () -> net.addLink(b, a, 2));
    }

    @Test
    void selfLinkIsRejected() {
        Network net = new Network();
        Router a = net.addRouter("A", 0, 0);
        assertThrows(IllegalArgumentException.class, () -> net.addLink(a, a, 1));
    }

    @Test
    void invalidCostIsRejected() {
        Network net = new Network();
        Router a = net.addRouter("A", 0, 0);
        Router b = net.addRouter("B", 0, 0);
        assertThrows(IllegalArgumentException.class, () -> net.addLink(a, b, 0));
        assertThrows(IllegalArgumentException.class, () -> net.addLink(a, b, Link.MAX_COST + 1));
    }

    @Test
    void removingRouterRemovesItsLinks() {
        Network net = new Network();
        Router a = net.addRouter("A", 0, 0);
        Router b = net.addRouter("B", 0, 0);
        Router c = net.addRouter("C", 0, 0);
        net.addLink(a, b, 1);
        net.addLink(b, c, 1);

        net.removeRouter(b);

        assertEquals(2, net.getRouters().size());
        assertTrue(net.getLinks().isEmpty());
        assertNull(net.findRouter("B"));
    }

    @Test
    void otherEndOfLink() {
        Network net = new Network();
        Router a = net.addRouter("A", 0, 0);
        Router b = net.addRouter("B", 0, 0);
        Router c = net.addRouter("C", 0, 0);
        Link link = net.addLink(a, b, 1);

        assertSame(b, link.other(a));
        assertSame(a, link.other(b));
        assertThrows(IllegalArgumentException.class, () -> link.other(c));
    }
}