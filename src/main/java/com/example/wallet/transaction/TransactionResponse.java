package com.example.wallet.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(UUID id, WalletTransactionType type, String amount, String currency,
                                  TransactionStatus status, Instant createdAt, String destinationAccountNumber) {
    public static TransactionResponse from(WalletTransaction transaction) {
        return new TransactionResponse(transaction.getId(), transaction.getType(),
                transaction.getAmount().setScale(4).toPlainString(), transaction.getCurrency(), transaction.getStatus(),
                transaction.getCreatedAt(), transaction.getType() == WalletTransactionType.TRANSFER
                        && transaction.getDestination() != null ? transaction.getDestination().getAccountNumber() : null);
    }
}
