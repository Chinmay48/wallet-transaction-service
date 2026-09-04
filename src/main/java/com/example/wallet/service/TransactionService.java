package com.example.wallet.service;

import com.example.wallet.dto.ProcessTransactionRequest;
import com.example.wallet.dto.ProcessTransactionResponse;
import com.example.wallet.entity.Transaction;
import com.example.wallet.entity.TransactionStatus;
import com.example.wallet.entity.Wallet;
import com.example.wallet.exception.InsufficientFundsException;
import com.example.wallet.exception.WalletNotFoundException;
import com.example.wallet.repository.TransactionRepository;
import com.example.wallet.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    public TransactionService(
            WalletRepository walletRepository,
            TransactionRepository transactionRepository
    ) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public ProcessTransactionResponse process(
            ProcessTransactionRequest request
    ) {

        // 1. Lock the wallet first.
        // This ensures concurrent transactions for the same wallet
        // are processed one at a time.
        Wallet wallet = walletRepository
                .findByUserIdForUpdate(request.getUserId())
                .orElseThrow(
                        () -> new WalletNotFoundException(request.getUserId())
                );

        // 2. Now check whether this transaction was already processed.
        var existingTransaction =
                transactionRepository.findByTransactionId(
                        request.getTransactionId()
                );

        if (existingTransaction.isPresent()) {
            return buildResponse(
                    existingTransaction.get(),
                    wallet.getBalance()
            );
        }

        // 3. Check sufficient balance.
        if (wallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException(
                    wallet.getBalance(),
                    request.getAmount()
            );
        }

        // 4. Debit the wallet.
        wallet.setBalance(
                wallet.getBalance().subtract(request.getAmount())
        );

        walletRepository.save(wallet);

        // 5. Create the transaction record.
        Transaction transaction = new Transaction(
                request.getTransactionId(),
                request.getUserId(),
                request.getAmount(),
                request.getType(),
                TransactionStatus.SUCCESS
        );

        // 6. Save the transaction.
        transactionRepository.save(transaction);

        // 7. Return the successful response.
        return buildResponse(
                transaction,
                wallet.getBalance()
        );
    }

    private ProcessTransactionResponse buildResponse(
            Transaction transaction,
            java.math.BigDecimal balance
    ) {
        return new ProcessTransactionResponse(
                transaction.getTransactionId(),
                transaction.getUserId(),
                transaction.getAmount(),
                transaction.getStatus(),
                balance
        );
    }
}