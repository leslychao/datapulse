package io.datapulse.promotions.domain;

import io.datapulse.integration.domain.CredentialKeys;
import io.datapulse.integration.domain.CredentialStore;
import io.datapulse.integration.domain.MarketplaceType;
import io.datapulse.integration.domain.event.CredentialAccessedEvent;
import io.datapulse.integration.persistence.SecretReferenceEntity;
import io.datapulse.integration.persistence.SecretReferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromoCredentialResolver {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final SecretReferenceRepository secretReferenceRepository;
    private final CredentialStore credentialStore;
    private final ApplicationEventPublisher eventPublisher;

    private static final String RESOLVE_SQL = """
            SELECT mc.id AS connection_id,
                   mc.workspace_id,
                   mc.marketplace_type,
                   mc.secret_reference_id
            FROM marketplace_connection mc
            WHERE mc.id = :connectionId
            """;

    public PromoExecutionContext resolve(long connectionId) {
        var params = new MapSqlParameterSource("connectionId", connectionId);

        var rows = jdbcTemplate.query(RESOLVE_SQL, params, (rs, rowNum) ->
                new ConnectionRow(
                        rs.getLong("connection_id"),
                        rs.getLong("workspace_id"),
                        rs.getString("marketplace_type"),
                        rs.getLong("secret_reference_id")));

        if (rows.isEmpty()) {
            throw new IllegalStateException(
                    "Marketplace connection not found: connectionId=%d".formatted(connectionId));
        }

        ConnectionRow row = rows.get(0);

        SecretReferenceEntity secretRef = secretReferenceRepository
                .findById(row.secretReferenceId())
                .orElseThrow(() -> new IllegalStateException(
                        "SecretReference not found: id=%d, connectionId=%d"
                                .formatted(row.secretReferenceId(), connectionId)));

        Map<String, String> credentials = credentialStore.read(
                secretRef.getVaultPath(), secretRef.getVaultKey());

        eventPublisher.publishEvent(new CredentialAccessedEvent(
                connectionId, row.workspaceId(), "promo_execution"));

        MarketplaceType marketplace = MarketplaceType.valueOf(row.marketplaceType());

        log.debug("Promo credentials resolved: connectionId={}, marketplace={}",
                connectionId, marketplace);

        return new PromoExecutionContext(connectionId, marketplace, credentials);
    }

    public record PromoExecutionContext(
            long connectionId,
            MarketplaceType marketplace,
            Map<String, String> credentials
    ) {
        public String ozonClientId() {
            return credentials.getOrDefault(CredentialKeys.OZON_CLIENT_ID, "");
        }

        public String ozonApiKey() {
            return credentials.getOrDefault(CredentialKeys.OZON_API_KEY, "");
        }
    }

    private record ConnectionRow(long connectionId, long workspaceId,
                                  String marketplaceType, long secretReferenceId) {}
}
