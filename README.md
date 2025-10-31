# ⚙️ LoadBalancerSim — Java Load Balancer Simulator

**LoadBalancerSim** is a Java-based simulation project that demonstrates how **L4 (Transport-level)** and **L7 (Application-level)** load balancers work internally.

It models realistic traffic flow — from clients opening TCP connections → L4 NAT translation → L7 request routing → backend servers responding — with support for multiple **load-balancing strategies** like *Round Robin* and *Least Connections*.

---

## 🚀 Project Overview

### 🔹 What it does
- Simulates **L4 (network)** and **L7 (application)** load balancing in pure Java.
- Implements **pluggable strategies**:  
  - 🌀 **Round Robin**  
  - ⚖️ **Least Connections**  
  - 🧮 (Optional) Weighted Least Connections  
- Tracks active connections and response latency per backend.
- Logs every step of the routing process:
  - Connection acceptance & NAT mapping (L4)
  - TLS termination & request handling (L7)
  - Backend response & status
- Provides a multithreaded simulator to emulate many concurrent clients.

---

## 🧱 Architecture

### Layers

| Layer | Component | Role |
|--------|------------|------|
| **L4 (Transport)** | `L4LoadBalancer` | Simulates TCP connection acceptance, NAT mapping, and forwards to downstream L7. |
| **L7 (Application)** | `L7LoadBalancer` | Terminates TLS, parses request paths, applies routing strategy, forwards to backends. |
| **Backend Servers** | `BackendServer` | Simulated API/image servers handling requests with latency and health states. |
| **Clients** | `Simulator` | Spawns concurrent threads generating `/api` and `/img` requests to test balancing. |

## 🧮 Load Balancing Strategies

### 1️⃣ RoundRobinStrategy
- Rotates requests evenly among available healthy backends per route.
- Maintains a thread-safe counter per route using `AtomicInteger`.

### 2️⃣ LeastConnectionsStrategy
- Chooses the backend currently handling the fewest active requests.
- Prefers faster or less busy servers dynamically.

## 🧰 How to Run

### ✅ Prerequisites
- **Java 17+**
- Any IDE (e.g., Eclipse, IntelliJ) or CLI with `javac` / `java`.

### ▶️ Steps

1. Clone the repo:
   ```bash
   git clone https://github.com/gowtham-hd/LoadBalancerSim.git
   cd LoadBalancerSim

2. Open in your IDE.

3. Run the class:
4. com.gowtham.lbsim.simulation.Simulator

🧠 Design Highlights

Thread-safe simulation: uses AtomicInteger and ConcurrentHashMap.

Pluggable strategy pattern: easily extend to Weighted RR, P2C, etc.

Connection simulation: Connection tracks bytes, NAT ports, and state.

Backend realism: configurable latency ranges + health toggle.


Design Patterns Used in the Project:
This project intentionally uses small, well-known design patterns to keep the code modular, testable and extendable:

### 1. Strategy Pattern
**Where:** `com.gowtham.lbsim.strategy` (`Strategy`, `RoundRobinStrategy`, `LeastConnectionsStrategy`, `WeightedLeastConnectionsStrategy`)  
**What it does:** Encapsulates backend-selection algorithms as interchangeable objects.  
**Why used:** Lets `L7LoadBalancer` remain strategy-agnostic and enables easy addition of new selection policies (P2C, Weighted RR, etc.) without changing LB code.  
**Example:** `L7LoadBalancer` delegates selection to `strategy.select(routeKey, request, candidates)`.

---

### 2. Interface-based Polymorphism (Open/Closed + Dependency Inversion)
**Where:** `com.gowtham.lbsim.core.LoadBalancer` (interface), `L4LoadBalancer`, `L7LoadBalancer`, `BackendServer`  
**What it does:** Defines contracts (interfaces) and programs to the interface, not implementation.  
**Why used:** Allows swapping L4/L7 implementations, registering downstream LBs, and testing components independently. Enables the simulator to forward `Connection`/`Request` to any `LoadBalancer` implementation.

---

### 3. Chain of Responsibility (simple pipeline)
**Where:** L4 → L7 forwarding chain (`L4LoadBalancer` forwards accepted `Connection` to registered downstream LBs like `L7LoadBalancer`)  
**What it does:** Request/connection flows through layered handlers (transport-level then application-level).  
**Why used:** Models real-world networking stacks (NLB → ALB) and keeps concerns separated (NAT & TCP handling in L4, routing & TLS in L7).

---

### 4. Dependency Injection (manual)
**Where:** `Simulator` wiring: creating `BackendServer` instances and passing them into `L7LoadBalancer.registerRoute(...)`; registering `L7` as downstream on `L4`.  
**What it does:** External code constructs components and injects dependencies rather than hard-coding them inside classes.  
**Why used:** Makes components configurable, easier to test, and easier to experiment with different topologies/strategies.

---

### 5. Defensive Copying & Thread-safe State
**Where:** `L7LoadBalancer.registerRoute(...)` (defensive copy of backend lists); `BackendServer`/strategies use `AtomicInteger` and `ConcurrentHashMap`.  
**What it does:** Prevents external mutation and ensures safe concurrent access.  
**Why used:** Keeps internal LB state consistent in a multithreaded simulation and avoids subtle concurrency bugs.

---

🔮 Possible Extensions

✅ Weighted Round Robin
✅ Power-of-Two-Choices (P2C)
✅ Sticky sessions (ConnectionMapper)
✅ Health checks (periodic)
✅ Metrics dashboard/visualization
✅ JSON config for backend pools

