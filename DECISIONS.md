# Design Decisions

## 1. Concurrency and Race Condition Handling

The wallet balance is protected using a database-level pessimistic write lock.

When a transaction request is received, the service first locks the wallet row using:

`findByUserIdForUpdate()`

This ensures that concurrent transactions for the same wallet are processed one at a time.

The processing flow is:

1. Lock the wallet row.
2. Check whether the transaction ID already exists.
3. If it exists, return the existing transaction without debiting the wallet again.
4. Check whether sufficient funds are available.
5. Debit the wallet balance.
6. Save the transaction.

This prevents two concurrent requests from reading the same wallet balance and both successfully debiting it.

The approach was verified using a concurrency integration test with 10 simultaneous ₹100 debit requests against a ₹500 wallet. Exactly 5 requests succeed, 5 fail due to insufficient funds, and the final wallet balance is ₹0.

## 2. Idempotency

Each transaction has a unique transaction ID enforced by a database unique constraint.

Before processing a transaction, the service checks whether the transaction ID already exists.

If it exists, the existing transaction is returned and the wallet is not debited again.

This ensures that retrying the same transaction does not result in duplicate processing.

This behavior was verified using 3 concurrent requests with the same transaction ID. Only one transaction is created and the wallet is debited only once.

## 3. AI-Assisted Development – Incorrect Initial Approach

An initial implementation suggestion checked whether the transaction already existed before acquiring the wallet lock.

The flow was initially:

1. Check transaction ID.
2. Lock wallet.
3. Process transaction.

This was not sufficient for concurrent requests because multiple threads could simultaneously check the transaction ID before any of them had inserted the transaction.

This resulted in a database unique-constraint race and a Hibernate failure during the concurrent integration test.

The implementation was corrected to acquire the wallet lock before checking transaction existence:

1. Lock wallet.
2. Check transaction ID.
3. Process only if the transaction is new.

The concurrency tests were then executed again and all required tests passed successfully.