package io.datapulse.tenancy.api;

public record TenantResponse(
        long id,
        String name,
        String slug
) {}
