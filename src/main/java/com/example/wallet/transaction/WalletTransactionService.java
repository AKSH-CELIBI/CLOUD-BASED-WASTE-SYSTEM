package com.example.wallet.transaction;

import com.example.wallet.error.ApiException;
import com.example.wallet.identity.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class WalletTransactionService {
    private final UserRepository users;
    private final IdempotencyRecordRepository idempotency;
    private final WalletTransactionExecutor executor;
    private final FailedAttemptRecorder failedAttempts;

    public WalletTransactionService(UserRepository users, IdempotencyRecordRepository idempotency,
                                   WalletTransactionExecutor executor, FailedAttemptRecorder failedAttempts) {
        this.users = users; this.idempotency = idempotency; this.executor = executor; this.failedAttempts = failedAttempts;
    }

    public ExecutionResult deposit(String email, String key, String amountText) {
        return execute(email, key, WalletTransactionType.DEPOSIT, amountText, null);
    }

    public ExecutionResult transfer(String email, String key, String destinationNumber, String amountText) {
        return execute(email, key, WalletTransactionType.TRANSFER, amountText, destinationNumber);
    }

    private ExecutionResult execute(String email, String key, WalletTransactionType type, String amountText, String destination) {
        String normalizedKey = key == null ? "" : key.trim();
        if (normalizedKey.isEmpty() || normalizedKey.length() > 128) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Idempotency-Key must contain 1 to 128 characters",
                    java.util.Map.of("Idempotency-Key", "Must contain 1 to 128 characters"));
        }
        BigDecimal amount;
        try { amount = new BigDecimal(amountText).setScale(4, java.math.RoundingMode.UNNECESSARY); }
        catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Amount must be a decimal with at most four fractional digits",
                    java.util.Map.of("amount", "Must be a positive decimal with at most four fractional digits"));
        }
        if (amount.signum() <= 0 || amount.precision() > 19) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Amount is outside the supported range",
                    java.util.Map.of("amount", "Must be greater than zero and fit DECIMAL(19,4)"));
        }
        var user = users.findByEmail(email).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required"));
        String hash = hash(type + "|" + (destination == null ? "" : destination) + "|" + amount.toPlainString());
        try {
            var existing = idempotency.findByUserIdAndKey(user.getId(), normalizedKey);
            if (existing.isPresent()) {
                if (!existing.get().getRequestHash().equals(hash)) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "IDEMPOTENCY_KEY_REUSED",
                            "Idempotency-Key was already used with a different request");
                }
                return new ExecutionResult(existing.get().getTransaction(), true);
            }
            return executor.execute(type, user.getId(), normalizedKey, hash, amount, destination);
        } catch (ApiException exception) {
            try { failedAttempts.record(user.getId(), type, amount, destination, hash, exception.code()); }
            catch (RuntimeException recordingFailure) { exception.addSuppressed(recordingFailure); }
            throw exception;
        } catch (DataIntegrityViolationException exception) {
            var winner = idempotency.findByUserIdAndKey(user.getId(), normalizedKey);
            if (winner.isPresent()) {
                if (!winner.get().getRequestHash().equals(hash)) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "IDEMPOTENCY_KEY_REUSED",
                            "Idempotency-Key was already used with a different request");
                }
                return new ExecutionResult(winner.get().getTransaction(), true);
            }
            throw new ApiException(HttpStatus.CONFLICT, "CONFLICT", "The request conflicts with existing data");
        }
    }

    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
