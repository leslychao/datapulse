package io.datapulse.pricing.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import io.datapulse.common.exception.BadRequestException;
import io.datapulse.platform.outbox.OutboxEventType;
import io.datapulse.platform.outbox.OutboxService;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.pricing.api.PricingRunFilter;
import io.datapulse.pricing.api.PricingRunMapper;
import io.datapulse.pricing.api.PricingRunResponse;
import io.datapulse.pricing.persistence.PricingRunEntity;
import io.datapulse.pricing.persistence.PricingRunReadRepository;
import io.datapulse.pricing.persistence.PricingRunRepository;

@ExtendWith(MockitoExtension.class)
class PricingRunApiServiceTest {

  @Mock private PricingRunRepository runRepository;
  @Mock private PricingRunReadRepository runReadRepository;
  @Mock private PricingRunMapper runMapper;
  @Mock private OutboxService outboxService;

  @InjectMocks
  private PricingRunApiService service;

  @Captor
  private ArgumentCaptor<PricingRunEntity> entityCaptor;

  private static final long WORKSPACE_ID = 10L;
  private static final long CONNECTION_ID = 20L;

  @Nested
  @DisplayName("triggerManualRun")
  class TriggerManualRun {

    @Test
    @DisplayName("creates PENDING run when no run in progress")
    void should_createPendingRun_when_noRunInProgress() {
      when(runRepository.existsByConnectionIdAndStatus(CONNECTION_ID, RunStatus.IN_PROGRESS))
          .thenReturn(false);
      when(runRepository.save(any())).thenAnswer(i -> {
        PricingRunEntity e = i.getArgument(0);
        e.setId(1L);
        return e;
      });
      when(runMapper.toResponse(any())).thenReturn(null);

      service.triggerManualRun(CONNECTION_ID, WORKSPACE_ID);

      verify(runRepository).save(entityCaptor.capture());
      PricingRunEntity saved = entityCaptor.getValue();
      assertThat(saved.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
      assertThat(saved.getConnectionId()).isEqualTo(CONNECTION_ID);
      assertThat(saved.getStatus()).isEqualTo(RunStatus.PENDING);
      assertThat(saved.getTriggerType()).isEqualTo(RunTriggerType.MANUAL);

      verify(outboxService)
          .createEvent(
              eq(OutboxEventType.PRICING_RUN_EXECUTE),
              eq("pricing_run"),
              eq(1L),
              eq(Map.of("runId", 1L)));
    }

    @Test
    @DisplayName("throws BadRequest when run already in progress")
    void should_throwBadRequest_when_runInProgress() {
      when(runRepository.existsByConnectionIdAndStatus(CONNECTION_ID, RunStatus.IN_PROGRESS))
          .thenReturn(true);

      assertThatThrownBy(() -> service.triggerManualRun(CONNECTION_ID, WORKSPACE_ID))
          .isInstanceOf(BadRequestException.class);
    }
  }

  @Nested
  @DisplayName("getRun")
  class GetRun {

    @Test
    @DisplayName("returns response when run exists")
    void should_returnResponse_when_runExists() {
      var entity = new PricingRunEntity();
      entity.setId(1L);
      when(runRepository.findByIdAndWorkspaceId(1L, WORKSPACE_ID))
          .thenReturn(Optional.of(entity));
      when(runMapper.toResponse(entity)).thenReturn(null);

      service.getRun(1L, WORKSPACE_ID);

      verify(runMapper).toResponse(entity);
    }

    @Test
    @DisplayName("throws NotFoundException when run does not exist")
    void should_throwNotFound_when_runNotFound() {
      when(runRepository.findByIdAndWorkspaceId(99L, WORKSPACE_ID))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.getRun(99L, WORKSPACE_ID))
          .isInstanceOf(NotFoundException.class);
    }
  }

  @Nested
  @DisplayName("listRuns")
  class ListRuns {

    @Test
    @DisplayName("delegates to read repo and maps results")
    void should_delegateToReadRepo_when_listing() {
      PricingRunFilter filter = new PricingRunFilter(null, null, null, null);
      Pageable pageable = PageRequest.of(0, 20);
      Page<PricingRunEntity> page = new PageImpl<>(List.of(new PricingRunEntity()));

      when(runReadRepository.findByFilter(WORKSPACE_ID, filter, pageable)).thenReturn(page);
      when(runMapper.toResponse(any(PricingRunEntity.class))).thenReturn(null);

      service.listRuns(WORKSPACE_ID, filter, pageable);

      verify(runReadRepository).findByFilter(WORKSPACE_ID, filter, pageable);
    }
  }
}
