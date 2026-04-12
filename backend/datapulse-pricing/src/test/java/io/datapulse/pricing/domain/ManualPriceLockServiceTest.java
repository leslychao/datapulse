package io.datapulse.pricing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.pricing.api.CreateManualLockRequest;
import io.datapulse.pricing.api.ManualLockMapper;
import io.datapulse.pricing.api.ManualLockResponse;
import io.datapulse.pricing.persistence.ManualPriceLockEntity;
import io.datapulse.pricing.persistence.ManualPriceLockRepository;
import io.datapulse.pricing.persistence.PricingRunReadRepository;

@ExtendWith(MockitoExtension.class)
class ManualPriceLockServiceTest {

  @Mock private ManualPriceLockRepository lockRepository;
  @Mock private ManualLockMapper lockMapper;
  @Mock private PricingRunReadRepository runReadRepository;

  @InjectMocks
  private ManualPriceLockService service;

  @Captor
  private ArgumentCaptor<ManualPriceLockEntity> entityCaptor;

  private static final long WORKSPACE_ID = 10L;
  private static final long USER_ID = 1L;

  @Nested
  @DisplayName("createLock")
  class CreateLock {

    @Test
    @DisplayName("creates lock when offer is not already locked")
    void should_createLock_when_offerNotLocked() {
      CreateManualLockRequest request = new CreateManualLockRequest(
          100L, new BigDecimal("999"), "Testing", null);

      when(lockRepository.isLocked(100L)).thenReturn(false);
      when(lockRepository.save(any())).thenAnswer(i -> {
        ManualPriceLockEntity e = i.getArgument(0);
        e.setId(1L);
        return e;
      });
      when(lockMapper.toResponse(any())).thenReturn(null);

      service.createLock(request, WORKSPACE_ID, USER_ID);

      verify(lockRepository).save(entityCaptor.capture());
      ManualPriceLockEntity saved = entityCaptor.getValue();
      assertThat(saved.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
      assertThat(saved.getMarketplaceOfferId()).isEqualTo(100L);
      assertThat(saved.getLockedPrice()).isEqualByComparingTo(new BigDecimal("999"));
      assertThat(saved.getLockedBy()).isEqualTo(USER_ID);
      assertThat(saved.getReason()).isEqualTo("Testing");
    }

    @Test
    @DisplayName("throws when offer is already locked")
    void should_throw_when_alreadyLocked() {
      CreateManualLockRequest request = new CreateManualLockRequest(
          100L, new BigDecimal("999"), null, null);

      when(lockRepository.isLocked(100L)).thenReturn(true);

      assertThatThrownBy(() -> service.createLock(request, WORKSPACE_ID, USER_ID))
          .isInstanceOf(BadRequestException.class);
    }
  }

  @Nested
  @DisplayName("unlock")
  class Unlock {

    @Test
    @DisplayName("unlocks active lock")
    void should_unlock_when_lockActive() {
      ManualPriceLockEntity entity = buildLockEntity(null);
      when(lockRepository.findById(1L)).thenReturn(Optional.of(entity));
      when(lockRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      service.unlock(1L, WORKSPACE_ID, USER_ID);

      assertThat(entity.getUnlockedAt()).isNotNull();
      assertThat(entity.getUnlockedBy()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("no-op when lock is already unlocked")
    void should_doNothing_when_alreadyUnlocked() {
      ManualPriceLockEntity entity = buildLockEntity(OffsetDateTime.now().minusHours(1));
      when(lockRepository.findById(1L)).thenReturn(Optional.of(entity));

      service.unlock(1L, WORKSPACE_ID, USER_ID);

      verify(lockRepository, never()).save(any());
    }

    @Test
    @DisplayName("throws when lock not found")
    void should_throwNotFound_when_lockNotFound() {
      when(lockRepository.findById(99L)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.unlock(99L, WORKSPACE_ID, USER_ID))
          .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("throws when workspace does not match")
    void should_throwNotFound_when_workspaceMismatch() {
      ManualPriceLockEntity entity = buildLockEntity(null);
      entity.setWorkspaceId(999L);
      when(lockRepository.findById(1L)).thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> service.unlock(1L, WORKSPACE_ID, USER_ID))
          .isInstanceOf(NotFoundException.class);
    }
  }

  @Nested
  @DisplayName("listActiveLocks")
  class ListActiveLocks {

    @Test
    @DisplayName("returns single lock when filtered by offer ID")
    void should_returnSingleLock_when_filteredByOfferId() {
      ManualPriceLockEntity entity = buildLockEntity(null);
      when(lockRepository.findActiveLock(100L)).thenReturn(Optional.of(entity));
      ManualLockResponse response = new ManualLockResponse(
          1L, 100L, new BigDecimal("999"), "Testing", USER_ID,
          OffsetDateTime.now(), null, null, null, null, null,
          null, null, null);
      when(lockMapper.toResponse(entity)).thenReturn(response);
      when(runReadRepository.findOfferInfo(any())).thenReturn(Collections.emptyMap());
      when(runReadRepository.findUserNames(any())).thenReturn(Collections.emptyMap());
      when(runReadRepository.findConnectionNames(any())).thenReturn(Collections.emptyMap());

      var result = service.listActiveLocks(WORKSPACE_ID, 100L,
          null, null, PageRequest.of(0, 10));

      assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("returns empty list when no active lock for offer")
    void should_returnEmpty_when_noActiveLockForOffer() {
      when(lockRepository.findActiveLock(100L)).thenReturn(Optional.empty());

      var result = service.listActiveLocks(WORKSPACE_ID, 100L,
          null, null, PageRequest.of(0, 10));

      assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("returns all workspace locks when offer ID is null")
    void should_returnAllLocks_when_offerIdNull() {
      when(lockRepository.findAllByWorkspaceIdAndUnlockedAtIsNull(eq(WORKSPACE_ID), any(Pageable.class)))
          .thenReturn(new PageImpl<>(List.of()));

      service.listActiveLocks(WORKSPACE_ID, null,
          null, null, PageRequest.of(0, 20));

      verify(lockRepository).findAllByWorkspaceIdAndUnlockedAtIsNull(eq(WORKSPACE_ID), any(Pageable.class));
    }
  }

  private ManualPriceLockEntity buildLockEntity(OffsetDateTime unlockedAt) {
    var entity = new ManualPriceLockEntity();
    entity.setId(1L);
    entity.setWorkspaceId(WORKSPACE_ID);
    entity.setMarketplaceOfferId(100L);
    entity.setLockedPrice(new BigDecimal("999"));
    entity.setLockedBy(USER_ID);
    entity.setLockedAt(OffsetDateTime.now());
    entity.setUnlockedAt(unlockedAt);
    return entity;
  }
}
