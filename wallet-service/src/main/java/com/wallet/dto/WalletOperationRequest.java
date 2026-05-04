package com.wallet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletOperationRequest(
        @NotNull(message = "walletId is required")
        UUID walletId,

        @NotNull(message = "operationType is required")
        OperationType operationType,

        @NotNull(message = "amount is required")
        @Positive(message = "amount must be positive")
        BigDecimal amount
) {}
