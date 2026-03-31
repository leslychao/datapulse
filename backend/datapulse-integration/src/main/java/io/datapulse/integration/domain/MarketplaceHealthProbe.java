package io.datapulse.integration.domain;

import java.util.Map;

public interface MarketplaceHealthProbe {

    MarketplaceType marketplaceType();

    HealthProbeResult probe(Map<String, String> credentials);
}
