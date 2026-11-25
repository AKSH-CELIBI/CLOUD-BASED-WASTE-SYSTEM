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
                : destinationNumber == null ? null : accounts.findByAccountNumber…4174 tokens truncated…ork.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WalletTransactionExecutor {
    private final UserRepository users;
    private final WalletAccountRepository accounts;
    private final WalletTransactionRepository transactions;
    private final LedgerEntryRepository ledger;
    private final IdempotencyRecordRepository idempotency;

    public WalletTransactionExecutor(UserRepository users, WalletAccountRepository accounts,
                                    WalletTransactionRepository transactions, LedgerEntryRepository ledger,
                                    IdempotencyRecordRepository idempotency) {
        this.users = users; this.accounts = accounts; this.transactions = transactions; this.ledger = ledger;
        this.idempotency = idempotency;
    }

    @Transactional
    public ExecutionResult execute(WalletTransactionType type, UUID userId, String key, String requestHash,
                                   BigDecimal amount, String destinationNumber) {
        User user = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED", "Authentication is required"));
        var prior = idempotency.findByUserIdAndKey(userId, key);
        if (prior.isPresent()) return replay(prior.get(), requestHash);

        WalletAccount customer = accounts.findByUserId(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "WALLET_NOT_FOUND", "Wallet account was not found"));
        WalletAccount settlement = null;
        WalletAccount destination = null;
        if (type == WalletTransactionType.DEPOSIT) {
            settlement = accounts.findByKind(com.example.wallet.wallet.AccountKind.SETTLEMENT).orElseThrow(() ->
                    new IllegalStateException("Settlement account is missing"));
            destination = customer;
        } else {
            destination = accounts.findByAccountNumber(destinationNumber).orElseThrow(() -> new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "DESTINATION_NOT_FOUND", "Destination account was not found"));
            if (destination.getId().equals(customer.getId())) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SELF_TRANSFER", "A wallet cannot transfer to itself");
            }
        }

        WalletAccount source = type == WalletTransactionType.DEPOSIT ? settlement : customer;
        List<WalletAccount> locked = accounts.findAllForUpdateInStableOrder(List.of(source.getId(), destination.getId()));
        Map<UUID, WalletAccount> byId = new HashMap<>();
        locked.forEach(account -> byId.put(account.getId(), account));
        source = byId.get(source.getId());
        destination = byId.get(destination.getId());
        if (source == null || destination == null) throw new IllegalStateException("Wallet account disappeared while locking");

        // Account locks serialize requests from the same wallet, including races on a new idempotency key.
        prior = idempotency.findByUserIdAndKey(userId, key);
        if (prior.isPresent()) return replay(prior.get(), requestHash);
        if (source.getStatus() != AccountStatus.ACTIVE || destination.getStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ACCOUNT_FROZEN", "A frozen wallet cannot participate in transactions");
        }
        if (!source.getCurrency().equals(destination.getCurrency())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CURRENCY_MISMATCH", "Wallets must use the same currency");
        }
        if (source.getBalance().compareTo(amount) < 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS", "Wallet balance is insufficient");
        }

        BigDecimal sourceBalance = source.getBalance().subtract(amount).setScale(4);
        BigDecimal destinationBalance = destination.getBalance().add(amount).setScale(4);
        source.changeBalance(sourceBalance);
        destination.changeBalance(destinationBalance);
        WalletTransaction transaction = transactions.saveAndFlush(new WalletTransaction(type, user, source, destination,
                amount, source.getCurrency(), TransactionStatus.COMPLETED, null, requestHash));
        ledger.saveAllAndFlush(List.of(
                new LedgerEntry(transaction, source, LedgerDirection.DEBIT, amount, sourceBalance),
                new LedgerEntry(transaction, destination, LedgerDirection.CREDIT, amount, destinationBalance)));
        idempotency.saveAndFlush(new IdempotencyRecord(user, key, requestHash, transaction));
        return new ExecutionResult(transaction, false);
    }

    private ExecutionResult replay(IdempotencyRecord record, String requestHash) {
        if (!record.getRequestHash().equals(requestHash)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "IDEMPOTENCY_KEY_REUSED",
                    "Idempotency-Key was already used with a different request");
        }
        return new ExecutionResult(record.getTransaction(), true);
    }
}
