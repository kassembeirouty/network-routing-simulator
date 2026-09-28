package com.netsim.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The whole network: a list of routers and the links between them. */
public class Network {

    private final List<Router> routers = new ArrayList<>();
    private final List<Link> links = new ArrayList<>();
    private int nextId = 1;

    // ---------- Routers ----------

    /** Adds a router with an automatic name: R1, R2, R3... */
    public Router addRouter(double x, double y) {
        while (findRouter("R" + nextId) != null) {
            nextId++;
        }
        return addRouter("R" + nextId, x, y);
    }

    public Router addRouter(String name, double x, double y) {
        if (findRouter(name) != null) {
            throw new IllegalArgumentException("A router named " + name + " already exists");
        }
        Router router = new Router(nextId++, name, x, y);
        routers.add(router);
        return router;
    }

    /** Removes a router and every link connected to it. */
    public void removeRouter(Router router) {
        links.removeIf(link -> link.connects(router));
        routers.remove(router);
    }

    public Router findRouter(String name) {
        for (Router r : routers) {
            if (r.getName().equals(name)) {
                return r;
            }
        }
        return null;
    }

    public List<Router> getRouters() {
        return Collections.unmodifiableList(routers);
    }

    // ---------- Links ----------

    public Link addLink(Router a, Router b, int cost) {
        if (!routers.contains(a) || !routers.contains(b)) {
            throw new IllegalArgumentException("Both routers must be part of the network");
        }
        if (findLink(a, b) != null) {
            throw new IllegalArgumentException("A link between " + a + " and " + b + " already exists");
        }
        Link link = new Link(a, b, cost);
        links.add(link);
        return link;
    }

    /** Convenience method using router names, used by presets and tests. */
    public Link addLink(String a, String b, int cost) {
        return addLink(findRouter(a), findRouter(b), cost);
    }

    public void removeLink(Link link) {
        links.remove(link);
    }

    public Link findLink(Router a, Router b) {
        for (Link link : links) {
            if (link.connects(a, b)) {
                return link;
            }
        }
        return null;
    }

    public Link findLink(String a, String b) {
        return findLink(findRouter(a), findRouter(b));
    }

    public List<Link> getLinks() {
        return Collections.unmodifiableList(links);
    }

    /** Direct neighbors of a router through links that are UP, with the link cost. */
    public Map<Router, Integer> neighbors(Router router) {
        Map<Router, Integer> result = new LinkedHashMap<>();
        for (Link link : links) {
            if (link.isUp() && link.connects(router)) {
                result.put(link.other(router), link.getCost());
            }
        }
        return result;
    }

    public void clear() {
        routers.clear();
        links.clear();
        nextId = 1;
    }
}