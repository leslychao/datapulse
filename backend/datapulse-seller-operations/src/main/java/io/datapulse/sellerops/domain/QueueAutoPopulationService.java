package io.datapulse.sellerops.domain;

import io.datapulse.sellerops.persistence.WorkingQueueAssignmentEntity;
import io.datapulse.sellerops.persistence.WorkingQueueAssignmentRepository;
import io.datapulse.sellerops.persistence.WorkingQueueDefinitionEntity;
import io.datapulse.sellerops.persistence.WorkingQueueDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueAutoPopulationService {

    private final WorkingQueueDefinitionRepository definitionRepository;
    private final WorkingQueueAssignmentRepository assignmentRepository;
    private final NamedParameterJdbcTemplate jdbc;

    @Transactional
    public void populateAllQueues() {
        List<WorkingQueueDefinitionEntity> queues =
                definitionRepository.findAllEnabledWithAutoCriteria();

        for (WorkingQueueDefinitionEntity queue : queues) {
            try {
                populateQueue(queue);
            } catch (Exception e) {
                log.error("Auto-population failed: queueId={}, name={}, workspaceId={}, error={}",
                        queue.getId(), queue.getName(), queue.getWorkspaceId(), e.getMessage(), e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void populateQueue(WorkingQueueDefinitionEntity queue) {
        Map<String, Object> criteria = queue.getAutoCriteria();
        if (criteria == null || criteria.isEmpty()) {
            return;
        }

        String entityType = (String) criteria.get("entity_type");
        List<Map<String, Object>> matchRules =
                (List<Map<String, Object>>) criteria.get("match_rules");

        if (entityType == null || matchRules == null || matchRules.isEmpty()) {
            return;
        }

        Set<Long> matchingIds = evaluateCriteria(queue.getWorkspaceId(), entityType, matchRules);
        if (matchingIds.isEmpty()) {
            autoResolveStaleAssignments(queue.getId(), entityType, Set.of());
            return;
        }

        int added = 0;
        for (Long entityId : matchingIds) {
            if (assignmentRepository.findActiveAssignment(
                    queue.getId(), entityType, entityId).isEmpty()) {
                var assignment = new WorkingQueueAssignmentEntity();
                assignment.setQueueDefinitionId(queue.getId());
                assignment.setEntityType(entityType);
                assignment.setEntityId(entityId);
                assignment.setStatus(QueueAssignmentStatus.PENDING.name());
                assignmentRepository.save(assignment);
                added++;
            }
        }

        autoResolveStaleAssignments(queue.getId(), entityType, matchingIds);

        if (added > 0) {
            log.info("Auto-populated queue: queueId={}, name={}, added={}",
                    queue.getId(), queue.getName(), added);
        }
    }

    private Set<Long> evaluateCriteria(long workspaceId, String entityType,
                                        List<Map<String, Object>> matchRules) {
        return switch (entityType) {
            case "price_action" -> evaluatePriceActionCriteria(workspaceId, matchRules);
            case "marketplace_offer" -> evaluateOfferCriteria(workspaceId, matchRules);
            default -> {
                log.warn("Unsupported entity_type for auto-criteria: entityType={}", entityType);
                yield Set.of();
            }
        };
    }

    private Set<Long> evaluatePriceActionCriteria(long workspaceId,
                                                   List<Map<String, Object>> matchRules) {
        var where = new StringBuilder(" WHERE pa.workspace_id = :workspaceId");
        var params = new MapSqlParameterSource("workspaceId", workspaceId);

        for (int i = 0; i < matchRules.size(); i++) {
            Map<String, Object> rule = matchRules.get(i);
            String field = (String) rule.get("field");
            String op = (String) rule.get("op");
            Object value = rule.get("value");

            String sqlField = mapPriceActionField(field);
            if (sqlField == null) {
                continue;
            }
            appendCondition(where, params, sqlField, op, value, "pa_" + i);
        }

        String sql = "SELECT pa.id FROM price_action pa" + where;
        List<Long> ids = jdbc.queryForList(sql, params, Long.class);
        return new HashSet<>(ids);
    }

    private Set<Long> evaluateOfferCriteria(long workspaceId,
                                             List<Map<String, Object>> matchRules) {
        var where = new StringBuilder("""
                 WHERE mc.workspace_id = :workspaceId
                """);
        var params = new MapSqlParameterSource("workspaceId", workspaceId);

        for (int i = 0; i < matchRules.size(); i++) {
            Map<String, Object> rule = matchRules.get(i);
            String field = (String) rule.get("field");
            String op = (String) rule.get("op");
            Object value = rule.get("value");

            String sqlField = mapOfferField(field);
            if (sqlField == null) {
                continue;
            }
            appendCondition(where, params, sqlField, op, value, "mo_" + i);
        }

        String sql = """
                SELECT mo.id
                FROM marketplace_offer mo
                JOIN marketplace_connection mc ON mc.id = mo.marketplace_connection_id
                """ + where;
        List<Long> ids = jdbc.queryForList(sql, params, Long.class);
        return new HashSet<>(ids);
    }

    private void autoResolveStaleAssignments(long queueId, String entityType,
                                              Set<Long> currentMatchingIds) {
        List<WorkingQueueAssignmentEntity> active =
                assignmentRepository.findAllActiveByQueue(queueId);

        for (WorkingQueueAssignmentEntity assignment : active) {
            if (!entityType.equals(assignment.getEntityType())) {
                continue;
            }
            if (QueueAssignmentStatus.IN_PROGRESS.name().equals(assignment.getStatus())) {
                continue;
            }
            if (!currentMatchingIds.contains(assignment.getEntityId())) {
                assignment.setStatus(QueueAssignmentStatus.DONE.name());
                assignment.setNote("Auto-resolved: condition no longer matches");
                assignmentRepository.save(assignment);
            }
        }
    }

    private String mapPriceActionField(String field) {
        return switch (field) {
            case "status" -> "pa.status";
            case "execution_mode" -> "pa.execution_mode";
            default -> null;
        };
    }

    private String mapOfferField(String field) {
        return switch (field) {
            case "status" -> "mo.status";
            default -> null;
        };
    }

    private void appendCondition(StringBuilder where, MapSqlParameterSource params,
                                  String sqlField, String op, Object value, String paramName) {
        switch (op) {
            case "eq" -> {
                where.append(" AND ").append(sqlField).append(" = :").append(paramName);
                params.addValue(paramName, value);
            }
            case "neq" -> {
                where.append(" AND ").append(sqlField).append(" != :").append(paramName);
                params.addValue(paramName, value);
            }
            case "in" -> {
                where.append(" AND ").append(sqlField).append(" IN (:").append(paramName).append(")");
                params.addValue(paramName, value);
            }
            case "gt" -> {
                where.append(" AND ").append(sqlField).append(" > :").append(paramName);
                params.addValue(paramName, value);
            }
            case "lt" -> {
                where.append(" AND ").append(sqlField).append(" < :").append(paramName);
                params.addValue(paramName, value);
            }
            case "gte" -> {
                where.append(" AND ").append(sqlField).append(" >= :").append(paramName);
                params.addValue(paramName, value);
            }
            case "lte" -> {
                where.append(" AND ").append(sqlField).append(" <= :").append(paramName);
                params.addValue(paramName, value);
            }
            default -> log.warn("Unsupported auto-criteria operator: op={}", op);
        }
    }
}
