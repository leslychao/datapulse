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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import io.datapulse.common.exception.NotFoundException;
import io.datapulse.pricing.api.PriceDecisionFilter;
import io.datapulse.pricing.api.PriceDecisionMapper;
import io.datapulse.pricing.api.PriceDecisionResponse;
import io.datapulse.pricing.persistence.PriceDecisionEntity;
import io.datapulse.pricing.persistence.PriceDecisionReadRepository;
import io.datapulse.pricing.persistence.PriceDecisionRepository;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class PriceDecisionServiceTest {

  @Mock private PriceDecisionRepository decisionRepository;
  @Mock private PriceDecisionReadRepository decisionReadRepository;
  @Mock private PriceDecisionMapper decisionMapper;

  @InjectMocks
  private PriceDecisionService service;

  private static final long WORKSPACE_ID = 10L;

  @Nested
  @DisplayName("getDecision")
  class GetDecision {

    @Test
    @DisplayName("returns response when decision found")
    void should_returnResponse_when_decisionExists() {
      PriceDecisionEntity entity = new PriceDecisionEntity();
      entity.setId(1L);
      when(decisionRepository.findByIdAndWorkspaceId(1L, WORKSPACE_ID))
          .thenReturn(Optional.of(entity));
      when(decisionMapper.toResponse(entity)).thenReturn(null);

      service.getDecision(1L, WORKSPACE_ID);

      verify(decisionMapper).toResponse(entity);
    }

    @Test
    @DisplayName("throws NotFoundException when decision not found")
    void should_throwNotFound_when_decisionNotFound() {
      when(decisionRepository.findByIdAndWorkspaceId(99L, WORKSPACE_ID))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.getDecision(99L, WORKSPACE_ID))
          .isInstanceOf(NotFoundException.class);
    }
  }

  @Nested
  @DisplayName("listDecisions")
  class ListDecisions {

    @Test
    @DisplayName("delegates to read repository and maps results")
    void should_delegateToReadRepo_when_listing() {
      Pageable pageable = PageRequest.of(0, 20);
      PriceDecisionFilter filter = new PriceDecisionFilter(null, null, null, null, null, null);
      PriceDecisionEntity entity = new PriceDecisionEntity();
      Page<PriceDecisionEntity> page = new PageImpl<>(List.of(entity));

      when(decisionReadRepository.findByFilter(WORKSPACE_ID, filter, pageable))
          .thenReturn(page);
      when(decisionMapper.toResponse(entity)).thenReturn(null);

      service.listDecisions(WORKSPACE_ID, filter, pageable);

      verify(decisionReadRepository).findByFilter(WORKSPACE_ID, filter, pageable);
      verify(decisionMapper).toResponse(entity);
    }
  }
}
