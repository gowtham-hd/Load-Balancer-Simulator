package com.gowtham.lbsim.net;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simulated TCP-like connection object used by the LoadBalancerSim.
 *
 * Notes:
 * - This is NOT a real socket wrapper. It's a simulation artifact that carries
 *   addressing, lifecycle state, simple byte counters and timestamps.
 * - The L4LoadBalancer and L7LoadBalancer will accept/forward Connection objects.
 * - Keep it small and thread-safe where necessary.
 */
public class Connection {

    public enum State {
        NEW,        // just created, handshake not completed
        ESTABLISHED,// handshake completed, data may flow
        CLOSED      // closed / terminated
    }

    // Unique connection id for logging/tracing.
    private final String id = UUID.randomUUID().toString();

    // Client-side endpoint (original requester).
    private String clientIp;
    private int clientPort;

    // Destination endpoint (what client originally targeted; e.g., LB IP:port).
    private String destIp;
    private int destPort;

    // After NAT by L4, the mapped source may be different. NAT IP/port that LB uses.
    private String natIp;   // may be null until NAT applied
    private Integer natPort; // boxed so null is allowed

    // Protocol (e.g., "TCP" or "UDP" — mainly "TCP" in our sim).
    private final String protocol;

    // Connection lifecycle state
    private volatile State state = State.NEW;

    // Simple counters for simulated traffic (bytes transferred).
    private final AtomicLong bytesSentToBackend = new AtomicLong(0);
    private final AtomicLong bytesSentToClient = new AtomicLong(0);

    // Timestamps (millis) to measure latency / lifetime.
    private final long createdAt = System.currentTimeMillis();
    private volatile long establishedAt = -1;
    private volatile long closedAt = -1;

    // Optional field: a human-friendly tag (useful in logs)
    private String tag;

    // ---- Constructors ----

    /**
     * Create a connection representing a client connecting to a destination.
     *
     * @param clientIp  client's IP
     * @param clientPort client's port
     * @param destIp destination IP (LB public IP)
     * @param destPort destination port (LB listening port)
     * @param protocol transport protocol (usually "TCP")
     */
    public Connection(String clientIp, int clientPort, String destIp, int destPort, String protocol) {
        this.clientIp = clientIp;
        this.clientPort = clientPort;
        this.destIp = destIp;
        this.destPort = destPort;
        this.protocol = protocol == null ? "TCP" : protocol.toUpperCase();
    }

    // ---- Lifecycle methods ----

    /**
     * Mark connection as established (e.g., after TCP handshake).
     * Records timestamp.
     */
    public void establish() {
        if (state == State.NEW) {
            state = State.ESTABLISHED;
            establishedAt = System.currentTimeMillis();
        }
    }

    /**
     * Close the connection and set timestamp.
     */
    public void close() {
        state = State.CLOSED;
        closedAt = System.currentTimeMillis();
    }

    // ---- NAT helpers ----

    /**
     * Apply NAT mapping to this connection. Called by L4 when it rewrites source to LB NAT IP.
     *
     * @param natIp mapped NAT IP
     * @param natPort mapped NAT port
     */
    public void applyNat(String natIp, int natPort) {
        this.natIp = natIp;
        this.natPort = natPort;
    }

    /**
     * Remove NAT (optional utility).
     */
    public void clearNat() {
        this.natIp = null;
        this.natPort = null;
    }

    // ---- Traffic counters (thread-safe) ----

    public void addBytesToBackend(long bytes) {
        if (bytes > 0) bytesSentToBackend.addAndGet(bytes);
    }

    public void addBytesToClient(long bytes) {
        if (bytes > 0) bytesSentToClient.addAndGet(bytes);
    }

    public long getBytesSentToBackend() {
        return bytesSentToBackend.get();
    }

    public long getBytesSentToClient() {
        return bytesSentToClient.get();
    }

    // ---- Getters / setters ----

    public String getId() {
        return id;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public int getClientPort() {
        return clientPort;
    }

    public void setClientPort(int clientPort) {
        this.clientPort = clientPort;
    }

    public String getDestIp() {
        return destIp;
    }

    public void setDestIp(String destIp) {
        this.destIp = destIp;
    }

    public int getDestPort() {
        return destPort;
    }

    public void setDestPort(int destPort) {
        this.destPort = destPort;
    }

    public String getNatIp() {
        return natIp;
    }

    public Integer getNatPort() {
        return natPort;
    }

    public String getProtocol() {
        return protocol;
    }

    public State getState() {
        return state;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getEstablishedAt() {
        return establishedAt;
    }

    public long getClosedAt() {
        return closedAt;
    }

    public String getTag() {
        return tag;
    }

    /**
     * Optional human-friendly tag to correlate logs (e.g., "client-203-1" or "test-run-1")
     */
    public void setTag(String tag) {
        this.tag = tag;
    }

    // ---- Utilities ----

    /**
     * Lifetime in millis (if closed), or current duration if still open.
     */
    public long getLifetimeMillis() {
        if (closedAt > 0) return closedAt - createdAt;
        return System.currentTimeMillis() - createdAt;
    }

    @Override
    public String toString() {
        return "Connection{" +
                "id='" + id + '\'' +
                ", client=" + clientIp + ":" + clientPort +
                ", dest=" + destIp + ":" + destPort +
                (natIp != null ? ", nat=" + natIp + ":" + natPort : "") +
                ", proto=" + protocol +
                ", state=" + state +
                ", bytesToBackend=" + bytesSentToBackend +
                ", bytesToClient=" + bytesSentToClient +
                ", createdAt=" + createdAt +
                '}';
    }
}
