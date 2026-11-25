package com.example.wallet.admin;

import jakarta.validation.constraints.NotNull;

public record FreezeRequest(@NotNull Boolean frozen) {}
