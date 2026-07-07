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

### Installing prerequisites on Ubuntu

**Docker** — install from Docker's official apt repo, not the `snap` package. The
snap build runs Docker under stricter AppArmor confinement, which is known to
conflict with `kind`'s Docker-in-Docker control-plane container (mount/cgroup
errors on `kind create cluster`). If `docker --version` already shows a snap
install and `kind` misbehaves later, this is the first thing to rule out.

```bash
# Remove snap Docker if present
sudo snap remove docker

# Add Docker's official apt repo
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Install Docker Engine + Compose plugin
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Run docker without sudo
sudo usermod -aG docker $USER
newgrp docker

docker --version
docker compose version
```

**kubectl**

```bash
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
chmod +x kubectl
sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl
rm kubectl

kubectl version --client
```

**kind**

```bash
curl -Lo kind "https://kind.sigs.k8s.io/dl/v0.24.0/kind-linux-amd64"
chmod +x kind
sudo install -o root -g root -m 0755 kind /usr/local/bin/kind
rm kind

kind version
```

`openssl` ships by default on Ubuntu; verify with `openssl version`.

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
| `ecommerce-kafka` | 9092 / 29092 | KRaft mode, single broker, auto-creates topics. `9092` (`PLAINTEXT` listener, advertised as `localhost:9092`) is for host-side tools (CLI, IDE plugins). `29092` (`DOCKER` listener, advertised as `host.docker.internal:29092`) is for other containers — see [Troubleshooting](#troubleshooting) if you connect from a container on `9092` and see repeating disconnects. |

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
  --add-host=host.docker.internal:host-gateway \
  -p 8081:8081 \
  -e DB_HOST=host.docker.internal \
  -e REDIS_HOST=host.docker.internal \
  -e KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:29092 \
  user-service:latest
```

> `host.docker.internal` resolves to the host machine from inside a container, reaching
> the ports exposed by the infra compose services. On **Docker Desktop** (Mac/Windows)
> this hostname resolves automatically. On **native Docker Engine on Linux** it does
> not — you must add `--add-host=host.docker.internal:host-gateway` explicitly, or the
> container fails with `UnknownHostException: host.docker.internal`.
>
> Kafka uses port `29092`, not `9092`, for container-to-container connections — see the
> infra table above and [Troubleshooting](#troubleshooting).

```bash
# Follow logs
docker logs -f user-service

# Stop
docker stop user-service
```

---

## Option 3 — Kubernetes (kind / minikube / k3d)

The `k8s/overlays/local` overlay sets 1 replica, removes HPA/PDB, and points infra hosts at `host.docker.internal`.

> **Known gap:** `overlays/local/kustomization.yaml` currently generates
> `KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9092` — the same broken pattern
> documented under [Troubleshooting](#troubleshooting) for Option 2 (wrong port, and
> `host.docker.internal` isn't auto-resolved inside `kind` node containers on native
> Linux Docker either, since `kind` doesn't pass `--add-host` to its nodes). This
> overlay has not yet been exercised end-to-end on a Linux `kind` cluster. Until it's
> fixed, either:
> - patch `KAFKA_BOOTSTRAP_SERVERS` to `host.docker.internal:29092` and add a
>   `hostAliases` entry in the Deployment pointing `host.docker.internal` at the
>   `kind` Docker network's gateway IP (`docker network inspect kind`), or
> - skip host infra entirely for local k8s testing and apply `kubectl apply -k
>   phase1/k8s/infra` instead, which deploys MySQL/Redis/Kafka *inside* the cluster —
>   `base/configmap.yaml`'s in-cluster DNS values (`user-mysql.ecommerce-infra.svc.cluster.local`,
>   etc.) then work with no host-networking tricks at all.

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
| `GET /v1/auth/.well-known/jwks.json` | Public JWKS endpoint (JWT verification) |
| `POST /v1/auth/register` | Register a new user |
| `POST /v1/auth/login` | Login — returns access + refresh tokens |
| `POST /v1/auth/refresh` | Rotate refresh token |
| `POST /v1/auth/logout` | Blacklist access token |
| `POST /v1/auth/verify-email` | Email verification |
| `GET /v1/admin/users` | List users (ADMIN role) |
| `POST /v1/admin/users/{userId}/deactivate` | Deactivate user (ADMIN role) |

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

---

## Troubleshooting

### `docker: address already in use` on port 8081 (or 3306/6379/9092)

Find and free the port:

```bash
sudo lsof -i :8081
# or
sudo ss -ltnp | grep 8081

# If it's a leftover container:
docker ps -a --filter "publish=8081"
docker rm -f <container>

# If it's a local process (e.g. mvnw spring-boot:run still running):
kill <PID>          # escalate to kill -9 only if it doesn't die
```

### `no main manifest attribute, in /app/app.jar`

The jar built by `mvn package` isn't an executable Spring Boot jar — `java -jar`
can't find a `Main-Class`. Cause: `spring-boot-maven-plugin` was declared only under
`<pluginManagement>` in the parent `phase1/pom.xml`, which sets version/config for
child modules to *inherit if referenced*, but never actually binds the `repackage`
goal to the build. Fixed by adding the plugin to the parent's real `<build><plugins>`
block (not just `pluginManagement`) with an explicit `repackage` execution, so every
service module inherits a working build. If you scaffold a new service and hit this
again, check `phase1/pom.xml`'s `<build><plugins>` section is still in place.

### `UnknownHostException: host.docker.internal`

Only happens on **native Docker Engine on Linux** (not Docker Desktop). Add
`--add-host=host.docker.internal:host-gateway` to your `docker run` command — see
[Option 2](#option-2--docker) above.

### Kafka producer stuck in a `Bootstrap broker ... disconnected` loop

The initial connection to Kafka succeeds, but every request after that fails. Cause:
Kafka's `advertised.listeners` told the client to reconnect to `localhost:9092` —
which, from inside a container, means the container itself, not the host. Fixed in
`docker-compose.infra.yml` by adding a second `DOCKER` listener
(`host.docker.internal:29092`) specifically for container-to-container traffic, while
keeping `PLAINTEXT` (`localhost:9092`) for host-side tools. Use port `29092`, not
`9092`, in `KAFKA_BOOTSTRAP_SERVERS` from any container.

### Kafka container crash-loops: `Cluster ID string ... does not appear to be a valid UUID`

KRaft mode requires `CLUSTER_ID` to be a 16-byte value base64url-encoded to exactly 22
characters — not an arbitrary string. Generate a valid one:

```bash
python3 -c "
import uuid, base64
print(base64.urlsafe_b64encode(uuid.uuid4().bytes).decode().rstrip('='))
"
```

and set it as `CLUSTER_ID` in `docker-compose.infra.yml`. Since the `kafka` service
has no persistent volume, there's no stale storage to wipe — just
`docker compose -f docker-compose.infra.yml up -d --force-recreate kafka`.

### After editing `docker-compose.infra.yml`, the container still shows old behaviour

`docker compose up -d` alone won't pick up env var changes for a container that's
already running — it has to be recreated:

```bash
docker compose -f docker-compose.infra.yml up -d --force-recreate <service>
```

Also double check you're running `docker compose` from the directory containing
`docker-compose.infra.yml` (repo root) — running it from `phase1/` (or any other
subdirectory) fails with `open docker-compose.infra.yml: no such file or directory`
and silently does nothing, leaving the old container running.

### `kind create cluster` hangs or the control-plane node never becomes Ready

Likely cause: Docker installed via `snap` runs under AppArmor confinement that can
block the mounts/cgroups `kind`'s Docker-in-Docker control-plane container needs.
Switch to Docker's official apt package — see
[Installing prerequisites on Ubuntu](#installing-prerequisites-on-ubuntu).
