package io.datapulse.tenancy.api;

import jakarta.validation.constraints.NotNull;

public record OwnershipTransferRequest(
        @NotNull Long newOwnerUserId
) {}
