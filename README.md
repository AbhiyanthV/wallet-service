# wallet-service

A RESTful wallet microservice built with Spring Boot 3 and PostgreSQL. Supports deposit and withdrawal operations with pessimistic locking to guarantee correctness under concurrent load.

## Tech Stack

- **Java 17** / **Spring Boot 3.2.3**
- **PostgreSQL 16** — primary data store
- **Liquibase** — database schema migrations
- **HikariCP** — connection pooling
- **Testcontainers** — integration tests against a real PostgreSQL instance
- **Docker Compose** — local environment setup

## API

Base path: `/api/v1`

### Perform a wallet operation

```
POST /api/v1/wallet
Content-Type: application/json
```

**Request body**

| Field           | Type            | Required | Description                      |
|-----------------|-----------------|----------|----------------------------------|
| `walletId`      | UUID            | yes      | Target wallet identifier         |
| `operationType` | `DEPOSIT` \| `WITHDRAW` | yes | Operation to perform    |
| `amount`        | decimal         | yes      | Positive amount to credit/debit  |

**Example**

```json
{
  "walletId": "550e8400-e29b-41d4-a716-446655440000",
  "operationType": "DEPOSIT",
  "amount": 500.00
}
```

**Responses**

| Status | Meaning                                |
|--------|----------------------------------------|
| 200    | Operation applied successfully         |
| 400    | Invalid request body or field values   |
| 404    | Wallet not found                       |
| 422    | Insufficient funds for withdrawal      |

---

### Get wallet balance

```
GET /api/v1/wallets/{walletId}
```

**Response body**

```json
{
  "walletId": "550e8400-e29b-41d4-a716-446655440000",
  "balance": 1500.00
}
```

**Responses**

| Status | Meaning             |
|--------|---------------------|
| 200    | Balance returned     |
| 400    | Invalid UUID format  |
| 404    | Wallet not found     |

---

### Error response shape

All error responses use a consistent envelope:

```json
{
  "message": "Insufficient funds"
}
```

## Running locally

### Prerequisites

- Docker & Docker Compose

### Steps

```bash
# 1. Clone the repository
git clone <repo-url>
cd wallet-service

# 2. Copy and (optionally) edit environment variables
cp .env.example .env

# 3. Start the application and database
docker compose up --build
```

The service will be available at `http://localhost:8080`.

### Environment variables

All variables have defaults so the service works out of the box with the values in `.env.example`.

| Variable        | Default    | Description                        |
|-----------------|------------|------------------------------------|
| `POSTGRES_DB`   | `walletdb` | Database name                      |
| `POSTGRES_USER` | `wallet`   | Database user                      |
| `POSTGRES_PASSWORD` | `wallet` | Database password                |
| `POSTGRES_PORT` | `5432`     | Host port mapped to PostgreSQL     |
| `SERVER_PORT`   | `8080`     | Host port mapped to the app        |
| `DB_POOL_SIZE`  | `20`       | HikariCP maximum connection count  |

## Running tests

Tests use Testcontainers and spin up a real PostgreSQL container automatically. Docker must be running.

```bash
./mvnw test
```

The test suite covers:
- Successful deposit and withdrawal
- Insufficient funds rejection (422)
- Wallet not found (404)
- Invalid request body and missing fields (400)
- Negative and zero amount rejection (400)
- Invalid UUID path parameter (400)
- 50 concurrent deposits completing correctly with final balance verified

## Concurrency model

Each write operation acquires a pessimistic lock (`SELECT ... FOR UPDATE`) on the wallet row before modifying the balance. This prevents lost updates under concurrent requests. The lock timeout is set to 10 seconds (configurable via `jakarta.persistence.lock.timeout`), so requests queue rather than failing immediately when contention is high.

## Project structure

```
src/
├── main/java/com/wallet/
│   ├── WalletApplication.java
│   ├── controller/        # REST layer
│   ├── service/           # Business logic & transaction management
│   ├── repository/        # Spring Data JPA + locking query
│   ├── model/             # JPA entities
│   ├── dto/               # Request/response records and enums
│   └── exception/         # Custom exceptions & global handler
└── main/resources/
    ├── application.yml
    └── db/changelog/      # Liquibase migration scripts
```
