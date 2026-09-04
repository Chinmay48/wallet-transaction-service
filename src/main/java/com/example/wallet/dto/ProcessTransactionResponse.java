package com.example.wallet.dto;

import com.example.wallet.entity.TransactionStatus;

import java.math.BigDecimal;
import java.util.UUID;

public class ProcessTransactionResponse {

    private UUID transactionId;
    private UUID userId;
    private BigDecimal amount;
    private TransactionStatus status;
    private BigDecimal balance;

    public ProcessTransactionResponse(
            UUID transactionId,
            UUID userId,
            BigDecimal amount,
            TransactionStatus status,
            BigDecimal balance
    ) {
        this.transactionId = transactionId;
        this.userId = userId;
        this.amount = amount;
        this.status = status;
        this.balance = balance;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public UUID getUserId() {
        return userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public BigDecimal getBalance() {
        return balance;
    }
}