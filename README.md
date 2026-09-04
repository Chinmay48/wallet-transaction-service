# Wallet Transaction Service

A Spring Boot REST API for processing wallet debit transactions safely under concurrent requests.

The service demonstrates two key backend concepts:

- **Concurrency control** using database-level pessimistic locking
- **Idempotency** using unique transaction IDs

## Tech Stack

- Java 21
- Spring Boot 4
- Spring Data JPA
- H2 Database
- Maven
- JUnit 5

## How It Works

A transaction request flows through:

```text
Client
  |
  | POST /api/v1/transactions/process
  v
TransactionController
  |
  v
TransactionService
  |
  +--> WalletRepository
  |
  +--> TransactionRepository
  |
  v
H2 Database
```

The main processing flow is:

```text
1. Lock the wallet row
2. Check whether the transaction ID already exists
3. Return the existing transaction if it was already processed
4. Check whether sufficient funds are available
5. Debit the wallet
6. Save the transaction
7. Return the response
```

## API

### Process Transaction

**POST** `/api/v1/transactions/process`

Request:

```json
{
  "transactionId": "11111111-1111-1111-1111-111111111111",
  "userId": "22222222-2222-2222-2222-222222222222",
  "amount": 100.00,
  "type": "DEBIT"
}
```

Successful response:

```json
{
  "transactionId": "11111111-1111-1111-1111-111111111111",
  "userId": "22222222-2222-2222-2222-222222222222",
  "amount": 100.00,
  "status": "SUCCESS",
  "balance": 400.00
}
```

## Concurrency Control

The wallet is locked using:

```text
findByUserIdForUpdate()
```

This uses a pessimistic write lock so concurrent transactions for the same wallet are processed one at a time.

For example:

```text
Initial balance = ₹500

Request A → Debit ₹400
Request B → Debit ₹200
```

One request obtains the lock first and updates the balance:

```text
₹500 → ₹100
```

When the second request obtains the lock, it sees the updated balance of ₹100, so the ₹200 debit fails due to insufficient funds.

This prevents race conditions and incorrect wallet balances.

## Idempotency

Each transaction has a unique `transactionId`.

If the same transaction request is received multiple times, the service returns the already-created transaction instead of debiting the wallet again.

Example:

```text
Request 1 ─┐
Request 2 ─┼─ Same transactionId
Request 3 ─┘
```

Result:

```text
Initial balance = ₹500
Final balance   = ₹400
Transactions    = 1
```

The database also enforces uniqueness for the transaction ID.

## Validation

The request validates:

- `transactionId` is required
- `userId` is required
- `amount` is required
- `amount` must be greater than zero
- `type` is required

If the wallet does not have enough funds, `InsufficientFundsException` is raised and the balance is not debited.

## Project Structure

```text
src/
├── main/
│   └── java/com/example/wallet/
│       ├── controller/
│       │   └── TransactionController.java
│       ├── dto/
│       │   ├── ProcessTransactionRequest.java
│       │   └── ProcessTransactionResponse.java
│       ├── entity/
│       │   ├── Wallet.java
│       │   ├── Transaction.java
│       │   ├── TransactionType.java
│       │   └── TransactionStatus.java
│       ├── exception/
│       │   ├── InsufficientFundsException.java
│       │   └── WalletNotFoundException.java
│       ├── repository/
│       │   ├── WalletRepository.java
│       │   └── TransactionRepository.java
│       └── service/
│           └── TransactionService.java
│
└── test/
    └── java/com/example/wallet/
        ├── integration/
        │   └── TransactionIntegrationTest.java
        └── WalletTransactionServiceApplicationTests.java
```

## Main Components

### `TransactionController`

Exposes the REST endpoint and delegates transaction processing to the service.

### `TransactionService`

Contains the core business logic:

- wallet locking
- idempotency check
- balance validation
- wallet balance update
- transaction creation
- response creation

Main method:

```java
process(ProcessTransactionRequest request)
```

### `WalletRepository`

Handles wallet database operations, including the pessimistic locking operation:

```text
findByUserIdForUpdate()
```

### `TransactionRepository`

Handles transaction persistence and lookup by transaction ID.

## Testing

The integration tests verify the important functional and concurrency requirements.

### 1. Successful Debit

```text
Initial balance = ₹500
Debit           = ₹100
Expected        = ₹400
Status          = SUCCESS
```

### 2. Concurrent Duplicate Transaction

Three concurrent requests use the same transaction ID:

```text
Requests        = 3
Initial balance = ₹500
Debit           = ₹100
Final balance   = ₹400
Transactions    = 1
```

This verifies idempotency.

### 3. Concurrent Insufficient Funds

Ten concurrent requests attempt to debit ₹100 from a ₹500 wallet:

```text
Requests                 = 10
Amount per request       = ₹100
Initial balance          = ₹500
Successful requests      = 5
Insufficient fund errors = 5
Final balance             = ₹0
```

This verifies concurrency control and prevents the wallet from being overdrawn.

### 4. REST API Processing

The application is also tested through the REST layer to verify the complete flow:

```text
HTTP Request
     ↓
TransactionController
     ↓
TransactionService
     ↓
Database
     ↓
HTTP Response
```

### Test Result

Current test suite:

```text
Tests run: 5
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

## Running the Application

### Windows

```bash
.\mvnw.cmd spring-boot:run
```

### Linux / macOS

```bash
./mvnw spring-boot:run
```

Application:

```text
http://localhost:8080
```

## Running Tests

### Windows

```bash
.\mvnw.cmd test
```

### Linux / macOS

```bash
./mvnw test
```

## Database

The project uses an H2 in-memory database, so no external database installation is required for running the assignment.

## Design Decisions

Additional reasoning about concurrency, idempotency, and an initial implementation issue discovered during concurrent testing is documented in:

`DECISIONS.md`
