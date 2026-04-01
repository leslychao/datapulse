package io.datapulse.sellerops.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record UpdateSavedViewRequest(
        @NotBlank @Size(max = 200) String name,
        boolean isDefault,
        @NotNull Map<String, Object> filters,
        String sortColumn,
        String sortDirection,
        @NotNull List<String> visibleColumns,
        boolean groupBySku
) {
}
