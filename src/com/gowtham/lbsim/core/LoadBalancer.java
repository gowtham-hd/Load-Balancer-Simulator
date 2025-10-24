package com.gowtham.lbsim.core;

import com.gowtham.lbsim.model.Request;
import com.gowtham.lbsim.net.Connection;

/**
 * Common contract for all Load Balancer types in the simulator.
 *
 * - L4LoadBalancer handles Connection-level operations (transport layer).
 * - L7LoadBalancer handles Request-level operations (application layer).
 *
 * The default methods let concrete classes implement only the relevant methods.
 */
public interface LoadBalancer {

    /**
     * Handle an incoming transport-level connection (for L4).
     *
     * @param connection incoming simulated connection
     */
    default void handleConnection(Connection connection) {
        throw new UnsupportedOperationException("handleConnection not supported by " + getName());
    }

    /**
     * Handle an application-level request (for L7).
     *
     * @param request    application request
     * @param connection connection carrying the request (may be null if not modeled)
     */
    default void handleRequest(Request request, Connection connection) {
        throw new UnsupportedOperationException("handleRequest not supported by " + getName());
    }

    /**
     * Human-friendly name used in logs and metrics.
     *
     * @return name of this load balancer
     */
    String getName();
}
