package io.datapulse.bidding.domain.strategy.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MinimalPresenceConfig() {
}
