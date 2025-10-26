package com.gowtham.lbsim.backend;

import com.gowtham.lbsim.model.Request;
import com.gowtham.lbsim.model.Response;
import com.gowtham.lbsim.net.Connection;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal simulated backend server.
 * 
 * Responsibilities:
 * - Accept application-level Request objects and produce a Response.
 * - Simulate processing latency and optional failures.
 * - Track currentConnections (simulated concurrency) for strategies like LeastConnections.
 *
 * Thread-safe for basic concurrent simulation.
 */
public class BackendServer {

    private final String name;       // human friendly name (e.g., "api-1")
    private final String host;       // backend IP or hostname (simulated)
    private final int port;          // backend port
    private volatile boolean healthy = true; // simple health flag
    private final AtomicInteger currentConnections = new AtomicInteger(0);
    private final int minLatencyMs;  // simulate processing latency lower bound
    private final int maxLatencyMs;  // simulate processing latency upper bound

    /**
     * Create a backend server with latency range (ms).
     *
     * @param name name shown in logs
     * @param host host/ip
     * @param port port
     * @param minLatencyMs minimum processing time in ms
     * @param maxLatencyMs maximum processing time in ms
     */
    public BackendServer(String name, String host, int port, int minLatencyMs, int maxLatencyMs) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.minLatencyMs = Math.max(0, minLatencyMs);
        this.maxLatencyMs = Math.max(this.minLatencyMs, maxLatencyMs);
    }

    /**
     * Simulate serving a request. This method:
     *  - increases currentConnections
     *  - waits for a simulated latency (sleep)
     *  - constructs a Response (status 200 by default)
     *  - decreases currentConnections
     *
     * In a realistic sim you might offload work to thread pools; here we do synchronous wait
     * so that upstream code can measure end-to-end timing easily.
     *
     * @param request request to serve
     * @param connection optional connection carrying the request (may be null)
     * @return simulated response
     */
    public Response serve(Request request, Connection connection) {
        currentConnections.incrementAndGet();
        try {
            // quick health check: if unhealthy, return 503 quickly
            if (!healthy) {
                Response resp = new Response(503, "Service Unavailable", "Backend " + name + " is unhealthy");
                resp.setProducedBy(name);
                return resp;
            }

            // simulate variable processing delay
            int delay = ThreadLocalRandom.current().nextInt(minLatencyMs, maxLatencyMs + 1);
            try {
                Thread.sleep(delay);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            // create a successful response
            Response resp = new Response();
            resp.setStatus(200);
            resp.setStatusMessage("OK");
            String body = "Handled by " + name + " (" + host + ":" + port + ") for path: " + request.getPath();
            resp.setBody(body);
            resp.setProducedBy(name);

            // optionally add a server header
            resp.setHeader("X-Backend-Server", name);

            return resp;

        } finally {
            currentConnections.decrementAndGet();
        }
    }

    // ---- Health controls ----
    public boolean isHealthy() {
        return healthy;
    }

    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }

    // ---- Metrics / introspection ----
    public int getCurrentConnections() {
        return currentConnections.get();
    }

    public String getName() {
        return name;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return "BackendServer{" +
                "name='" + name + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", healthy=" + healthy +
                ", currentConnections=" + currentConnections +
                ", latencyRange=[" + minLatencyMs + "," + maxLatencyMs + "]}";
    }
}
