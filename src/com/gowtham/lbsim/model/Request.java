package com.gowtham.lbsim.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Objects;

/**
 * Represents an incoming HTTP-like request in the LoadBalancer simulator.
 *
 * Notes:
 * - This is intentionally mutable (setters) to match your stated preference
 *   for traditional Java object construction.
 * - Headers are stored in a ConcurrentHashMap to be safe when multiple threads
 *   inspect/augment headers (LBs often add X-Forwarded-* headers).
 */
public class Request {

    // HTTP method (GET, POST, PUT, DELETE...).
    private String method;

    // The request path (e.g. "/api/events").
    private String path;

    // Simple body as String for simulator purposes (could be binary in real systems).
    private String body;

    // Request headers. ConcurrentHashMap chosen for thread-safety in simulation.
    private final Map<String, String> headers = new ConcurrentHashMap<>();

    // Client networking info (useful for X-Forwarded-For, logging, sticky-mapping).
    private String clientIp;
    private int clientPort;

    // Request creation timestamp in millis - useful for latency metrics.
    private final long timestamp;

    // ---- Constructors ----

    public Request() {
        this.timestamp = System.currentTimeMillis();
    }

    public Request(String method, String path, String body, String clientIp, int clientPort) {
        this.method = method;
        this.path = path;
        this.body = body;
        this.clientIp = clientIp;
        this.clientPort = clientPort;
        this.timestamp = System.currentTimeMillis();
    }

    // ---- Getters / Setters ----

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    /**
     * Returns a live reference to the headers map. This design keeps the class
     * simple in the simulator — callers can add/remove headers directly.
     */
    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getHeader(String name) {
        return headers.get(name);
    }

    public void setHeader(String name, String value) {
        if (value == null) {
            headers.remove(name);
        } else {
            headers.put(name, value);
        }
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

    public long getTimestamp() {
        return timestamp;
    }

    // ---- Convenience / utility ----

    @Override
    public String toString() {
        return "Request{" +
                "method='" + method + '\'' +
                ", path='" + path + '\'' +
                ", clientIp='" + clientIp + '\'' +
                ", clientPort=" + clientPort +
                ", headers=" + headers +
                ", timestamp=" + timestamp +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Request request = (Request) o;
        return clientPort == request.clientPort &&
                Objects.equals(method, request.method) &&
                Objects.equals(path, request.path) &&
                Objects.equals(body, request.body) &&
                Objects.equals(headers, request.headers) &&
                Objects.equals(clientIp, request.clientIp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, path, body, headers, clientIp, clientPort);
    }
}

