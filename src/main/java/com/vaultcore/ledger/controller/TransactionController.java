package com.vaultcore.ledger.controller;

import com.vaultcore.ledger.domain.Transaction;
import com.vaultcore.ledger.dto.TransferRequest;
import com.vaultcore.ledger.dto.TransferResponse;
import com.vaultcore.ledger.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/transfer")
    public TransferResponse transfer(@Valid @RequestBody TransferRequest request) {
        String idempotencyKey = request.getIdempotencyKey() != null
                ? request.getIdempotencyKey()
                : "idem-" + UUID.randomUUID();
        String referenceId = request.getReferenceId() != null
                ? request.getReferenceId()
                : "ref-" + UUID.randomUUID();

        Transaction transaction = transactionService.createTransaction(
                idempotencyKey,
                referenceId,
                request.getAmount(),
                request.getFromAccountId(),
                request.getToAccountId()
        );

        TransferResponse response = new TransferResponse();
        response.setTransactionId(transaction.getId());
        response.setReferenceId(transaction.getReferenceId());
        response.setAmount(transaction.getAmount());
        response.setStatus(transaction.getStatus());

        return response;
    }
}