package com.example.wallet.transaction;

import com.example.wallet.identity.UserRepository;
import com.example.wallet.wallet.AccountKind;
import com.example.wallet.wallet.WalletAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class FailedAttemptRecorder {
    private final UserRepository users;
    private final WalletAccountRepository accounts;
    private final WalletTransactionRepository transactions;

    public FailedAttemptRecorder(UserRepository users, WalletAccountRepository accounts, WalletTransactionRepository transactions) {
        this.users = users; this.accounts = accounts; this.transactions = transactions;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID userId, WalletTransactionType type, BigDecimal amount, String destinationNumber,
                       String requestHash, String failureCode) {
        var user = users.findById(userId).orElse(null);
        if (user == null) return;
        var customer = accounts.findByUserId(userId).orElse(null);
        var settlement = type == WalletTransactionType.DEPOSIT
                ? accounts.findByKind(AccountKind.SETTLEMENT).orElse(null) : null;
        var destination = type == WalletTransactionType.DEPOSIT ? customer
                : destinationNumber == null ? null : accounts.findByAccountNumber(destinationNumber).orElse(null);
        var source = type == WalletTransactionType.DEPOSIT ? settlement : customer;
        String currency = customer == null ? "INR" : customer.getCurrency();
        transactions.save(new WalletTransaction(type, user, source, destination, amount, currency,
                TransactionStatus.FAILED, failureCode, requestHash));
    }
}
