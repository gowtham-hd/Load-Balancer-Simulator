package com.gowtham.lbsim.strategy;

import com.gowtham.lbsim.backend.BackendServer;
import com.gowtham.lbsim.model.Request;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-route Round-Robin selection.
 * Maintains a counter per routeKey so each route rotates independently.
 */
public class RoundRobinStrategy implements Strategy {

    // routeKey -> AtomicInteger counter
    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    @Override
    public BackendServer select(String routeKey, Request request, List<BackendServer> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;
        AtomicInteger counter = counters.computeIfAbsent(routeKey, k -> new AtomicInteger(0));
        int idx = Math.abs(counter.getAndIncrement()) % candidates.size();
        return candidates.get(idx);
    }
}
