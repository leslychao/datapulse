package io.datapulse.test.persistence;

import static io.datapulse.test.builder.TestPriceActionBuilder.aPriceAction;
import static io.datapulse.test.builder.TestTenantBuilder.aTenant;
import static io.datapulse.test.builder.TestUserBuilder.aUser;
import static io.datapulse.test.builder.TestWorkspaceBuilder.aWorkspace;
import static org.assertj.core.api.Assertions.assertThat;

import io.datapulse.execution.domain.ActionExecutionMode;
import io.datapulse.execution.domain.ActionReconciliationSource;
import io.datapulse.execution.domain.ActionStatus;
import io.datapulse.execution.persistence.PriceActionCasRepository;
import io.datapulse.execution.persistence.PriceActionEntity;
import io.datapulse.execution.persistence.PriceActionRepository;
import io.datapulse.tenancy.persistence.AppUserRepository;
import io.datapulse.tenancy.persistence.TenantRepository;
import io.datapulse.tenancy.persistence.WorkspaceRepository;
import io.datapulse.test.AbstractIntegrationTest;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PriceActionRepositoryIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private PriceActionRepository actionRepository;

  @Autowired
  private PriceActionCasRepository casRepository;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private WorkspaceRepository workspaceRepository;

  @Autowired
  private AppUserRepository appUserRepository;

  private Long workspaceId;

  @BeforeEach
  void setUp() {
    var user = appUserRepository.save(aUser().build());
    var tenant = tenantRepository.save(aTenant().withOwnerUserId(user.getId()).build());
    var ws = workspaceRepository.save(
        aWorkspace().withTenant(tenant).withOwnerUserId(user.getId()).build());
    workspaceId = ws.getId();
  }

  @Nested
  @DisplayName("save and findActiveByOfferAndMode")
  class FindActive {

    @Test
    void should_findActive_when_notTerminal() {
      var action = saveAction(ActionStatus.PENDING_APPROVAL);

      var found = actionRepository.findActiveByOfferAndMode(
          action.getMarketplaceOfferId(), ActionExecutionMode.LIVE);

      assertThat(found).isPresent();
      assertThat(found.get().getId()).isEqualTo(action.getId());
    }

    @Test
    void should_returnEmpty_when_allTerminal() {
      var action = saveAction(ActionStatus.SUCCEEDED);

      var found = actionRepository.findActiveByOfferAndMode(
          action.getMarketplaceOfferId(), ActionExecutionMode.LIVE);

      assertThat(found).isEmpty();
    }
  }

  @Nested
  @DisplayName("CAS operations")
  class CasOperations {

    @Test
    void should_casTransition_when_statusMatches() {
      var action = saveAction(ActionStatus.PENDING_APPROVAL);

      int updated = casRepository.casTransition(
          action.getId(), ActionStatus.PENDING_APPROVAL, ActionStatus.APPROVED);

      assertThat(updated).isEqualTo(1);
    }

    @Test
    void should_failCasTransition_when_statusDoesNotMatch() {
      var action = saveAction(ActionStatus.PENDING_APPROVAL);

      int updated = casRepository.casTransition(
          action.getId(), ActionStatus.EXECUTING, ActionStatus.SUCCEEDED);

      assertThat(updated).isEqualTo(0);
    }

    @Test
    void should_casApprove_withUserId() {
      var action = saveAction(ActionStatus.PENDING_APPROVAL);

      int updated = casRepository.casApprove(
          action.getId(), ActionStatus.PENDING_APPROVAL, 42L);

      assertThat(updated).isEqualTo(1);

      var refreshed = actionRepository.findById(action.getId()).orElseThrow();
      assertThat(refreshed.getStatus()).isEqualTo(ActionStatus.APPROVED);
      assertThat(refreshed.getApprovedByUserId()).isEqualTo(42L);
      assertThat(refreshed.getApprovedAt()).isNotNull();
    }

    @Test
    void should_casHold_withReason() {
      var action = saveAction(ActionStatus.APPROVED);

      int updated = casRepository.casHold(action.getId(), "Price freeze");

      assertThat(updated).isEqualTo(1);
    }

    @Test
    void should_casCancel_withReason() {
      var action = saveAction(ActionStatus.PENDING_APPROVAL);

      int updated = casRepository.casCancel(
          action.getId(), ActionStatus.PENDING_APPROVAL, "User cancelled");

      assertThat(updated).isEqualTo(1);
    }

    @Test
    void should_casRetryScheduled() {
      var action = saveAction(ActionStatus.EXECUTING);

      int updated = casRepository.casRetryScheduled(
          action.getId(), 2, OffsetDateTime.now().plusMinutes(5));

      assertThat(updated).isEqualTo(1);
    }

    @Test
    void should_casSucceed() {
      var action = saveAction(ActionStatus.RECONCILIATION_PENDING);

      int updated = casRepository.casSucceed(
          action.getId(), ActionStatus.RECONCILIATION_PENDING,
          ActionReconciliationSource.AUTO, null);

      assertThat(updated).isEqualTo(1);
    }

    @Test
    void should_casFail() {
      var action = saveAction(ActionStatus.EXECUTING);

      int updated = casRepository.casFail(
          action.getId(), ActionStatus.EXECUTING, 3);

      assertThat(updated).isEqualTo(1);
    }

    @Test
    void should_casIncrementAttempt() {
      var action = saveAction(ActionStatus.EXECUTING);

      int updated = casRepository.casIncrementAttempt(action.getId());

      assertThat(updated).isEqualTo(1);
    }
  }

  private PriceActionEntity saveAction(ActionStatus status) {
    return actionRepository.save(
        aPriceAction()
            .withWorkspaceId(workspaceId)
            .withMarketplaceOfferId(1L)
            .withPriceDecisionId(1L)
            .withStatus(status)
            .build());
  }
}
