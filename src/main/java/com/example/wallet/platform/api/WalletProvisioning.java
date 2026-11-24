package com.example.wallet.platform.api;

import java.util.UUID;

/** AI-2 implementation boundary. Implementations must join the caller's transaction. */
public interface WalletProvisioning {
    String createWalletForUser(UUID userId);
}
