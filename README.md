# Banking Transaction Microservice

A Spring Boot REST service that accepts banking transactions (deposits, withdrawals, transfers),
validates them against business rules, and updates balances in PostgreSQL using a double-entry ledger.
Transfers are atomic and stay correct under concurrent requests.

**Stack:** Java 21 · Spring Boot · Spring Data JPA · PostgreSQL · Flyway · JUnit 5 · Mockito · Docker

## Run it

Requires Docker Desktop.

```bash
docker compose up --build
```

The API is then available at `http://localhost:8080`. Stop it with `Ctrl+C`, or `docker compose down`
(add `-v` to also delete the database volume).

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/accounts` | Create an account |
| `GET` | `/api/accounts/{id}` | Get an account and its balance |
| `POST` | `/api/transactions` | Deposit, withdraw, or transfer |
| `GET` | `/api/accounts/{id}/transactions` | Transaction history, newest first |

### Examples

```bash
# Create two accounts
curl -X POST localhost:8080/api/accounts -H "Content-Type: application/json" \
  -d '{"ownerName":"Sid","currency":"INR"}'
curl -X POST localhost:8080/api/accounts -H "Content-Type: application/json" \
  -d '{"ownerName":"Ravi","currency":"INR"}'

# Deposit 1000 into account 1
curl -X POST localhost:8080/api/transactions -H "Content-Type: application/json" \
  -d '{"type":"DEPOSIT","amount":1000.00,"toAccountId":1}'

# Transfer 250.50 from account 1 to account 2
curl -X POST localhost:8080/api/transactions -H "Content-Type: application/json" \
  -d '{"type":"TRANSFER","amount":250.50,"fromAccountId":1,"toAccountId":2}'

# Check balance and history
curl localhost:8080/api/accounts/1
curl localhost:8080/api/accounts/1/transactions
```

`DEPOSIT` needs `toAccountId`, `WITHDRAWAL` needs `fromAccountId`, and `TRANSFER` needs both.

### Response codes

| Code | Meaning |
|---|---|
| `201` | Account created, or transaction `COMPLETED` |
| `400` | Malformed request (e.g. negative amount, more than 2 decimals, missing account id) |
| `404` | Account not found |
| `422` | Transaction `REJECTED` by a business rule; the reason is in `rejectionReason` |

## Business rules

- Amount is positive, with at most 2 decimal places
- Amount is at most 1,000,000.00
- Source and destination accounts differ
- Both accounts are `ACTIVE`
- Currencies match
- Source has sufficient funds

Rejected transactions are still recorded with status `REJECTED` and a reason, for auditing.

## Design

- **Layers:** `controller` → `service` → `repository`, with `dto`, `entity`, and `exception` packages.
  Controllers only handle HTTP; business logic lives in services.
- **Money:** `BigDecimal` in Java and `NUMERIC(19,2)` in Postgres. Never `double`.
- **Double-entry ledger:** every completed transfer writes one `DEBIT` and one `CREDIT` row in
  `ledger_entries`, so money never appears or disappears.
- **Schema:** managed by Flyway (`src/main/resources/db/migration`), with database-level constraints
  (`balance >= 0`, `amount > 0`, unique account numbers, foreign keys) as a last line of defence.
- **Atomicity:** each transaction runs inside one `@Transactional` method, so it commits fully or not at all.
- **Concurrency:** accounts are locked with `SELECT ... FOR UPDATE` (`PESSIMISTIC_WRITE`) before the
  balance check. Transfers always lock the lower account id first, which prevents deadlocks.
- **Errors:** a `@RestControllerAdvice` turns exceptions into consistent JSON error responses.

## Tests

```bash
docker start bankdb 2>/dev/null || docker run --name bankdb \
  -e POSTGRES_DB=bankdb -e POSTGRES_USER=bank -e POSTGRES_PASSWORD=bank \
  -p 5432:5432 -d postgres:16
./mvnw test
```

- **Unit tests** (no database): every validation rule, the service layer with mocked repositories
  (Mockito), and the DTO validation annotations.
- **Concurrency tests** (real Postgres): 100 parallel transfers in both directions keep the total money
  constant, and 50 parallel withdrawals never overdraw an account.
