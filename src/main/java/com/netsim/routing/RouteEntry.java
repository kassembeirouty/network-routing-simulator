package com.netsim.routing;

import com.netsim.model.Router;

/**
 * One row of a routing table: to reach {@code destination}, send the packet to
 * {@code nextHop}; the total cost is {@code cost}. For the router itself, nextHop is null.
 */
public record RouteEntry(Router destination, Router nextHop, int cost) {
}