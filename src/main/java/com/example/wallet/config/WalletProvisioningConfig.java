package com.example.wallet.config;

import com.example.wallet.error.ApiException;
import com.example.wallet.platform.api.WalletProvisioning;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

@Configuration
public class WalletProvisioningConfig {
    @Bean
    @ConditionalOnMissingBean(WalletProvisioning.class)
    WalletProvisioning walletProvisioningPending() {
        return userId -> {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "WALLET_PROVISIONING_UNAVAILABLE",
                    "Wallet provisioning is not configured yet");
        };
    }
}
