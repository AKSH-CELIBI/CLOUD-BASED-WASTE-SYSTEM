package com.example.wallet.transaction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DepositRequest(@NotBlank @Pattern(regexp = "^[0-9]{1,15}(\\.[0-9]{1,4})?$") String amount) {}
