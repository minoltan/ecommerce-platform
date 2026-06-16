# Docker & Kubernetes — Interview Questions & Answers

---

### Basic

**1. What is Docker? What is a container vs a virtual machine?**

Docker is a platform for packaging applications into containers — lightweight, portable units that include the app, its runtime, and dependencies.

- **Container** — shares the host OS kernel. Isolated via Linux namespaces and cgroups. Starts in milliseconds. Typically MBs in size.
- **Virtual Machine** — includes a full guest OS. Runs on a hypervisor. Starts in minutes. Typically GBs in size.

Containers are preferred for microservices: faster startup, lower overhead, consistent across environments.

---

**2. What is a `Dockerfile`? What do `FROM`, `COPY`, `RUN`, and `ENTRYPOINT` do?**

A `Dockerfile` is a script that defines how to build a Docker image.
- `FROM` — base image (e.g., `eclipse-temurin:21-jre-alpine`).
- `COPY` — copies files from the build context into the image.
- `RUN` — executes a command during build (e.g., `mvn package`).
- `ENTRYPOINT` — the command run when the container starts (e.g., `["java", "-jar", "/app/app.jar"]`).

---

**3. What is a multi-stage Docker build? What is the benefit in this project's `Dockerfile`?**

A multi-stage build uses multiple `FROM` statements. Each stage can discard its artifacts.

In the user-service `Dockerfile`:
```
Stage 1 (build): FROM maven:3.9-eclipse-temurin-21
  → copies pom.xml and src, runs mvn package, produces app.jar

Stage 2 (runtime): FROM eclipse-temurin:21-jre-alpine
  → copies only app.jar from stage 1
```

Benefits:
- Maven, source code, and build tools are not in the final image.
- Final image is ~200MB (JRE only) vs ~800MB+ (JDK + Maven).
- Smaller attack surface, faster pulls.

---

**4. What is Docker Compose? What does `docker-compose.infra.yml` start?**

Docker Compose is a tool for defining and running multi-container applications. `docker-compose.infra.yml` starts three containers:
- `ecommerce-mysql` — MySQL 8.0.39 on port 3306.
- `ecommerce-redis` — Redis 7 Alpine on port 6379.
- `ecommerce-kafka` — Confluent Kafka 7.6.1 (KRaft mode) on port 9092.

All share the `ecommerce-platform-infra_default` Docker network.

---

**5. What is a Docker volume? Why does `docker-compose.infra.yml` use `mysql-data`?**

A Docker volume is a persistent storage mechanism managed by Docker, surviving container restarts and `docker compose down`. `mysql-data` persists MySQL's data directory (`/var/lib/mysql`), so the `user_db` schema and data survive container restarts. Without it, every `docker compose up` would start with an empty database.

`docker compose down -v` removes the volume — wiping all data for a fresh start (useful when Flyway migration changes require a clean DB).

---

**6. What is the difference between `docker compose down` and `docker compose down -v`?**

- `down` — stops and removes containers and networks. **Preserves volumes** — data is retained.
- `down -v` — also removes named volumes (`mysql-data`). **All MySQL data is deleted.**

Use `down` for routine restarts. Use `down -v` to force a clean slate — e.g., after changing `V1__init.sql` on a dev machine where the migration has already been applied (Flyway checksum would fail otherwise).

---

### Intermediate

**7. What is Kubernetes? What problems does it solve over plain Docker?**

Kubernetes (k8s) is a container orchestration platform. Problems it solves:
- **Scheduling** — places containers on available nodes based on resource requests.
- **Self-healing** — restarts crashed containers, replaces failed nodes.
- **Scaling** — HPA scales replica count based on CPU/memory.
- **Service discovery** — DNS-based (`user-service.ecommerce.svc.cluster.local`).
- **Rolling deployments** — zero-downtime updates.
- **Config/secret management** — `ConfigMap` and `Secret` injected as env vars.

---

