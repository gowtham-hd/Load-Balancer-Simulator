package com.gowtham.lbsim.core;

import com.gowtham.lbsim.backend.BackendServer;
import com.gowtham.lbsim.model.Request;
import com.gowtham.lbsim.model.Response;
import com.gowtham.lbsim.net.Connection;
import com.gowtham.lbsim.strategy.RoundRobinStrategy;
import com.gowtham.lbsim.strategy.Strategy;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L7 load balancer updated to use pluggable Strategy implementations.
 *
 * - Each registered route can have its own Strategy (RoundRobin, LeastConnections, etc).
 * - If no strategy specified for a route, the LB uses defaultStrategy (RoundRobin).
 * - Strategy.select(routeKey, request, candidates) is responsible for making selection.
 */
public class L7LoadBalancer implements LoadBalancer {

    private final String name;

    // routePrefix -> ordered list of backends for that prefix
    private final Map<String, List<BackendServer>> routes = new ConcurrentHashMap<>();

    // routePrefix -> strategy; if absent, defaultStrategy is used
    private final Map<String, Strategy> routeStrategies = new ConcurrentHashMap<>();

    // default strategy (Round Robin)
    private final Strategy defaultStrategy = new RoundRobinStrategy();

    public L7LoadBalancer(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * Register a route with default strategy (RoundRobin).
     */
    public void registerRoute(String pathPrefix, List<BackendServer> backends) {
        registerRoute(pathPrefix, backends, null);
    }

    /**
     * Register a route and optionally provide a Strategy (null -> use default).
     */
    public void registerRoute(String pathPrefix, List<BackendServer> backends, Strategy strategy) {
        if (pathPrefix == null || !pathPrefix.startsWith("/")) {
            throw new IllegalArgumentException("pathPrefix must start with '/'");
        }
        if (backends == null) backends = Collections.emptyList();
        List<BackendServer> copy = new ArrayList<>(backends);
        routes.put(pathPrefix, copy);
        if (strategy != null) routeStrategies.put(pathPrefix, strategy);
        else routeStrategies.remove(pathPrefix);
    }

    public void deregisterRoute(String pathPrefix) {
        routes.remove(pathPrefix);
        routeStrategies.remove(pathPrefix);
    }

    protected String findMatchingPrefix(String path) {
        if (path == null) return null;
        String matched = null;
        for (String prefix : routes.keySet()) {
            if (path.startsWith(prefix)) {
                if (matched == null || prefix.length() > matched.length()) matched = prefix;
            }
        }
        return matched;
    }

    /**
     * Delegates selection to the configured strategy for the prefix (or default).
     * Filters out unhealthy backends before calling the strategy.
     */
    protected BackendServer selectBackendForPrefix(String prefix, Request request) {
        List<BackendServer> list = routes.get(prefix);
        if (list == null || list.isEmpty()) return null;

        // Filter healthy backends
        List<BackendServer> healthy = new ArrayList<>();
        for (BackendServer b : list) {
            if (b != null && b.isHealthy()) healthy.add(b);
        }
        if (healthy.isEmpty()) return null;

        Strategy strategy = routeStrategies.getOrDefault(prefix, defaultStrategy);
        return strategy.select(prefix, request, healthy);
    }

    /**
     * Accept a transport-level connection and simulate TLS handshake completion.
     */
    @Override
    public void handleConnection(Connection connection) {
        if (connection == null) return;
        System.out.println("[" + name + "] Received transport connection " + connection.getId()
                + " from " + connection.getClientIp() + ":" + connection.getClientPort());
        connection.establish();
        System.out.println("[" + name + "] Connection " + connection.getId() + " established (TLS terminated).");
    }

    /**
     * Handle an application request:
     *  - find route prefix
     *  - select backend via strategy
     *  - set X-Forwarded headers
     *  - forward to backend and log response
     */
    @Override
    public void handleRequest(Request request, Connection connection) {
        if (request == null) return;

        String path = request.getPath();
        String prefix = findMatchingPrefix(path);

        if (prefix == null) {
            Response resp404 = new Response(404, "Not Found", "No route for " + path);
            resp404.setProducedBy(name);
            System.out.println("[" + name + "] No route for path " + path + " -> 404");
            System.out.println("[" + name + "] Response: " + resp404);
            return;
        }

        BackendServer backend = selectBackendForPrefix(prefix, request);
        if (backend == null) {
            Response resp503 = new Response(503, "Service Unavailable", "No healthy backend for " + prefix);
            resp503.setProducedBy(name);
            System.out.println("[" + name + "] No healthy backend for prefix " + prefix + " -> 503");
            System.out.println("[" + name + "] Response: " + resp503);
            return;
        }

        // Add X-Forwarded headers
        if (connection != null) {
            request.setHeader("X-Forwarded-For", connection.getClientIp());
            request.setHeader("X-Forwarded-Proto", "https");
        } else if (request.getClientIp() != null) {
            request.setHeader("X-Forwarded-For", request.getClientIp());
        }

        System.out.println("[" + name + "] Routing " + path + " -> backend " + backend.getName());

        // Synchronously serve and post-process
        Response backendResp = backend.serve(request, connection);
        backendResp.setHeader("Via-LB", name);

        System.out.println("[" + name + "] Received response from " + backend.getName() + ": " + backendResp);

        if (connection != null && backendResp.getBody() != null) {
            int bytes = backendResp.getBody().getBytes().length;
            connection.addBytesToClient(bytes);
            // connection.addBytesToBackend(...) could be used for request size
        }
    }

    // Utilities
    public Set<String> getRegisteredPrefixes() {
        return Collections.unmodifiableSet(routes.keySet());
    }

    public List<BackendServer> getBackendsForPrefix(String prefix) {
        List<BackendServer> list = routes.get(prefix);
        return list == null ? Collections.emptyList() : new ArrayList<>(list);
    }
}
