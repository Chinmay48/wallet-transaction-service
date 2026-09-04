package com.example.wallet.integration;
import com.example.wallet.entity.Transaction;
import com.example.wallet.repository.TransactionRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import com.example.wallet.dto.ProcessTransactionRequest;
import com.example.wallet.entity.TransactionType;
import com.example.wallet.entity.Wallet;
import com.example.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.example.wallet.service.TransactionService;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)

class TransactionIntegrationTest {

    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private TransactionService transactionService;
    @Autowired
    private TransactionRepository transactionRepository;
    @LocalServerPort
    private int port;
    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Test
    @DisplayName("Processes a single valid debit transaction successfully.")
    void processesSingleValidDebitTransactionSuccessfully() {

        // Arrange
        UUID userId = UUID.randomUUID();

        Wallet wallet = new Wallet(
                userId,
                new BigDecimal("500.00")
        );

        walletRepository.save(wallet);

        ProcessTransactionRequest request =
                new ProcessTransactionRequest();

        request.setTransactionId(UUID.randomUUID());
        request.setUserId(userId);
        request.setAmount(new BigDecimal("100.00"));
        request.setType(TransactionType.DEBIT);

        // Act
        var response = transactionService.process(request);

// Assert
        assertEquals(
                new BigDecimal("400.00"),
                response.getBalance()
        );

        assertEquals(
                "SUCCESS",
                response.getStatus().name()
        );
    }
    @Test
    @DisplayName("Processes the same transaction ID only once under concurrent requests.")
    void processesSameTransactionOnlyOnceConcurrently() throws Exception {

        // Arrange
        UUID userId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();

        Wallet wallet = new Wallet(
                userId,
                new BigDecimal("500.00")
        );

        walletRepository.save(wallet);

        ProcessTransactionRequest request =
                new ProcessTransactionRequest();

        request.setTransactionId(transactionId);
        request.setUserId(userId);
        request.setAmount(new BigDecimal("100.00"));
        request.setType(TransactionType.DEBIT);

        int numberOfRequests = 3;

        ExecutorService executor =
                Executors.newFixedThreadPool(numberOfRequests);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        List<Future<?>> futures = new ArrayList<>();

        // Act
        for (int i = 0; i < numberOfRequests; i++) {

            futures.add(
                    executor.submit(() -> {

                        startLatch.await();

                        return transactionService.process(request);
                    })
            );
        }

        // Release all three requests together
        startLatch.countDown();

        // Wait for all requests to finish
        for (Future<?> future : futures) {
            future.get();
        }

        executor.shutdown();

        // Assert
        Wallet finalWallet =
                walletRepository.findByUserId(userId).orElseThrow();

        assertEquals(
                new BigDecimal("400.00"),
                finalWallet.getBalance()
        );

        assertEquals(
                1,
                transactionRepository.count()
        );
    }
    @Test
    @DisplayName("Sends 10 concurrent debit requests of ₹100 for a wallet with a ₹500 balance. Ensures the final balance is exactly ₹0 and 5 requests fail with insufficient funds.")
    void processesTenConcurrentDebitsWithInsufficientFundsCorrectly()
            throws Exception {

        // Arrange
        UUID userId = UUID.randomUUID();

        Wallet wallet = new Wallet(
                userId,
                new BigDecimal("500.00")
        );

        walletRepository.save(wallet);

        int numberOfRequests = 10;

        ExecutorService executor =
                Executors.newFixedThreadPool(numberOfRequests);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        List<Future<?>> futures = new ArrayList<>();

        // Create 10 independent transactions
        for (int i = 0; i < numberOfRequests; i++) {

            ProcessTransactionRequest request =
                    new ProcessTransactionRequest();

            request.setTransactionId(UUID.randomUUID());
            request.setUserId(userId);
            request.setAmount(new BigDecimal("100.00"));
            request.setType(TransactionType.DEBIT);

            futures.add(
                    executor.submit(() -> {

                        startLatch.await();

                        return transactionService.process(request);
                    })
            );
        }

        // Act
        // Release all 10 requests at approximately the same time.
        startLatch.countDown();

        int successfulRequests = 0;
        int insufficientFundsRequests = 0;

        // Wait for all requests to finish.
        for (Future<?> future : futures) {

            try {
                future.get();
                successfulRequests++;

            } catch (java.util.concurrent.ExecutionException exception) {

                if (exception.getCause()
                        instanceof com.example.wallet.exception.InsufficientFundsException) {

                    insufficientFundsRequests++;

                } else {
                    throw exception;
                }
            }
        }

        executor.shutdown();

        // Assert
        Wallet finalWallet =
                walletRepository.findByUserId(userId).orElseThrow();

        assertEquals(
                5,
                successfulRequests
        );

        assertEquals(
                5,
                insufficientFundsRequests
        );

        assertEquals(
                new BigDecimal("0.00"),
                finalWallet.getBalance()
        );
    }
    @Test
    @DisplayName("Processes a valid transaction through the REST API.")
    void processesTransactionThroughRestApi() throws Exception {

        // Arrange
        UUID userId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();

        Wallet wallet = new Wallet(
                userId,
                new BigDecimal("500.00")
        );

        walletRepository.save(wallet);

        String requestBody = """
            {
                "transactionId": "%s",
                "userId": "%s",
                "amount": 100.00,
                "type": "DEBIT"
            }
            """.formatted(transactionId, userId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://localhost:" + port + "/api/v1/transactions/process"
                ))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        // Act
        HttpResponse<String> response =
                HttpClient.newHttpClient()
                        .send(
                                request,
                                HttpResponse.BodyHandlers.ofString()
                        );

        // Assert
        assertEquals(
                200,
                response.statusCode()
        );

        assertEquals(
                true,
                response.body().contains("\"status\":\"SUCCESS\"")
        );

        assertEquals(
                true,
                response.body().contains("\"balance\":400.00")
        );
    }
}