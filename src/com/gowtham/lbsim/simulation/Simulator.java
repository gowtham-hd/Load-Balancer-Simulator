package com.gowtham.lbsim.simulation;

import com.gowtham.lbsim.backend.BackendServer;
import com.gowtham.lbsim.core.L4LoadBalancer;
import com.gowtham.lbsim.core.L7LoadBalancer;
import com.gowtham.lbsim.model.Request;
import com.gowtham.lbsim.net.Connection;
import com.gowtham.lbsim.strategy.LeastConnectionsStrategy;
import com.gowtham.lbsim.strategy.RoundRobinStrategy;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

/**
 * Simulator demonstrating per-route strategies:
 * - /api => LeastConnections
 * - /img => RoundRobin
 */
public class Simulator {

    public static void main(String[] args) throws InterruptedException {
        L4LoadBalancer l4 = new L4LoadBalancer("L4-Main", "52.34.10.5", "10.0.0.2");
        L7LoadBalancer l7 = new L7LoadBalancer("App-L7");
        l4.registerDownstream(l7);

        // Backends
        BackendServer api1 = new BackendServer("api-1", "10.0.0.11", 8080, 50, 150);
        BackendServer api2 = new BackendServer("api-2", "10.0.0.12", 8080, 20, 80);
        BackendServer api3 = new BackendServer("api-3", "10.0.0.13", 8080, 20, 80);

        BackendServer img1 = new BackendServer("img-1", "10.0.0.21", 8080, 10, 40);
        BackendServer img2 = new BackendServer("img-2", "10.0.0.22", 8080, 10, 40);
        BackendServer img3 = new BackendServer("img-3", "10.0.0.23", 8080, 10, 40);

        // Register routes with strategies
        l7.registerRoute("/api", Arrays.asList(api1, api2, api3), new LeastConnectionsStrategy());
        l7.registerRoute("/img", Arrays.asList(img1, img2, img3), new RoundRobinStrategy());

        // simulate concurrent clients
        int clients = 24;
        ExecutorService exec = Executors.newFixedThreadPool(8);
        CountDownLatch latch = new CountDownLatch(clients);

        for (int i = 1; i <= clients; i++) {
            final int id = i;
            exec.submit(() -> {
                try {
                    String clientIp = "203.0.113." + (100 + id);
                    int clientPort = 40000 + id;
                    Connection conn = new Connection(clientIp, clientPort, "52.34.10.5", 443, "TCP");
                    conn.setTag("client-" + id);

                    // connect via L4
                    l4.handleConnection(conn);

                    // alternate paths, but bias api a little for load test
                    String path;
                    if (id % 3 != 0) path = "/api/resource/" + id; // more /api
                    else path = "/img/photo" + id + ".jpg";

                    Request req = new Request("GET", path, null, clientIp, clientPort);

                    // small jitter
                    Thread.sleep(ThreadLocalRandom.current().nextInt(5, 60));

                    // deliver request to L7
                    l7.handleRequest(req, conn);

                    // print which downstream L4 recorded for this connection
                    System.out.println("[SIM] L4 mapped " + conn.getId()
                            + " -> " + l4.getDownstreamForConnection(conn.getId()).getName());

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        exec.shutdownNow();

        // summary
        System.out.println("[SIM] All tasks finished.");
        System.out.println("[SIM] /api backends:");
        List<BackendServer> apis = l7.getBackendsForPrefix("/api");
        for (BackendServer b : apis) {
            System.out.println("  " + b.getName() + " currentConnections=" + b.getCurrentConnections());
        }
        System.out.println("[SIM] /img backends:");
        List<BackendServer> imgs = l7.getBackendsForPrefix("/img");
        for (BackendServer b : imgs) {
            System.out.println("  " + b.getName() + " currentConnections=" + b.getCurrentConnections());
        }
        System.out.println("[SIM] Done.");
    }
}
