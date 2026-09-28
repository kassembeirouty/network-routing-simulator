package com.netsim.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.netsim.model.Network;
import com.netsim.model.NetworkPresets;
import com.netsim.model.Router;
import org.junit.jupiter.api.Test;

class PresetsTest {

    @Test
    void everyExampleIsFullyConnected() {
        for (String name : NetworkPresets.NAMES) {
            Network net = new Network();
            NetworkPresets.load(net, name, 900, 600);

            assertTrue(net.getRouters().size() >= 3, name);
            RoutingTable table = LinkStateRouting.computeTable(net, net.getRouters().get(0));
            assertEquals(net.getRouters().size(), table.size(), name + " should be fully connected");
        }
    }

    @Test
    void routersArePlacedInsideTheCanvas() {
        for (String name : NetworkPresets.NAMES) {
            Network net = new Network();
            NetworkPresets.load(net, name, 900, 600);
            for (Router r : net.getRouters()) {
                assertTrue(r.getX() > 0 && r.getX() < 900, name + " " + r);
                assertTrue(r.getY() > 0 && r.getY() < 600, name + " " + r);
            }
        }
    }

    @Test
    void loadingReplacesThePreviousNetwork() {
        Network net = new Network();
        NetworkPresets.load(net, "Campus network", 900, 600);
        NetworkPresets.load(net, "Count-to-infinity demo", 900, 600);

        assertEquals(3, net.getRouters().size());
        assertEquals("R1", net.getRouters().get(0).getName());
    }
}