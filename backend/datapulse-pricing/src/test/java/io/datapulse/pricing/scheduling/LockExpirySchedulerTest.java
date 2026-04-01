package io.datapulse.pricing.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.datapulse.pricing.persistence.ManualPriceLockEntity;
import io.datapulse.pricing.persistence.ManualPriceLockRepository;

@ExtendWith(MockitoExtension.class)
class LockExpirySchedulerTest {

  @Mock private ManualPriceLockRepository lockRepository;

  @InjectMocks
  private LockExpiryScheduler scheduler;

  @Captor
  private ArgumentCaptor<List<ManualPriceLockEntity>> locksCaptor;

  @Test
  @DisplayName("does nothing when no expired locks")
  void should_noOp_when_noExpiredLocks() {
    when(lockRepository.findExpiredLocks()).thenReturn(List.of());

    scheduler.expireLocks();

    verify(lockRepository, never()).saveAll(locksCaptor.capture());
  }

  @Test
  @DisplayName("marks expired locks as unlocked")
  void should_markUnlocked_when_locksExpired() {
    ManualPriceLockEntity lock1 = new ManualPriceLockEntity();
    lock1.setId(1L);
    ManualPriceLockEntity lock2 = new ManualPriceLockEntity();
    lock2.setId(2L);

    when(lockRepository.findExpiredLocks()).thenReturn(List.of(lock1, lock2));

    scheduler.expireLocks();

    verify(lockRepository).saveAll(locksCaptor.capture());
    List<ManualPriceLockEntity> saved = locksCaptor.getValue();
    assertThat(saved).hasSize(2);
    assertThat(saved).allSatisfy(lock -> {
      assertThat(lock.getUnlockedAt()).isNotNull();
      assertThat(lock.getUnlockedBy()).isNull();
    });
  }

  @Test
  @DisplayName("catches exception without propagating")
  void should_catchException_when_repositoryFails() {
    when(lockRepository.findExpiredLocks()).thenThrow(new RuntimeException("DB error"));

    scheduler.expireLocks();

    verify(lockRepository, never()).saveAll(locksCaptor.capture());
  }
}
