package io.datapulse.integration.api;

import java.util.List;

public record TriggerSyncRequest(
        List<String> domains
) {
}
