package io.datapulse.domain.dto.credentials;

public sealed interface MarketplaceCredentials permits WbCredentials, OzonCredentials {

}
