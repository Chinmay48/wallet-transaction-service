# Wallet Transaction Service

A Spring Boot REST API for processing wallet debit transactions with
concurrency control and idempotency.

## Tech Stack

- Java 21
- Spring Boot
- Spring Data JPA
- H2 Database
- Maven
- JUnit 5

## API

### Process Transaction

**POST** `/api/v1/transactions/process`

Example request:

```json
{
  "transactionId": "11111111-1111-1111-1111-111111111111",
  "userId": "22222222-2222-2222-2222-222222222222",
  "amount": 100.00,
  "type": "DEBIT"
}