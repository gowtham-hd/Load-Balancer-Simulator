package com.gowtham.lbsim.core;

import com.gowtham.lbsim.backend.BackendServer;
import com.gowtham.lbsim.model.Request;
import com.gowtham.lbsim.model.Response;
import com.gowtham.lbsim.net.Connection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple L7 (application-level) load balancer.
 *
 * Features:
 * - Path-prefix based routing: registerRoute("/api", List<BackendServer>)
 * - Per-route Round-Robin selection using AtomicInteger counters.
 * - handleRequest(...) forwards to chosen backend and logs the response.
 * - handleConnection(...) simulates TLS handshake completion and marks connection ESTABLISHED.
 *
 * Notes:
 * - This class keeps things intentionally simple and synchronous to make the sim easy to reason about.
 * - You can later swap selection with LeastConnections by reading BackendServer.getCurrentConnections().
 */
public class L7LoadBalancer implements LoadBalancer {

    private final String name;

    /**
     * Routing table:
     * key = path prefix (e.g. "/api"), value = ordered list of backends for that prefix.
     * Use ConcurrentHashMap for thread-safe reads/writes.
     */
    private final Map<String, List<BackendServer>> routes = new ConcurrentHashMap<>();

    /**
     * Per-route round-robin counters: pathPrefix -> counter
     */
    private final Map<String, AtomicInteger> rrCounters = new ConcurrentHashMap<>();

    public L7LoadBalancer(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * Register or replace a route mapping for a path prefix.
     * Example: registerRoute("/api", Arrays.asList(api1, api2))
     *
     * @param pathPrefix must start with "/" (no trailing semantics enforced).
     * @param backends   ordered list of BackendServer instances.
     */
    public void registerRoute(String pathPrefix, List<BackendServer> backends) {
        if (pathPrefix == null || !pathPrefix.startsWith("/")) {
            throw new IllegalArgumentException("pathPrefix must start with '/'");
        }
        if (backends == null) backends = Collections.emptyList();
        // defensive copy
        List<BackendServer> copy = new ArrayList<>(backends);
        routes.put(pathPrefix, copy);
        rrCounters.putIfAbsent(pathPrefix, new AtomicInteger(0));
    }

    /**
     * Remove a registered route.
     */
    public void deregisterRoute(String pathPrefix) {
        routes.remove(pathPrefix);
        rrCounters.remove(pathPrefix);
    }

    /**
     * Find best matching route for a request path using longest-prefix match.
     * Returns null if no route matches.
     */
    protected String findMatchingPrefix(String path) {
        if (path == null) return null;
        // Longest prefix match: iterate registered prefixes and pick the longest that is a prefix of path
        String matched = null;
        for (String prefix : routes.keySet()) {
            if (path.startsWith(prefix)) {
                if (matched == null || prefix.length() > matched.length()) {
                    matched = prefix;
                }
            }
        }
        return matched;
    }

    /**
     * Select a backend for the given prefix using per-prefix round-robin.
     * Returns null if no healthy backend is available.
     */
    protected BackendServer selectBackendForPrefix(String prefix) {
        List<BackendServer> list = routes.get(prefix);
        if (list == null || list.isEmpty()) return null;

        // Filter healthy backends
        List<BackendServer> healthy = new ArrayList<>();
        for (BackendServer b : list) {
            if (b != null && b.isHealthy()) healthy.add(b);
        }
        if (healthy.isEmpty()) return null;

        AtomicInteger counter = rrCounters.computeIfAbsent(prefix, k -> new AtomicInteger(0));
        int idx = Math.abs(counter.getAndIncrement()) % healthy.size();
        return healthy.get(idx);
    }

    /**
     * Entry point when a transport Connection arrives at L7.
     * We simulate TLS termination here by marking the connection as ESTABLISHED.
     * In a fuller sim we'd parse HTTP bytes from the connection; here we simply mark the connection.
     */
    @Override
    public void handleConnection(Connection connection) {
        if (connection == null) return;
        System.out.println("[" + name + "] Received transport connection " + connection.getId()
                + " from " + connection.getClientIp() + ":" + connection.getClientPort());
        // Simulate TLS handshake completion
        connection.establish();
        System.out.println("[" + name + "] Connection " + connection.getId() + " established (TLS terminated).");
        // In this simplified sim we expect requests to be created externally and passed to handleRequest(...)
    }

    /**
     * Main entry point for application requests.
     * - Performs routing by path prefix.
     * - Adds X-Forwarded-* headers.
     * - Forwards to a chosen backend (synchronously) and handles the Response.
     *
     * NOTE: this method currently logs the response and closes the connection if none exists.
     */
    @Override
    public void handleRequest(Request request, Connection connection) {
        if (request == null) return;

        String path = request.getPath();
        String prefix = findMatchingPrefix(path);

        if (prefix == null) {
            // No route matched -> 404 simulation
            System.out.println("[" + name + "] No route for path " + path + " -> 404");
            Response resp404 = new Response(404, "Not Found", "No route for " + path);
            resp404.setProducedBy(name);
            // Optionally write back to connection (simulated) or log
            System.out.println("[" + name + "] Response: " + resp404);
            return;
        }

        BackendServer backend = selectBackendForPrefix(prefix);
        if (backend == null) {
            System.out.println("[" + name + "] No healthy backend for prefix " + prefix + " -> 503");
            Response resp503 = new Response(503, "Service Unavailable", "No healthy backend for " + prefix);
            resp503.setProducedBy(name);
            System.out.println("[" + name + "] Response: " + resp503);
            return;
        }

        // Add/modify X-Forwarded headers
        if (connection != null) {
            request.setHeader("X-Forwarded-For", connection.getClientIp());
            request.setHeader("X-Forwarded-Proto", "https"); // or "http" depending on sim
        } else if (request.getClientIp() != null) {
            request.setHeader("X-Forwarded-For", request.getClientIp());
        }

        // Log routing decision
        System.out.println("[" + name + "] Routing " + path + " -> backend " + backend.getName());

        // Forward the request synchronously to backend and obtain a response
        Response backendResp = backend.serve(request, connection);

        // Post-process response (add header indicating LB)
        backendResp.setHeader("Via-LB", name);

        // Log response (in real system would be sent back to client via connection)
        System.out.println("[" + name + "] Received response from " + backend.getName() + ": " + backendResp);

        // Optionally update connection byte counters to simulate traffic (simple approach)
        if (connection != null && backendResp.getBody() != null) {
            // approximate bytes (length of body)
            int bytes = backendResp.getBody().getBytes().length;
            connection.addBytesToClient(bytes);
            connection.addBytesToBackend(0); // we could track request size similarly
        }

        // In this simple sim we don't stream the response back — we log it.
    }

    /**
     * Utility: list registered prefixes for inspection.
     */
    public Set<String> getRegisteredPrefixes() {
        return Collections.unmodifiableSet(routes.keySet());
    }

    /**
     * Utility: get backends for a prefix (defensive copy).
     */
    public List<BackendServer> getBackendsForPrefix(String prefix) {
        List<BackendServer> list = routes.get(prefix);
        return list == null ? Collections.emptyList() : new ArrayList<>(list);
    }
}
