package io.datapulse.execution.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.datapulse.execution.persistence.SimulatedOfferStateRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimulationService")
class SimulationServiceTest {

  @Mock private SimulatedOfferStateRepository simulatedStateRepository;

  @InjectMocks
  private SimulationService service;

  @Nested
  @DisplayName("resetShadowState")
  class ResetShadowState {

    @Test
    @DisplayName("should delete shadow state and return count")
    void should_returnDeletedCount_when_stateExists() {
      when(simulatedStateRepository.deleteByConnection(10L, 5L)).thenReturn(42);

      int result = service.resetShadowState(10L, 5L);

      assertThat(result).isEqualTo(42);
      verify(simulatedStateRepository).deleteByConnection(10L, 5L);
    }

    @Test
    @DisplayName("should return 0 when no shadow state exists")
    void should_returnZero_when_noState() {
      when(simulatedStateRepository.deleteByConnection(10L, 5L)).thenReturn(0);

      int result = service.resetShadowState(10L, 5L);

      assertThat(result).isZero();
    }
  }
}
