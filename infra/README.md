# Local Infrastructure

`docker-compose.infra.yml` (repo root) starts the shared local dependencies for Phase 1
services: MySQL, Redis, and a single-node Kafka broker (KRaft mode).

```bash
# Start
docker compose -f docker-compose.infra.yml up -d

# Stop (keeps the mysql-data volume)
docker compose -f docker-compose.infra.yml down

# Stop and wipe all data
docker compose -f docker-compose.infra.yml down -v
```

## MySQL — one schema per service (ADR-0008)

All services share a single MySQL 8 container, but each gets its own schema and a
MySQL user with `GRANT` scoped to that schema only — no cross-service access at the
DB layer.

Schema-creation scripts live in `infra/mysql/init/`, one file per service
(`NN-<service>-db.sql`), and run automatically on first container start via MySQL's
`docker-entrypoint-initdb.d` mechanism. When scaffolding a new service, add its
`CREATE DATABASE` / `CREATE USER` / `GRANT` here, matching the
`DB_USERNAME` / `DB_PASSWORD` defaults in that service's `application.yml`.

If the container was already initialised before adding a new script, either run the
SQL manually against the running container or `down -v` to re-init from scratch.

| Service    | Schema    | User           | Init script                  |
|------------|-----------|----------------|-------------------------------|
| User/Auth  | `user_db` | `user_service` | `infra/mysql/init/01-user-db.sql` |

## Ports

| Service | Port |
|---|---|
| MySQL   | 3306 |
| Redis   | 6379 |
| Kafka   | 9092 |

## Connecting from a service run outside Docker

The defaults in each service's `application.yml` (`DB_HOST=localhost`,
`REDIS_HOST=localhost`, `KAFKA_BOOTSTRAP_SERVERS=localhost:9092`) match this compose
file out of the box — no extra environment variables needed for local development.
