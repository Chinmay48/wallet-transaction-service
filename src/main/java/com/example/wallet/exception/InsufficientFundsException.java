package com.example.wallet.exception;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(BigDecimal balance, BigDecimal amount) {
        super(
                "Insufficient funds. Available balance: "
                        + balance
                        + ", requested amount: "
                        + amount
        );
    }
}