package io.datapulse.etl.nextgen.dto;

import java.util.List;

public record MarketplaceScope(
    String marketplace,
    List<String> sourceKeys
) {
}
