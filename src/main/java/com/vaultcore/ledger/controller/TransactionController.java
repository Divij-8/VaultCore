package com.vaultcore.ledger.controller;

import com.vaultcore.ledger.domain.Account;
import com.vaultcore.ledger.domain.Transaction;
import com.vaultcore.ledger.domain.User;
import com.vaultcore.ledger.dto.TransferByAccountNumberRequest;
import com.vaultcore.ledger.dto.TransferRequest;
import com.vaultcore.ledger.dto.TransferResponse;
import com.vaultcore.ledger.repository.AccountRepository;
import com.vaultcore.ledger.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final AccountRepository accountRepository;

    @PostMapping("/transfer")
    public TransferResponse transfer(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody TransferRequest request
    ) {
        String idempotencyKey = request.getIdempotencyKey() != null
                ? request.getIdempotencyKey()
                : "idem-" + UUID.randomUUID();
        String referenceId = request.getReferenceId() != null
                ? request.getReferenceId()
                : "ref-" + UUID.randomUUID();

        Transaction transaction = transactionService.createTransaction(
                user.getId(),
                idempotencyKey,
                referenceId,
                request.getAmount(),
                request.getFromAccountId(),
                request.getToAccountId()
        );

        return toResponse(transaction);
    }

    @PostMapping("/transfer-by-account-number")
    public TransferResponse transferByAccountNumber(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody TransferByAccountNumberRequest request
    ) {
        Account fromAccount = accountRepository.findByAccountNumber(request.getFromAccountNumber())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Account not found: " + request.getFromAccountNumber()));
        Account toAccount = accountRepository.findByAccountNumber(request.getToAccountNumber())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Account not found: " + request.getToAccountNumber()));

        String idempotencyKey = request.getIdempotencyKey() != null
                ? request.getIdempotencyKey()
                : "idem-" + UUID.randomUUID();
        String referenceId = request.getReferenceId() != null
                ? request.getReferenceId()
                : "ref-" + UUID.randomUUID();

        Transaction transaction = transactionService.createTransaction(
                user.getId(),
                idempotencyKey,
                referenceId,
                request.getAmount(),
                fromAccount.getId(),
                toAccount.getId()
        );

        return toResponse(transaction);
    }

    private TransferResponse toResponse(Transaction transaction) {
        TransferResponse response = new TransferResponse();
        response.setTransactionId(transaction.getId());
        response.setReferenceId(transaction.getReferenceId());
        response.setAmount(transaction.getAmount());
        response.setStatus(transaction.getStatus());
        return response;
    }
}
