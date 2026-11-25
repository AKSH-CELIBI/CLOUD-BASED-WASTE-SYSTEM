# Digital Wallet Backend

A Java 21 and Spring Boot REST API for digital wallet accounts, deposits, transfers, and transaction history. It uses PostgreSQL for the wallet balances and append-only ledger, JWT for authentication, and Docker Compose for local development.

## Features

- Register and log in with BCrypt-hashed passwords and signed JWTs.
- Provision a wallet with a unique 12-digit account number during registration.
- Deposit and transfer decimal amounts with four-place precision.
- Keep paired debit and credit ledger entries in the same database transaction as each balance change.
- Lock wallets in UUID order during money movement to prevent lost updates and transfer deadlocks.
- Scope idempotency keys to each user and reject reuse with a different request payload.
- Freeze and unfreeze accounts with admin audit records.
- Validate the PostgreSQL schema with Flyway migrations and Hibernate schema validation.
- Expose health checks, Swagger UI, and an OpenAPI contract for the frontend.

Deposits are simulated using a prefunded settlement wallet. No bank, card, or payment provider is connected, so this project does not move real money.

## Run locally

Requirements: Java 21+, Docker Desktop with Compose, and a JWT secret of at least 32 UTF-8 bytes.

```sh
cp .env.example .env
# Set WALLET_JWT_SECRET in .env to a random value of at least 32 bytes.
docker compose up --build
```

- Health: `http://localhost:8080/api/v1/actuator/health`
- Swagger UI: `http://localhost:8080/api/v1/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/api/v1/v3/api-docs`
- OpenAPI source: [`openapi.yaml`](openapi.yaml)

Optional admin bootstrap: set both `WALLET_ADMIN_EMAIL` and `WALLET_ADMIN_PASSWORD`. The configured account is created or promoted to `ADMIN` at startup. Use a unique strong password and keep it out of source control.

## API routes

All routes are under `/api/v1`.

| Method | Path | Access |
| --- | --- | --- |
| `POST` | `/auth/register` | Public |
| `POST` | `/auth/login` | Public |
| `GET` | `/accounts/me` | User |
| `POST` | `/transactions/deposit` | User, `Idempotency-Key` required |
| `POST` | `/transactions/transfer` | User, `Idempotency-Key` required |
| `GET` | `/transactions/history` | User |
| `PATCH` | `/admin/accounts/{id}/freeze` | Admin |
| `GET` | `/admin/audit-logs` | Admin |

Transfer source is always the authenticated user's wallet. The request only supplies the destination account number and amount. Money is sent and returned as decimal strings; errors use `{status, code, message, fieldErrors}`.

## Configuration

See [`.env.example`](.env.example). Required in Compose: `WALLET_JWT_SECRET`. The default local PostgreSQL password is for development only; set `POSTGRES_PASSWORD` before using a shared environment. CORS defaults to React/Vite localhost origins.

## Tests

```sh
./mvnw -B verify
```

The PostgreSQL integration suite uses Testcontainers by default. To use an already-running PostgreSQL test database, set `WALLET_TEST_DATABASE_URL`, `WALLET_TEST_DATABASE_USERNAME`, and `WALLET_TEST_DATABASE_PASSWORD` before running Maven. Tests use PostgreSQL rather than H2 and include concurrent transfer and idempotency checks.

## Project boundary

This repository contains the backend platform, identity, wallet, transaction, ledger, and admin scopes. The frontend can use the REST routes and OpenAPI contract above. Real payment rails, production deployment, monitoring, and fraud controls require separate configuration and integrations.
