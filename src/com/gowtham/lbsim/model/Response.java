package com.gowtham.lbsim.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Objects;

/**
 * Simple HTTP-like response used in the LoadBalancer simulator.
 *
 * - Minimal, intentionally simple: status, headers, body, and a timestamp.
 * - Mutable via setters to match your preference for traditional Java objects.
 * - Headers use ConcurrentHashMap to be thread-safe in the simulator when middleware/LBs
 *   modify headers concurrently.
 */
public class Response {

    // HTTP-style numeric status code (200, 404, 500, etc.)
    private int status = 200;

    // Status message (OK, Not Found, Internal Server Error)
    private String statusMessage = "OK";

    // Response body as a String (sufficient for simulation)
    private String body;

    // Response headers (thread-safe container)
    private final Map<String, String> headers = new ConcurrentHashMap<>();

    // Timestamp when response was created (ms)
    private final long timestamp;

    // Optional field: which backend produced this response (for logging)
    private String producedBy;

    public Response() {
        this.timestamp = System.currentTimeMillis();
    }

    public Response(int status, String statusMessage, String body) {
        this.status = status;
        this.statusMessage = statusMessage;
        this.body = body;
        this.timestamp = System.currentTimeMillis();
    }

    // ---- Getters / Setters ----

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getHeader(String name) {
        return headers.get(name);
    }

    public void setHeader(String name, String value) {
        if (value == null) headers.remove(name);
        else headers.put(name, value);
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getProducedBy() {
        return producedBy;
    }

    public void setProducedBy(String producedBy) {
        this.producedBy = producedBy;
    }

    @Override
    public String toString() {
        return "Response{" +
                "status=" + status +
                ", statusMessage='" + statusMessage + '\'' +
                ", body='" + body + '\'' +
                ", headers=" + headers +
                ", timestamp=" + timestamp +
                ", producedBy='" + producedBy + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Response response = (Response) o;
        return status == response.status &&
                Objects.equals(statusMessage, response.statusMessage) &&
                Objects.equals(body, response.body) &&
                Objects.equals(headers, response.headers) &&
                Objects.equals(producedBy, response.producedBy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, statusMessage, body, headers, producedBy);
    }
}
