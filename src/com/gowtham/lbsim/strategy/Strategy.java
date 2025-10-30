package com.gowtham.lbsim.strategy;

import com.gowtham.lbsim.backend.BackendServer;
import com.gowtham.lbsim.model.Request;

import java.util.List;

/**
 * Strategy for selecting a BackendServer from a candidate list for a given route key.
 *
 * routeKey is the route/prefix (e.g. "/api") so strategies that maintain per-route state
 * (like RoundRobin) can index their counters by route.
 */
public interface Strategy {
    /**
     * Select a backend from candidates for the given route.
     *
     * @param routeKey  logical route identifier (e.g. "/api")
     * @param request   the request being routed (may be used by some strategies)
     * @param candidates list of candidate backends (assumed already filtered for health)
     * @return chosen BackendServer or null if none available
     */
    BackendServer select(String routeKey, Request request, List<BackendServer> candidates);
}
