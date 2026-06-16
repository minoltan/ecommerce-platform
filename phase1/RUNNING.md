# Phase 1 — Running Services Locally

Three ways to run any service: JVM + Docker infra (fastest for development), full Docker, or Kubernetes.

---

## Prerequisites

| Tool | Purpose |
|---|---|
| Java 21 | `mvn spring-boot:run` |
| Maven 3.9+ | Build tool (or use `./mvnw` wrapper) |
| Docker + Docker Compose | Infra and container builds |
| kubectl + Kustomize | Kubernetes deployments |
| kind / minikube / k3d | Local Kubernetes cluster |
| openssl | RSA key pair generation (k8s only) |

---

## Infrastructure (shared by all services)

All services depend on the same local infra stack. Start it once regardless of which run mode you choose.

```bash
# Start MySQL, Redis, Kafka (from repo root)
docker compose -f docker-compose.infra.yml up -d

# Check health (MySQL takes ~10s to initialise)
docker compose -f docker-compose.infra.yml ps

# Stop — keep data
docker compose -f docker-compose.infra.yml down

# Stop — wipe all data
docker compose -f docker-compose.infra.yml down -v
```

| Container | Port | Notes |
|---|---|---|
| `ecommerce-mysql` | 3306 | Schema + user created automatically via `infra/mysql/init/` scripts |
| `ecommerce-redis` | 6379 | |
| `ecommerce-kafka` | 9092 | KRaft mode, single broker, auto-creates topics |

### MySQL schemas

Each service owns its own schema and a MySQL user scoped to it (ADR-0008). Init scripts in `infra/mysql/init/` run automatically on first container start.

| Service | Schema | User | Init script |
|---|---|---|---|
| User/Auth | `user_db` | `user_service` | `infra/mysql/init/01-user-db.sql` |

When scaffolding a new service, add its `CREATE DATABASE` / `CREATE USER` / `GRANT` script here. If the container was already initialised, either run the SQL manually or `down -v` to re-init.

```bash
# Connect to MySQL
docker exec -it ecommerce-mysql mysql -u user_service -pchangeme user_db

# Or via host client (port 3306 is forwarded)
mysql -h 127.0.0.1 -P 3306 -u user_service -pchangeme user_db
```

---

## Option 1 — `mvn spring-boot:run` (recommended for development)

Infra runs in Docker; the service runs directly on your JVM. All `application.yml` defaults point to `localhost` — no extra environment variables needed.

```bash
# From phase1/
./mvnw spring-boot:run -pl user-service
```

Flyway applies pending migrations automatically on first boot. The service is ready when you see:
```
Started UserServiceApplication in X.XXX seconds
```

### Run a single test class

```bash
./mvnw test -pl user-service -Dtest=AuthControllerIntegrationTest
```

---

## Option 2 — Docker

**Step 1 — Build the image**

The `Dockerfile` uses a two-stage build and must be executed from `phase1/` as the build context (it copies the parent POM):

```bash
# From phase1/
docker build -f user-service/Dockerfile -t user-service:latest .
```

**Step 2 — Run the service container**

```bash
docker run --rm -d \
  --name user-service \
  -p 8081:8081 \
  -e DB_HOST=host.docker.internal \
  -e REDIS_HOST=host.docker.internal \
  -e KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9092 \
  user-service:latest
```

> `host.docker.internal` resolves to the host machine from inside a container, reaching the ports exposed by the infra compose services.

```bash
# Follow logs
docker logs -f user-service

# Stop
docker stop user-service
```

---

## Option 3 — Kubernetes (kind / minikube / k3d)

The `k8s/overlays/local` overlay sets 1 replica, removes HPA/PDB, and points infra hosts at `host.docker.internal`.

**Step 1 — Build and load the image into the cluster**

```bash
docker build -f phase1/user-service/Dockerfile -t user-service:latest phase1/

# kind
kind load docker-image user-service:latest

# minikube
minikube image load user-service:latest

# k3d
k3d image import user-service:latest
```

**Step 2 — Create the namespace**

```bash
kubectl create namespace ecommerce
```

**Step 3 — Create the Secret**

`base/secret.yaml` is a placeholder — do not apply it with blank JWT keys. With more than one replica, each pod would generate an ephemeral RSA key and cross-pod JWT verification would fail.

```bash
# Generate RSA key pair
openssl genrsa -out jwt-private.pem 2048
openssl rsa -in jwt-private.pem -pubout -out jwt-public.pem

# Create secret (kubectl reads the PEM files directly)
kubectl -n ecommerce create secret generic user-service-secrets \
  --from-literal=DB_USERNAME=user_service \
  --from-literal=DB_PASSWORD=changeme \
  --from-file=JWT_PRIVATE_KEY=jwt-private.pem \
  --from-file=JWT_PUBLIC_KEY=jwt-public.pem
```

> Keep `jwt-private.pem` out of git. Delete the files after loading.

**Step 4 — Apply the overlay**

```bash
kubectl apply -k phase1/user-service/k8s/overlays/local
```

**Step 5 — Verify**

```bash
kubectl -n ecommerce get pods,svc

# Wait for rollout
kubectl -n ecommerce rollout status deployment/user-service

# Forward port and check health
kubectl -n ecommerce port-forward svc/user-service 8081:8081
curl http://localhost:8081/actuator/health
```

**Tear down**

```bash
kubectl delete -k phase1/user-service/k8s/overlays/local
kubectl delete namespace ecommerce
```

---

## Health & API endpoints

| Path | Purpose |
|---|---|
| `GET /actuator/health` | Overall health |
| `GET /actuator/health/liveness` | Kubernetes liveness probe |
| `GET /actuator/health/readiness` | Kubernetes readiness probe |
| `GET /api/v1/auth/jwks` | Public JWKS endpoint (JWT verification) |
| `POST /api/v1/auth/register` | Register a new user |
| `POST /api/v1/auth/login` | Login — returns access + refresh tokens |
| `POST /api/v1/auth/refresh` | Rotate refresh token |
| `POST /api/v1/auth/logout` | Blacklist access token |
| `POST /api/v1/auth/verify-email` | Email verification |
| `GET /api/v1/admin/users` | List users (ADMIN role) |
| `PATCH /api/v1/admin/users/{id}/deactivate` | Deactivate user (ADMIN role) |

---

## Environment variable reference

All variables have `localhost` defaults — Option 1 works with zero configuration.

| Variable | Default | Description |
|---|---|---|
| `DB_HOST` | `localhost` | MySQL host |
| `DB_PORT` | `3306` | MySQL port |
| `DB_USERNAME` | `user_service` | MySQL user |
| `DB_PASSWORD` | `changeme` | MySQL password |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |
| `JWT_PRIVATE_KEY` | _(auto-generated)_ | RSA private key — must be set for multi-replica k8s |
| `JWT_PUBLIC_KEY` | _(auto-generated)_ | RSA public key — same requirement |