**8. What is a `Deployment` in Kubernetes? What does `replicas: 2` mean?**

A `Deployment` is a Kubernetes resource that declares the desired state for a set of `Pod`s. It manages a `ReplicaSet` to ensure the desired number of pod replicas are always running. `replicas: 2` means Kubernetes ensures exactly 2 pods running the `user-service:latest` image at all times. If one crashes, Kubernetes starts a replacement. The base `deployment.yaml` uses `replicas: 2`; the local overlay patches it to `replicas: 1` (single-node cluster).

---

**9. What is a `Service` in Kubernetes? What is `ClusterIP`?**

A `Service` provides a stable DNS name and virtual IP for a set of pods (which have ephemeral IPs). `ClusterIP` is the default service type — it creates an internal-only virtual IP accessible only within the cluster. The `user-service` Service exposes port 8081 internally:
```yaml
type: ClusterIP
ports:
  - port: 8081
    targetPort: http
```
Other services reach it at `http://user-service.ecommerce.svc.cluster.local:8081`.

---

**10. What is a `ConfigMap` vs a `Secret`? How are they used here?**

- `ConfigMap` — stores non-sensitive configuration as key-value pairs. Mounted as env vars or files.
- `Secret` — stores sensitive data (base64-encoded, not encrypted by default — use Sealed Secrets or external vaults for encryption at rest).

In this project:
- `user-service-config` (ConfigMap) — `DB_HOST`, `DB_PORT`, `REDIS_HOST`, `REDIS_PORT`, `KAFKA_BOOTSTRAP_SERVERS`.
- `user-service-secrets` (Secret) — `DB_USERNAME`, `DB_PASSWORD`, `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY`.

Both are referenced via `envFrom` in the Deployment, making them available as env vars to the container.

---

**11. What is Kustomize? How does `overlays/local` differ from `base`?**

Kustomize is a Kubernetes configuration management tool. It applies patches over a `base` directory without modifying the base files. `base` contains the canonical manifests (2 replicas, HPA, PDB, cluster DNS hostnames). `overlays/local` patches for a local single-node cluster:
- Replicas: `2 → 1`.
- Affinity: removed (irrelevant on one node).
- HPA and PDB: deleted.
- `ConfigMap` hostnames: overridden to `host.docker.internal` (points at Docker-hosted infra on the host machine).

---

**12. What is a liveness probe vs a readiness probe?**

- **Liveness probe** — "Is this container still alive?" If it fails repeatedly, Kubernetes restarts the container. Maps to `/actuator/health/liveness` (Spring Boot Kubernetes group: checks if the app is not deadlocked).
- **Readiness probe** — "Is this container ready to receive traffic?" If it fails, the pod is removed from the Service's load balancer endpoints. Maps to `/actuator/health/readiness` (Spring Boot Kubernetes group: checks if dependencies like DB are connected).

`initialDelaySeconds: 30` for liveness (JVM startup time) and `initialDelaySeconds: 10` for readiness.

---

**13. What is `host.docker.internal`? Why is it used in the `overlays/local` ConfigMap?**

`host.docker.internal` is a special DNS name that resolves to the host machine's IP from inside a Docker container or Kubernetes pod (on Docker Desktop and kind). The local overlay uses it because:
- Infra (MySQL, Redis, Kafka) runs in Docker Compose containers, exposed on the host at `localhost:3306`, `localhost:6379`, `localhost:9092`.
- A pod in Kubernetes cannot use `localhost` — that refers to the pod itself.
- `host.docker.internal` bridges the gap, letting pods reach the host-running infra.

---

**14. What is `podAntiAffinity`? Why is it configured in the base deployment?**

`podAntiAffinity` tells Kubernetes to prefer (or require) placing pods on different nodes. The base deployment uses `preferredDuringSchedulingIgnoredDuringExecution` with `topologyKey: kubernetes.io/hostname`:

```yaml
weight: 100
podAffinityTerm:
  labelSelector: app=user-service
  topologyKey: kubernetes.io/hostname
```

