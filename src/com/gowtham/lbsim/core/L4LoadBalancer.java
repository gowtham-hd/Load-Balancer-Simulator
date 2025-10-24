package com.gowtham.lbsim.core;

import com.gowtham.lbsim.net.Connection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A simple L4 (transport-level) load balancer simulation.
 *
 * Responsibilities:
 * - Accept Connection objects from clients.
 * - Apply a basic NAT mapping to simulate LB source rewriting.
 * - Choose a downstream target (another LoadBalancer, typically an L7) using
 *   a simple Round-Robin selector.
 * - Forward the Connection to the selected downstream by calling its handleConnection().
 *
 * This class keeps the implementation intentionally simple so you can iterate
 * quickly. Later we will extend it with different selection strategies,
 * connection tables, health checks, or forwarding directly to backend servers.
 */
public class L4LoadBalancer implements LoadBalancer {

    // Human-friendly name for logging/metrics.
    private final String name;

    // "Public" IP that clients connect to (simulated).
    private final String publicIp;

    // NAT pool base (simulated private IP range used when applying NAT).
    // In a real LB, NAT mapping may reuse same IP and change ports or use internal IPs.
    private final String natBaseIp;

    // Downstream targets (usually L7 load balancers or backend endpoints exposed as LoadBalancer).
    // We use a list so we can run Round-Robin across them.
    private final List<LoadBalancer> downstreams = new ArrayList<>();

    // Round-robin counter (thread-safe)
    private final AtomicInteger rrCounter = new AtomicInteger(0);

    // Connection mapping table: connectionId -> downstream chosen (for tracking / reverse mapping)
    private final Map<String, LoadBalancer> connectionTable = new ConcurrentHashMap<>();

    /**
     * Create an L4LoadBalancer.
     *
     * @param name     display name
     * @param publicIp public IP clients connect to (string)
     * @param natBaseIp NAT base ip used to simulate NAT mapping (e.g., "10.0.0.2")
     */
    public L4LoadBalancer(String name, String publicIp, String natBaseIp) {
        this.name = name;
        this.publicIp = publicIp;
        this.natBaseIp = natBaseIp;
    }

    /**
     * Register a downstream target (L7 LB or backend wrapped as LoadBalancer).
     */
    public synchronized void registerDownstream(LoadBalancer lb) {
        if (lb == null) return;
        downstreams.add(lb);
    }

    /**
     * Deregister (remove) a downstream target.
     */
    public synchronized void deregisterDownstream(LoadBalancer lb) {
        downstreams.remove(lb);
    }

    /**
     * Select a downstream using simple round-robin.
     * If no downstream available, returns null.
     */
    protected LoadBalancer selectDownstream() {
        int size = downstreams.size();
        if (size == 0) return null;
        int idx = Math.abs(rrCounter.getAndIncrement()) % size;
        return downstreams.get(idx);
    }

    /**
     * Simulate applying NAT for the incoming connection.
     * This method rewrites the connection's NAT fields to indicate the LB mapping.
     */
    protected void applyNatForConnection(Connection connection) {
        // For simplicity: keep the same NAT IP and use a port derived from connection id hash.
        // In a more advanced sim you might keep a pool of ports and reuse them.
        int derivedPort = Math.abs(connection.getId().hashCode()) % 40000 + 20000; // 20000..59999
        connection.applyNat(natBaseIp, derivedPort);
    }

    /**
     * Main entry point for L4: accept a connection and forward it to a downstream.
     * This implements the LoadBalancer.handleConnection contract.
     */
    @Override
    public void handleConnection(Connection connection) {
        if (connection == null) return;

        // Log basic accept - replace with your logger if needed.
        System.out.println("[" + name + "] Accepted connection " + connection.getId()
                + " from " + connection.getClientIp() + ":" + connection.getClientPort()
                + " -> dest " + connection.getDestIp() + ":" + connection.getDestPort());

        // Simulate NAT mapping
        applyNatForConnection(connection);
        System.out.println("[" + name + "] Applied NAT: " + connection.getNatIp() + ":" + connection.getNatPort());

        // Select downstream L7 (or backend) to forward to
        LoadBalancer downstream = selectDownstream();
        if (downstream == null) {
            System.out.println("[" + name + "] No downstreams registered. Closing connection " + connection.getId());
            connection.close();
            return;
        }

        // Register mapping for the connection so we can trace which downstream owns it
        connectionTable.put(connection.getId(), downstream);

        // Forward - call downstream.handleConnection. We assume downstream knows how to accept connections
        try {
            System.out.println("[" + name + "] Forwarding connection " + connection.getId()
                    + " to downstream " + downstream.getName());
            downstream.handleConnection(connection);
        } catch (UnsupportedOperationException uoe) {
            // If downstream does not implement handleConnection, we catch and show message.
            System.err.println("[" + name + "] Downstream " + downstream.getName()
                    + " doesn't support handleConnection(): " + uoe.getMessage());
            // Optionally attempt alternate behavior: if downstream supports handleRequest only,
            // you could simulate a request parse here and call handleRequest (not done by default).
            connection.close();
        }
    }

    @Override
    public String getName() {
        return name;
    }

    // ---- Utility methods for testing / inspection ----

    /**
     * Return which downstream a connection was forwarded to (or null).
     */
    public LoadBalancer getDownstreamForConnection(String connectionId) {
        return connectionTable.get(connectionId);
    }

    /**
     * Return registered downstreams (copy to avoid external modification).
     */
    public List<LoadBalancer> getDownstreams() {
        return new ArrayList<>(downstreams);
    }
}
