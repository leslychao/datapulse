package io.datapulse.analytics.domain;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import io.datapulse.analytics.api.DataQualityStatusResponse;
import io.datapulse.analytics.api.DataQualityStatusResponse.AutomationBlocker;
import io.datapulse.analytics.api.DataQualityStatusResponse.SyncFreshness;
import io.datapulse.analytics.api.ReconciliationResponse;
import io.datapulse.analytics.config.AnalyticsQueryProperties;
import io.datapulse.analytics.persistence.DataQualityReadRepository;
import io.datapulse.analytics.persistence.WorkspaceConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DataQualityService {

    private final DataQualityReadRepository dataQualityReadRepository;
    private final WorkspaceConnectionRepository connectionRepository;
    private final NamedParameterJdbcTemplate pgJdbc;
    private final AnalyticsQueryProperties properties;

    private static final String SYNC_FRESHNESS_SQL = """
            SELECT
                mc.id AS connection_id,
                mc.name AS connection_name,
                mc.marketplace_type AS source_platform,
                mss.data_domain,
                mss.last_success_at
            FROM marketplace_sync_state mss
            JOIN marketplace_connection mc ON mss.marketplace_connection_id = mc.id
            WHERE mc.workspace_id = :workspaceId
              AND mc.status != 'ARCHIVED'
            ORDER BY mc.id, mss.data_domain
            """;

    public DataQualityStatusResponse getStatus(long workspaceId) {
        var params = new MapSqlParameterSource("workspaceId", workspaceId);

        var freshnessList = new ArrayList<SyncFreshness>();
        var blockerList = new ArrayList<AutomationBlocker>();
        OffsetDateTime now = OffsetDateTime.now();

        var dqProps = properties.dataQuality();

        pgJdbc.query(SYNC_FRESHNESS_SQL, params, (rs, rowNum) -> {
            long connectionId = rs.getLong("connection_id");
            String connectionName = rs.getString("connection_name");
            String platform = rs.getString("source_platform");
            String domain = rs.getString("data_domain");
            OffsetDateTime lastSuccess = rs.getObject("last_success_at", OffsetDateTime.class);

            int thresholdHours = resolveThresholdHours(domain, dqProps);
            boolean stale = lastSuccess == null
                    || lastSuccess.plusHours(thresholdHours).isBefore(now);

            freshnessList.add(new SyncFreshness(
                    connectionId, connectionName, platform, domain,
                    lastSuccess, stale, thresholdHours));

            if (stale && isBlockingDomain(domain)) {
                blockerList.add(new AutomationBlocker(
                        connectionId, connectionName, platform,
                        "Stale %s data (>%dh)".formatted(domain, thresholdHours),
                        true));
            }

            return null;
        });

        return new DataQualityStatusResponse(freshnessList, blockerList);
    }

    public List<ReconciliationResponse> getReconciliation(long workspaceId) {
        List<Long> connectionIds = connectionRepository.findConnectionIdsByWorkspaceId(workspaceId);
        if (connectionIds.isEmpty()) {
            return List.of();
        }
        return dataQualityReadRepository.findReconciliation(
                connectionIds, properties.dataQuality().residualAnomalyStdMultiplier());
    }

    private int resolveThresholdHours(String domain,
                                       AnalyticsQueryProperties.DataQualityProperties dqProps) {
        return switch (domain.toLowerCase()) {
            case "finance" -> dqProps.staleFinanceThresholdHours();
            case "advertising" -> dqProps.staleAdvertisingThresholdHours();
            default -> dqProps.staleStateThresholdHours();
        };
    }

    private boolean isBlockingDomain(String domain) {
        return "finance".equalsIgnoreCase(domain);
    }
}