This spreads the 2 replicas across different nodes. If one node fails, the other pod (on a different node) keeps serving traffic. The local overlay removes this (single-node cluster has only one hostname).

---

**15. What is a HorizontalPodAutoscaler (HPA)?**

HPA automatically scales the number of pod replicas based on observed metrics (CPU, memory, or custom metrics). `base/hpa.yaml` defines autoscaling rules for the user-service. The local overlay deletes it because:
- Single-node clusters don't benefit from autoscaling.
- HPA requires metrics-server to be installed in the cluster.

In production (multi-node), HPA scales replicas 2→N when CPU exceeds the threshold, then scales back down when load drops.

---

**16. What is a PodDisruptionBudget (PDB)?**

A PDB ensures a minimum number of pods remain available during voluntary disruptions (node drains, cluster upgrades). `base/pdb.yaml` for user-service might specify `minAvailable: 1` — Kubernetes will not drain a node if doing so would leave 0 user-service pods running. The local overlay deletes it (irrelevant on a single node).

---

### Advanced

**17. Why must the JWT RSA key pair be set as a Kubernetes Secret rather than auto-generated per pod?**

`JwtKeyConfig` generates an ephemeral key pair at startup if `jwt.private-key` is blank. Each pod generates a different key. Pod A signs a JWT with its private key; pod B has a different public key and rejects it with 401. With 2 replicas and random routing, ~50% of requests would fail. The `Secret` provides the same key pair to all replicas via `envFrom: secretRef: user-service-secrets`.

---

**18. What is the difference between `envFrom` + `configMapRef` and mounting a ConfigMap as a volume?**

- `envFrom: configMapRef` — all keys become environment variables in the container. Simple; values are available as `System.getenv("DB_HOST")`. Spring Boot reads them via `${DB_HOST:localhost}`. Changes require pod restart.
- Volume mount — the ConfigMap is mounted as files in a directory. Useful for large configs (application.properties, certs). Files can be updated without pod restart (kubelet syncs changes within ~1 minute). Used for mounting TLS certificates.

This project uses `envFrom` for simplicity — all config values are small key-value pairs.

---

**19. What would happen if you applied `base/secret.yaml` with blank JWT keys to a 2-replica deployment?**

Each pod calls `JwtKeyConfig.jwtKeyPair()` at startup. Since `JWT_PRIVATE_KEY` is blank, `KeyPairGenerator.getInstance("RSA").generateKeyPair()` generates a new ephemeral RSA-2048 key pair. Each pod has a different key. Tokens signed by pod A fail validation on pod B. Approximately 50% of API requests (where the pod handling the request differs from the one that issued the token) return 401. The JWKS endpoint also returns different public keys per pod, breaking downstream services that cache the key set.

---

**20. How would you handle secret rotation (new DB password) with zero downtime?**

1. Create a new MySQL user with the new password.
2. Grant the same privileges as the old user.
3. Update the Kubernetes `Secret` with the new `DB_PASSWORD`.
4. Trigger a rolling deployment (`kubectl rollout restart deployment/user-service`).
5. Kubernetes gradually replaces old pods with new ones (one at a time). New pods start with the new password; old pods still use the old one. No downtime.
6. Once all pods are updated, revoke the old MySQL user.

Key: Kubernetes rolling update + a brief period where both old and new credentials are valid.

---

**21. What is `imagePullPolicy: Always` vs `IfNotPresent`? Which matters when using `user-service:latest`?**

- `IfNotPresent` — only pulls the image if it's not already cached on the node. Default for non-`latest` tags.
- `Always` — always pulls from the registry before starting a pod.

For `user-service:latest`, `Always` is critical — otherwise Kubernetes might use a stale cached version of `latest` and never pull the updated image. For production, always use specific immutable tags (e.g., `user-service:1.2.3`) with `IfNotPresent` to ensure reproducible deployments.
