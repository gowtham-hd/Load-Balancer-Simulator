package com.gowtham.lbsim.strategy;

import com.gowtham.lbsim.backend.BackendServer;
import com.gowtham.lbsim.model.Request;

import java.util.Comparator;
import java.util.List;

/**
 * Select the backend with the fewest currentConnections.
 * Ties are broken by the order in list (stable).
 */
public class LeastConnectionsStrategy implements Strategy {

    @Override
    public BackendServer select(String routeKey, Request request, List<BackendServer> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;
        return candidates.stream()
                .min(Comparator.comparingInt(BackendServer::getCurrentConnections))
                .orElse(null);
    }
}
