package com.example.wallet.transaction;

public record ExecutionResult(WalletTransaction transaction, boolean replayed) {}
