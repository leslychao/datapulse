package io.datapulse.audit.domain;

import java.util.List;

import io.datapulse.audit.api.AlertRuleResponse;
import io.datapulse.audit.api.UpdateAlertRuleRequest;
import io.datapulse.audit.persistence.AlertRuleRepository;
import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlertRuleService {

    private final AlertRuleRepository alertRuleRepository;

    @Transactional(readOnly = true)
    public List<AlertRuleResponse> listRules(long workspaceId) {
        return alertRuleRepository.findByWorkspaceId(workspaceId);
    }

    @Transactional(readOnly = true)
    public AlertRuleResponse getRule(long id, long workspaceId) {
        return alertRuleRepository.findById(id, workspaceId)
                .orElseThrow(() -> NotFoundException.of(MessageCodes.ALERT_RULE_NOT_FOUND, id));
    }

    @Transactional
    public AlertRuleResponse updateRule(long id, long workspaceId, UpdateAlertRuleRequest request) {
        ensureRuleExists(id, workspaceId);

        alertRuleRepository.update(id, workspaceId,
                request.config(), request.enabled(), request.severity(), request.blocksAutomation());

        return getRule(id, workspaceId);
    }

    @Transactional
    public AlertRuleResponse activate(long id, long workspaceId) {
        ensureRuleExists(id, workspaceId);
        alertRuleRepository.setEnabled(id, workspaceId, true);
        return getRule(id, workspaceId);
    }

    @Transactional
    public AlertRuleResponse deactivate(long id, long workspaceId) {
        ensureRuleExists(id, workspaceId);
        alertRuleRepository.setEnabled(id, workspaceId, false);
        return getRule(id, workspaceId);
    }

    private void ensureRuleExists(long id, long workspaceId) {
        alertRuleRepository.findById(id, workspaceId)
                .orElseThrow(() -> NotFoundException.of(MessageCodes.ALERT_RULE_NOT_FOUND, id));
    }
}
