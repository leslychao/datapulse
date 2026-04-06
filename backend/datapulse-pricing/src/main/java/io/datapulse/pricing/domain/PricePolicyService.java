package io.datapulse.pricing.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.pricing.api.CreatePricePolicyRequest;
import io.datapulse.pricing.api.PricePolicyMapper;
import io.datapulse.pricing.api.PricePolicyResponse;
import io.datapulse.pricing.api.PricePolicySummaryResponse;
import io.datapulse.pricing.api.UpdatePricePolicyRequest;
import io.datapulse.pricing.persistence.PricePolicyEntity;
import io.datapulse.pricing.persistence.PricePolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PricePolicyService {

    private final PricePolicyRepository policyRepository;
    private final PricePolicyMapper policyMapper;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final FullAutoSafetyGate fullAutoSafetyGate;

    @Transactional
    public PricePolicyResponse createPolicy(CreatePricePolicyRequest request,
                                            long workspaceId, long userId) {
        validateStrategyParams(request.strategyType(), request.strategyParams());

        var entity = new PricePolicyEntity();
        entity.setWorkspaceId(workspaceId);
        entity.setName(request.name());
        entity.setStatus(PolicyStatus.DRAFT);
        entity.setStrategyType(request.strategyType());
        entity.setStrategyParams(request.strategyParams());
        entity.setMinMarginPct(request.minMarginPct());
        entity.setMaxPriceChangePct(request.maxPriceChangePct());
        entity.setMinPrice(request.minPrice());
        entity.setMaxPrice(request.maxPrice());
        entity.setGuardConfig(request.guardConfig());
        entity.setExecutionMode(request.executionMode());
        entity.setExecutionModeChangedAt(java.time.OffsetDateTime.now());
        entity.setApprovalTimeoutHours(
            request.approvalTimeoutHours() != null ? request.approvalTimeoutHours()
                : request.executionMode() == ExecutionMode.SEMI_AUTO ? 72 : 0);
        entity.setPriority(request.priority() != null ? request.priority() : 0);
        entity.setVersion(1);
        entity.setLastPreviewVersion(0);
        entity.setCreatedBy(userId);

        PricePolicyEntity saved = policyRepository.save(entity);
        log.info("Policy created: id={}, workspace={}, strategyType={}",
                saved.getId(), workspaceId, saved.getStrategyType());

        return policyMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<PricePolicySummaryResponse> listPolicies(long workspaceId, PolicyStatus status,
                                                         PolicyType strategyType) {
        List<PricePolicyEntity> entities;

        if (status != null && strategyType != null) {
            entities = policyRepository.findAllByWorkspaceIdAndStatusAndStrategyType(
                    workspaceId, status, strategyType);
        } else if (status != null) {
            entities = policyRepository.findAllByWorkspaceIdAndStatus(workspaceId, status);
        } else if (strategyType != null) {
            entities = policyRepository.findAllByWorkspaceIdAndStrategyType(workspaceId, strategyType);
        } else {
            entities = policyRepository.findAllByWorkspaceId(workspaceId);
        }

        return policyMapper.toSummaries(entities);
    }

    @Transactional(readOnly = true)
    public Page<PricePolicySummaryResponse> listPoliciesPaged(
            long workspaceId, List<PolicyStatus> statuses,
            PolicyType strategyType, Pageable pageable) {
        boolean hasStatuses = statuses != null && !statuses.isEmpty();
        Page<PricePolicyEntity> page;

        if (hasStatuses && strategyType != null) {
            page = policyRepository.findAllByWorkspaceIdAndStatusInAndStrategyType(
                    workspaceId, statuses, strategyType, pageable);
        } else if (hasStatuses) {
            page = policyRepository.findAllByWorkspaceIdAndStatusIn(
                    workspaceId, statuses, pageable);
        } else if (strategyType != null) {
            page = policyRepository.findAllByWorkspaceIdAndStrategyType(
                    workspaceId, strategyType, pageable);
        } else {
            page = policyRepository.findAllByWorkspaceId(workspaceId, pageable);
        }

        return page.map(policyMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public PricePolicyResponse getPolicy(long policyId, long workspaceId) {
        PricePolicyEntity entity = findPolicyOrThrow(policyId, workspaceId);
        return policyMapper.toResponse(entity);
    }

    @Transactional
    public PricePolicyResponse updatePolicy(long policyId, UpdatePricePolicyRequest request,
                                            long workspaceId) {
        PricePolicyEntity entity = findPolicyOrThrow(policyId, workspaceId);

        if (entity.getStatus() == PolicyStatus.ARCHIVED) {
            throw BadRequestException.of(MessageCodes.PRICING_POLICY_ARCHIVED);
        }

        validateStrategyParams(request.strategyType(), request.strategyParams());

        if (request.executionMode() == ExecutionMode.FULL_AUTO
                && entity.getExecutionMode() != ExecutionMode.FULL_AUTO) {
            fullAutoSafetyGate.validateOnSwitch(entity, request.confirmFullAuto());
        }

        boolean logicChanged = isLogicChanged(entity, request);

        entity.setName(request.name());
        entity.setStrategyType(request.strategyType());
        entity.setStrategyParams(request.strategyParams());
        entity.setMinMarginPct(request.minMarginPct());
        entity.setMaxPriceChangePct(request.maxPriceChangePct());
        entity.setMinPrice(request.minPrice());
        entity.setMaxPrice(request.maxPrice());
        entity.setGuardConfig(request.guardConfig());
        if (request.executionMode() != entity.getExecutionMode()) {
            entity.setExecutionModeChangedAt(java.time.OffsetDateTime.now());
        }
        entity.setExecutionMode(request.executionMode());
        if (request.approvalTimeoutHours() != null) {
            entity.setApprovalTimeoutHours(request.approvalTimeoutHours());
        }
        entity.setPriority(request.priority() != null ? request.priority() : entity.getPriority());

        if (logicChanged) {
            entity.setVersion(entity.getVersion() + 1);
        }

        PricePolicyEntity saved = policyRepository.save(entity);
        log.info("Policy updated: id={}, version={}, logicChanged={}",
                saved.getId(), saved.getVersion(), logicChanged);

        if (logicChanged && saved.getStatus() == PolicyStatus.ACTIVE) {
            eventPublisher.publishEvent(new PolicyLogicChangedEvent(
                    saved.getId(), workspaceId, saved.getVersion()));
        }

        return policyMapper.toResponse(saved);
    }

    @Transactional
    public void activatePolicy(long policyId, long workspaceId) {
        PricePolicyEntity entity = findPolicyOrThrow(policyId, workspaceId);

        if (entity.getStatus() != PolicyStatus.DRAFT && entity.getStatus() != PolicyStatus.PAUSED) {
            throw BadRequestException.of(MessageCodes.PRICING_POLICY_INVALID_STATE,
                    entity.getStatus().name(), "ACTIVE");
        }

        enforceMandatoryPreviewGate(entity);

        entity.setStatus(PolicyStatus.ACTIVE);
        policyRepository.save(entity);
        log.info("Policy activated: id={}", policyId);

        eventPublisher.publishEvent(new PolicyActivatedEvent(policyId, workspaceId));
    }

    @Transactional
    public void markPreviewExecuted(long policyId, long workspaceId) {
        PricePolicyEntity entity = findPolicyOrThrow(policyId, workspaceId);
        entity.setLastPreviewVersion(entity.getVersion());
        policyRepository.save(entity);
    }

    private void enforceMandatoryPreviewGate(PricePolicyEntity entity) {
        if (entity.getExecutionMode() != ExecutionMode.FULL_AUTO) {
            return;
        }
        boolean hasConnectionScope = policyRepository.existsConnectionScopeAssignment(entity.getId());
        if (!hasConnectionScope) {
            return;
        }
        if (entity.getLastPreviewVersion() == null
                || entity.getLastPreviewVersion() < entity.getVersion()) {
            throw BadRequestException.of(MessageCodes.PRICING_POLICY_PREVIEW_REQUIRED);
        }
    }

    @Transactional
    public void pausePolicy(long policyId, long workspaceId) {
        PricePolicyEntity entity = findPolicyOrThrow(policyId, workspaceId);

        if (entity.getStatus() != PolicyStatus.ACTIVE) {
            throw BadRequestException.of(MessageCodes.PRICING_POLICY_INVALID_STATE,
                    entity.getStatus().name(), "PAUSED");
        }

        entity.setStatus(PolicyStatus.PAUSED);
        policyRepository.save(entity);
        log.info("Policy paused: id={}", policyId);
    }

    @Transactional
    public void archivePolicy(long policyId, long workspaceId) {
        PricePolicyEntity entity = findPolicyOrThrow(policyId, workspaceId);

        if (entity.getStatus() == PolicyStatus.ARCHIVED) {
            return;
        }

        entity.setStatus(PolicyStatus.ARCHIVED);
        policyRepository.save(entity);
        log.info("Policy archived: id={}", policyId);
    }

    private PricePolicyEntity findPolicyOrThrow(long policyId, long workspaceId) {
        return policyRepository.findByIdAndWorkspaceId(policyId, workspaceId)
                .orElseThrow(() -> NotFoundException.entity("PricePolicy", policyId));
    }

    private boolean isLogicChanged(PricePolicyEntity entity, UpdatePricePolicyRequest request) {
        return entity.getStrategyType() != request.strategyType()
                || !safeEquals(entity.getStrategyParams(), request.strategyParams())
                || !safeEquals(entity.getMinMarginPct(), request.minMarginPct())
                || !safeEquals(entity.getMaxPriceChangePct(), request.maxPriceChangePct())
                || !safeEquals(entity.getMinPrice(), request.minPrice())
                || !safeEquals(entity.getMaxPrice(), request.maxPrice())
                || !safeEquals(entity.getGuardConfig(), request.guardConfig())
                || entity.getExecutionMode() != request.executionMode();
    }

    private boolean safeEquals(Object a, Object b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.equals(b);
    }

    private void validateStrategyParams(PolicyType strategyType, String strategyParamsJson) {
        if (strategyType == PolicyType.MANUAL_OVERRIDE) {
            throw BadRequestException.of(MessageCodes.VALIDATION_FAILED, "strategyType",
                    "MANUAL_OVERRIDE cannot be used for policies");
        }

        if (strategyParamsJson == null || strategyParamsJson.isBlank()) {
            throw BadRequestException.of(MessageCodes.VALIDATION_FAILED, "strategyParams");
        }

        try {
            switch (strategyType) {
                case TARGET_MARGIN -> validateTargetMarginParams(
                        objectMapper.readValue(strategyParamsJson, TargetMarginParams.class));
                case PRICE_CORRIDOR -> validatePriceCorridorParams(
                        objectMapper.readValue(strategyParamsJson, PriceCorridorParams.class));
                case VELOCITY_ADAPTIVE -> validateVelocityAdaptiveParams(
                        objectMapper.readValue(strategyParamsJson,
                                VelocityAdaptiveParams.class));
                case STOCK_BALANCING -> validateStockBalancingParams(
                        objectMapper.readValue(strategyParamsJson,
                                StockBalancingParams.class));
                case COMPOSITE -> validateCompositeParams(
                        objectMapper.readValue(strategyParamsJson,
                                CompositeParams.class));
                case COMPETITOR_ANCHOR -> validateCompetitorAnchorParams(
                        objectMapper.readValue(strategyParamsJson,
                                CompetitorAnchorParams.class));
                case MANUAL_OVERRIDE -> throw new IllegalStateException("unreachable");
            }
        } catch (JsonProcessingException e) {
            throw BadRequestException.of(MessageCodes.VALIDATION_FAILED, e, "strategyParams");
        }
    }

    private void validateTargetMarginParams(TargetMarginParams params) {
        if (params.targetMarginPct() == null) {
            throw BadRequestException.of(MessageCodes.VALIDATION_FAILED,
                    "strategyParams.targetMarginPct is required");
        }
        java.math.BigDecimal pct = params.targetMarginPct();
        if (pct.compareTo(java.math.BigDecimal.ZERO) < 0
                || pct.compareTo(java.math.BigDecimal.ONE) >= 0) {
            throw BadRequestException.of(MessageCodes.VALIDATION_FAILED,
                    "strategyParams.targetMarginPct must be in [0, 1)");
        }
        if (params.commissionSource() == TargetMarginParams.CommissionSource.MANUAL
                && params.commissionManualPct() == null) {
            throw BadRequestException.of(MessageCodes.VALIDATION_FAILED,
                    "strategyParams.commissionManualPct required when commissionSource=MANUAL");
        }
        if (params.logisticsSource() == TargetMarginParams.LogisticsSource.MANUAL
                && params.logisticsManualAmount() == null) {
            throw BadRequestException.of(MessageCodes.VALIDATION_FAILED,
                    "strategyParams.logisticsManualAmount required when logisticsSource=MANUAL");
        }
        if (params.roundingStep() != null
                && params.roundingStep().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw BadRequestException.of(MessageCodes.VALIDATION_FAILED,
                    "strategyParams.roundingStep must be > 0");
        }
    }

    private void validatePriceCorridorParams(PriceCorridorParams params) {
        if (params.minPrice() == null && params.maxPrice() == null) {
            throw BadRequestException.of(MessageCodes.VALIDATION_FAILED,
                    "strategyParams must have at least minPrice or maxPrice");
        }
        if (params.minPrice() != null && params.maxPrice() != null
                && params.minPrice().compareTo(params.maxPrice()) > 0) {
            throw BadRequestException.of(MessageCodes.VALIDATION_FAILED,
                    "strategyParams.minPrice must be <= maxPrice");
        }
    }

    private void validateStockBalancingParams(StockBalancingParams params) {
        if (params.criticalDaysOfCover() != null
                && (params.criticalDaysOfCover() < 1
                || params.criticalDaysOfCover() > 30)) {
            throw BadRequestException.of(MessageCodes.VALIDATION_FAILED,
                    "strategyParams.criticalDaysOfCover must be in [1, 30]");
        }
        if (params.overstockDaysOfCover() != null
                && (params.overstockDaysOfCover() < 30
                || params.overstockDaysOfCover() > 365)) {
            throw BadRequestException.of(MessageCodes.VALIDATION_FAILED,
                    "strategyParams.overstockDaysOfCover must be in [30, 365]");
        }
        if (params.criticalDaysOfCover() != null && params.overstockDaysOfCover() != null
                && params.criticalDaysOfCover() >= params.overstockDaysOfCover()) {
            throw BadRequestException.of(MessageCodes.VALIDATION_FAILED,
                    "strategyParams.criticalDaysOfCover must be < overstockDaysOfCover");
        }
        if (params.stockoutMarkupPct() != null
                && (params.stockoutMarkupPct().compareTo(
                        new java.math.BigDecimal("0.01")) < 0
                || params.stockoutMarkupPct().compareTo(
                        new java.math.BigDecimal("0.30")) > 0)) {
            throw BadRequestException.of(MessageCodes.VALIDATION_FAILED,
                    "strategyParams.stockoutMarkupPct must be in [0.01, 0.30]");
        }
        if (params.overstockDiscountFactor() != null
                && (params.overstockDiscountFactor().compareTo(
                        new java.math.BigDecimal("0.01")) < 0
                || params.overstockDiscountFactor().compareTo(
                        new java.math.BigDecimal("0.50")) > 0)) {
            throw BadRequestException.of(MessageCodes.VALIDATION_FAILED,
                    "strategyParams.overstockDiscountFactor must be in [0.01, 0.50]");
        }
        if (params.maxDiscountPct() != null
                && (params.maxDiscountPct().compareTo(
                        new java.math.BigDecimal("0.01")) < 0
                || params.maxDiscountPct().compareTo(
                        new java.math.BigDecimal("0.50")) > 0)) {
            throw BadRequestException.of(MessageCodes.VALIDATION_FAILED,
                    "strategyParams.maxDiscountPct must be in [0.01, 0.50]");
        }
        if (params.leadTimeDays() != null
                && (params.leadTimeDays() < 1
                || params.leadTimeDays() > 180)) {
            throw BadRequestException.of(MessageCodes.VALIDATION_FAILED,
                    "strategyParams.leadTimeDays must be in [1, 180]");
        }
        if (params.roundingStep() != null
                && params.roundingStep()
                        .compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw BadRequestException.of(MessageCodes.VALIDATION_FAILED,
                    "strategyParams.roundingStep must be > 0");
        }
    }

    private void validateCompositeParams(CompositeParams params) {
        if (params.components() == null || params.components().isEmpty()) {
            throw BadRequestException.of(MessageCodes.VALIDATION_FAILED,
                    "strategyParams.components must not be empty");
        }
        for (int i = 0; i < params.components().size(); i++) {
            CompositeParams.ComponentConfig comp = params.components().get(i);
            if (comp.strategyType() == null) {
                throw BadRequestException.of(MessageCodes.VALIDATION_FAILED,
                        "strategyParams.components[%d].strategyType is required".formatted(i));
            }
            if (comp.strategyType() == PolicyType.COMPOSITE) {
                throw BadRequestException.of(MessageCodes.VALIDATION_FAILED,
                        "strategyParams.components[%d].strategyType cannot be COMPOSITE (no recursion)"
                                .formatted(i));
            }
            if (comp.strategyType() == PolicyType.MANUAL_OVERRIDE) {
                throw BadRequestException.of(MessageCodes.VALIDATION_FAILED,
                        "strategyParams.components[%d].strategyType cannot be MANUAL_OVERRIDE"
                                .formatted(i));
            }
            if (comp.weight() == null
                    || comp.weight().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                throw BadRequestException.of(MessageCodes.VALIDATION_FAILED,
                        "strategyParams.components[%d].weight must be > 0".formatted(i));
            }
            if (comp.strategyParams() == null || comp.strategyParams().isBlank()) {
                throw BadRequestException.of(MessageCodes.VALIDATION_FAILED,
                        "strategyParams.components[%d].strategyParams is required".formatted(i));
            }
            validateStrategyParams(comp.strategyType(), comp.strategyParams());
        }
        if (params.roundingStep() != null
                && params.roundingStep().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw BadRequestException.of(MessageCodes.VALIDATION_FAILED,
                    "strategyParams.roundingStep must be > 0");
        }
    }

    private void validateCompetitorAnchorParams(CompetitorAnchorParams params) {
        if (params.positionFactor() != null
                && (params.positionFactor().compareTo(
                        new java.math.BigDecimal("0.50")) < 0
                || params.positionFactor().compareTo(
                        new java.math.BigDecimal("2.00")) > 0)) {
            throw BadRequestException.of(MessageCodes.VALIDATION_FAILED,
                    "strategyParams.positionFactor must be in [0.50, 2.00]");
        }
        if (params.minMarginPct() != null
                && (params.minMarginPct().compareTo(java.math.BigDecimal.ZERO) < 0
                || params.minMarginPct().compareTo(java.math.BigDecimal.ONE) >= 0)) {
            throw BadRequestException.of(MessageCodes.VALIDATION_FAILED,
                    "strategyParams.minMarginPct must be in [0, 1)");
        }
        if (params.roundingStep() != null
                && params.roundingStep()
                        .compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw BadRequestException.of(MessageCodes.VALIDATION_FAILED,
                    "strategyParams.roundingStep must be > 0");
        }
    }

    private void validateVelocityAdaptiveParams(VelocityAdaptiveParams params) {
        if (params.decelerationThreshold() != null
                && (params.decelerationThreshold()
                        .compareTo(java.math.BigDecimal.ZERO) <= 0
                || params.decelerationThreshold()
                        .compareTo(java.math.BigDecimal.ONE) >= 0)) {
            throw BadRequestException.of(MessageCodes.VALIDATION_FAILED,
                    "strategyParams.decelerationThreshold must be in (0, 1)");
        }
        if (params.accelerationThreshold() != null
                && (params.accelerationThreshold()
                        .compareTo(java.math.BigDecimal.ONE) <= 0
                || params.accelerationThreshold().compareTo(
                        new java.math.BigDecimal("5")) > 0)) {
            throw BadRequestException.of(MessageCodes.VALIDATION_FAILED,
                    "strategyParams.accelerationThreshold must be in (1, 5]");
        }
        if (params.decelerationDiscountPct() != null
                && (params.decelerationDiscountPct().compareTo(
                        new java.math.BigDecimal("0.01")) < 0
                || params.decelerationDiscountPct().compareTo(
                        new java.math.BigDecimal("0.30")) > 0)) {
            throw BadRequestException.of(MessageCodes.VALIDATION_FAILED,
                    "strategyParams.decelerationDiscountPct must be in [0.01, 0.30]");
        }
        if (params.accelerationMarkupPct() != null
                && (params.accelerationMarkupPct().compareTo(
                        new java.math.BigDecimal("0.01")) < 0
                || params.accelerationMarkupPct().compareTo(
                        new java.math.BigDecimal("0.20")) > 0)) {
            throw BadRequestException.of(MessageCodes.VALIDATION_FAILED,
                    "strategyParams.accelerationMarkupPct must be in [0.01, 0.20]");
        }
        if (params.minBaselineSales() != null
                && (params.minBaselineSales() < 1
                || params.minBaselineSales() > 1000)) {
            throw BadRequestException.of(MessageCodes.VALIDATION_FAILED,
                    "strategyParams.minBaselineSales must be in [1, 1000]");
        }
        if (params.velocityWindowShortDays() != null
                && (params.velocityWindowShortDays() < 3
                || params.velocityWindowShortDays() > 14)) {
            throw BadRequestException.of(MessageCodes.VALIDATION_FAILED,
                    "strategyParams.velocityWindowShortDays must be in [3, 14]");
        }
        if (params.velocityWindowLongDays() != null
                && (params.velocityWindowLongDays() < 14
                || params.velocityWindowLongDays() > 90)) {
            throw BadRequestException.of(MessageCodes.VALIDATION_FAILED,
                    "strategyParams.velocityWindowLongDays must be in [14, 90]");
        }
        if (params.roundingStep() != null
                && params.roundingStep()
                        .compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw BadRequestException.of(MessageCodes.VALIDATION_FAILED,
                    "strategyParams.roundingStep must be > 0");
        }
    }
}
