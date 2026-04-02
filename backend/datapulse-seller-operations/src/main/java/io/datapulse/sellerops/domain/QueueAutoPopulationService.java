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
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
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
    private final TransactionTemplate transactionTemplate;

    public void populateAllQueues() {
        List<WorkingQueueDefinitionEntity> queues =
                definitionRepository.findAllEnabledWithAutoCriteria();

        for (WorkingQueueDefinitionEntity queue : queues) {
            try {
                transactionTemplate.executeWithoutResult(status -> populateQueue(queue));
            } catch (Exception e) {
                log.error("Auto-population failed: queueId={}, name={}, workspaceId={}, error={}",
                        queue.getId(), queue.getName(), queue.getWorkspaceId(), e.getMessage(), e);
            }
        }
    }

    @Transactional(readOnly = true)
    public long countByCriteria(long workspaceId, Map<String, Object> criteria) {
        if (criteria == null || criteria.isEmpty()) {
            return 0;
        }
        if (!(criteria.get("entity_type") instanceof String entityType)) {
            return 0;
        }
        if (!(criteria.get("match_rules") instanceof List<?> rawRules) || rawRules.isEmpty()) {
            return 0;
        }
        List<MatchRule> matchRules = parseMatchRules(rawRules);
        if (matchRules.isEmpty()) {
            return 0;
        }
        return evaluateCriteria(workspaceId, entityType, matchRules).size();
    }

    private void populateQueue(WorkingQueueDefinitionEntity queue) {
        Map<String, Object> criteria = queue.getAutoCriteria();
        if (criteria == null || criteria.isEmpty()) {
            return;
        }

        if (!(criteria.get("entity_type") instanceof String entityType)) {
            return;
        }
        if (!(criteria.get("match_rules") instanceof List<?> rawRules) || rawRules.isEmpty()) {
            return;
        }

        List<MatchRule> matchRules = parseMatchRules(rawRules);
        if (matchRules.isEmpty()) {
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
                                        List<MatchRule> matchRules) {
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
                                                   List<MatchRule> matchRules) {
        var where = new StringBuilder(" WHERE pa.workspace_id = :workspaceId");
        var params = new MapSqlParameterSource("workspaceId", workspaceId);

        for (int i = 0; i < matchRules.size(); i++) {
            MatchRule rule = matchRules.get(i);
            String sqlField = mapPriceActionField(rule.field);
            if (sqlField == null) {
                continue;
            }
            appendCondition(where, params, sqlField, rule.op, rule.value, "pa_" + i);
        }

        String sql = "SELECT pa.id FROM price_action pa" + where;
        List<Long> ids = jdbc.queryForList(sql, params, Long.class);
        return new HashSet<>(ids);
    }

    private Set<Long> evaluateOfferCriteria(long workspaceId,
                                             List<MatchRule> matchRules) {
        var where = new StringBuilder("""
                 WHERE mc.workspace_id = :workspaceId
                """);
        var params = new MapSqlParameterSource("workspaceId", workspaceId);

        for (int i = 0; i < matchRules.size(); i++) {
            MatchRule rule = matchRules.get(i);
            String sqlField = mapOfferField(rule.field);
            if (sqlField == null) {
                continue;
            }
            appendCondition(where, params, sqlField, rule.op, rule.value, "mo_" + i);
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

    private List<MatchRule> parseMatchRules(List<?> rawRules) {
        List<MatchRule> result = new ArrayList<>(rawRules.size());
        for (Object rawRule : rawRules) {
            if (rawRule instanceof Map<?, ?> ruleMap) {
                var rule = toMatchRule(ruleMap);
                if (rule != null) {
                    result.add(rule);
                }
            }
        }
        return result;
    }

    private MatchRule toMatchRule(Map<?, ?> raw) {
        if (!(raw.get("field") instanceof String field)) {
            return null;
        }
        if (!(raw.get("op") instanceof String op)) {
            return null;
        }
        return new MatchRule(field, op, raw.get("value"));
    }

    private record MatchRule(String field, String op, Object value) {}
}
