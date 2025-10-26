package com.gowtham.lbsim.simulation;

import com.gowtham.lbsim.backend.BackendServer;
import com.gowtham.lbsim.core.L4LoadBalancer;
import com.gowtham.lbsim.core.L7LoadBalancer;
import com.gowtham.lbsim.model.Request;
import com.gowtham.lbsim.net.Connection;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

/**
 * Simple simulator runner to demonstrate L4 -> L7 -> Backend flow.
 *
 * How it works:
 * 1. Create L4 (public) and L7 (app) load balancers.
 * 2. Create 6 backend servers and register them: 3 for /api and 3 for /img.
 * 3. Register L7 as downstream of L4.
 * 4. Spawn multiple client tasks: each task creates a Connection, sends it to L4,
 *    then creates a Request (either /api or /img) and calls L7.handleRequest(request, connection).
 *
 * Run this as a Java application in Eclipse (Run As -> Java Application).
 */
public class Simulator {

    public static void main(String[] args) throws InterruptedException {
        // 1) Build L4 and L7
        L4LoadBalancer l4 = new L4LoadBalancer("L4-Main", "52.34.10.5", "10.0.0.2");
        L7LoadBalancer l7 = new L7LoadBalancer("App-L7");

        // Register L7 as downstream of L4
        l4.registerDownstream(l7);

        // 2) Create 6 backend servers (3 for /api and 3 for /img)
        BackendServer api1 = new BackendServer("api-1", "10.0.0.11", 8080, 20, 40);
        BackendServer api2 = new BackendServer("api-2", "10.0.0.12", 8080, 20, 60);
        BackendServer api3 = new BackendServer("api-3", "10.0.0.13", 8080, 15, 50);

        BackendServer img1 = new BackendServer("img-1", "10.0.0.21", 8080, 10, 30);
        BackendServer img2 = new BackendServer("img-2", "10.0.0.22", 8080, 10, 30);
        BackendServer img3 = new BackendServer("img-3", "10.0.0.23", 8080, 10, 30);

        // Register routes on L7
        l7.registerRoute("/api", Arrays.asList(api1, api2, api3));
        l7.registerRoute("/img", Arrays.asList(img1, img2, img3));

        // 3) Simulate concurrent clients
        int clients = 12;
        ExecutorService exec = Executors.newFixedThreadPool(6);
        CountDownLatch latch = new CountDownLatch(clients);

        for (int i = 1; i <= clients; i++) {
            final int id = i;
            exec.submit(() -> {
                try {
                    // build a synthetic client connection that targets L4
                    String clientIp = "203.0.113." + (100 + id);
                    int clientPort = 40000 + id;
                    Connection conn = new Connection(clientIp, clientPort, "52.34.10.5", 443, "TCP");
                    conn.setTag("client-" + id);

                    // Client opens connection to L4 (L4 will NAT and forward to L7)
                    l4.handleConnection(conn);

                    // Choose a path: alternate between /api and /img to show both routes
                    String path = (id % 2 == 0) ? "/img/photo" + id + ".jpg" : "/api/resource/" + id;

                    Request req = new Request("GET", path, null, clientIp, clientPort);

                    // Simulate a small random delay to better mix concurrent requests
                    Thread.sleep(ThreadLocalRandom.current().nextInt(5, 50));

                    // Now deliver the request to L7 (in real world L7 would parse it from the connection)
                    l7.handleRequest(req, conn);

                    // Optional: print which downstream L4 recorded for this connection (inspection)
                    System.out.println("[SIM] L4 mapped connection " + conn.getId()
                            + " to downstream " + l4.getDownstreamForConnection(conn.getId()).getName());

                } catch (Exception e) {
                    System.err.println("[SIM] client task error: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        // wait for tasks to finish
        latch.await();
        exec.shutdownNow();
        System.out.println("[SIM] All client tasks completed.");

        // Inspect registered prefixes and backends
        System.out.println("[SIM] Registered prefixes: " + l7.getRegisteredPrefixes());
        System.out.println("[SIM] Backends for /api: " + l7.getBackendsForPrefix("/api"));
        System.out.println("[SIM] Backends for /img: " + l7.getBackendsForPrefix("/img"));

        // Print some backend load stats
        List<BackendServer> apis = l7.getBackendsForPrefix("/api");
        for (BackendServer b : apis) {
            System.out.println("[SIM] " + b.getName() + " currentConnections=" + b.getCurrentConnections());
        }
        List<BackendServer> imgs = l7.getBackendsForPrefix("/img");
        for (BackendServer b : imgs) {
            System.out.println("[SIM] " + b.getName() + " currentConnections=" + b.getCurrentConnections());
        }

        System.out.println("[SIM] Done.");
    }
}
