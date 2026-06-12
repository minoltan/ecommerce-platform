-- user-service (User / Auth bounded context) — ADR-0008 database-per-service.
-- Creates the user_db schema and a credential scoped to it only, matching the
-- DB_USERNAME/DB_PASSWORD defaults in phase1/user-service/src/main/resources/application.yml.
CREATE DATABASE IF NOT EXISTS user_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'user_service'@'%' IDENTIFIED BY 'changeme';
GRANT ALL PRIVILEGES ON user_db.* TO 'user_service'@'%';

FLUSH PRIVILEGES;
