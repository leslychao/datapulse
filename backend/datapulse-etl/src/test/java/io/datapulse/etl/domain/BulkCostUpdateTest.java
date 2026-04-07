package io.datapulse.etl.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
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

import io.datapulse.etl.api.BulkFormulaCostRequest;
import io.datapulse.etl.api.BulkFormulaCostResponse;
import io.datapulse.etl.persistence.canonical.CostProfileEntity;
import io.datapulse.etl.persistence.canonical.CostProfileRepository;
import io.datapulse.etl.persistence.canonical.SellerSkuReadRepository;

/**
 * Tests bulk cost formula (SCD2 versioning) logic in CostProfileService.
 * Verifies formula calculations, version creation, and skip conditions.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Bulk cost update — formula SCD2 logic")
class BulkCostUpdateTest {

  @Mock private CostProfileRepository costProfileRepository;
  @Mock private SellerSkuReadRepository sellerSkuReadRepository;

  @InjectMocks
  private CostProfileService service;

  @Captor
  private ArgumentCaptor<CostProfileEntity> entityCaptor;

  private static final long WORKSPACE_ID = 10L;
  private static final long USER_ID = 1L;
  private static final LocalDate VALID_FROM = LocalDate.of(2026, 4, 1);

  @Nested
  @DisplayName("INCREASE_PCT formula")
  class IncreasePct {

    @Test
    @DisplayName("should_createNewVersionWithIncreasedCost_when_existingProfileFound")
    void should_createNewVersionWithIncreasedCost_when_existingProfileFound() {
      long skuId = 100L;
      CostProfileEntity existing = costProfile(skuId, new BigDecimal("1000.00"));

      when(costProfileRepository.findCurrentBySkuAndWorkspace(skuId, WORKSPACE_ID))
          .thenReturn(Optional.of(existing));

      BulkFormulaCostRequest request = new BulkFormulaCostRequest(
          List.of(skuId), CostUpdateOperation.INCREASE_PCT,
          new BigDecimal("10"), VALID_FROM);

      BulkFormulaCostResponse response = service.bulkFormula(request, WORKSPACE_ID, USER_ID);

      assertThat(response.updated()).isEqualTo(1);
      assertThat(response.skipped()).isZero();

      verify(costProfileRepository).createVersion(entityCaptor.capture());
      CostProfileEntity created = entityCaptor.getValue();
      assertThat(created.getSellerSkuId()).isEqualTo(skuId);
      assertThat(created.getCostPrice()).isEqualByComparingTo(new BigDecimal("1100.00"));
      assertThat(created.getValidFrom()).isEqualTo(VALID_FROM);
      assertThat(created.getCurrency()).isEqualTo("RUB");
    }

    @Test
    @DisplayName("should_skipSku_when_noExistingProfileForIncreasePct")
    void should_skipSku_when_noExistingProfileForIncreasePct() {
      long skuId = 200L;
      when(costProfileRepository.findCurrentBySkuAndWorkspace(skuId, WORKSPACE_ID))
          .thenReturn(Optional.empty());

      BulkFormulaCostRequest request = new BulkFormulaCostRequest(
          List.of(skuId), CostUpdateOperation.INCREASE_PCT,
          new BigDecimal("10"), VALID_FROM);

      BulkFormulaCostResponse response = service.bulkFormula(request, WORKSPACE_ID, USER_ID);

      assertThat(response.updated()).isZero();
      assertThat(response.skipped()).isEqualTo(1);
      verify(costProfileRepository, never()).createVersion(any());
    }

    @Test
    @DisplayName("should_applyCorrectRounding_when_increasePctProducesFraction")
    void should_applyCorrectRounding_when_increasePctProducesFraction() {
      long skuId = 100L;
      CostProfileEntity existing = costProfile(skuId, new BigDecimal("333.33"));

      when(costProfileRepository.findCurrentBySkuAndWorkspace(skuId, WORKSPACE_ID))
          .thenReturn(Optional.of(existing));

      BulkFormulaCostRequest request = new BulkFormulaCostRequest(
          List.of(skuId), CostUpdateOperation.INCREASE_PCT,
          new BigDecimal("15"), VALID_FROM);

      service.bulkFormula(request, WORKSPACE_ID, USER_ID);

      verify(costProfileRepository).createVersion(entityCaptor.capture());
      BigDecimal expected = new BigDecimal("333.33")
          .multiply(BigDecimal.ONE.add(
              new BigDecimal("15").divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)))
          .setScale(2, RoundingMode.HALF_UP);
      assertThat(entityCaptor.getValue().getCostPrice()).isEqualByComparingTo(expected);
    }
  }

  @Nested
  @DisplayName("DECREASE_PCT formula")
  class DecreasePct {

    @Test
    @DisplayName("should_createNewVersionWithDecreasedCost_when_existingProfileFound")
    void should_createNewVersionWithDecreasedCost_when_existingProfileFound() {
      long skuId = 100L;
      CostProfileEntity existing = costProfile(skuId, new BigDecimal("1000.00"));

      when(costProfileRepository.findCurrentBySkuAndWorkspace(skuId, WORKSPACE_ID))
          .thenReturn(Optional.of(existing));

      BulkFormulaCostRequest request = new BulkFormulaCostRequest(
          List.of(skuId), CostUpdateOperation.DECREASE_PCT,
          new BigDecimal("20"), VALID_FROM);

      BulkFormulaCostResponse response = service.bulkFormula(request, WORKSPACE_ID, USER_ID);

      assertThat(response.updated()).isEqualTo(1);
      verify(costProfileRepository).createVersion(entityCaptor.capture());
      assertThat(entityCaptor.getValue().getCostPrice())
          .isEqualByComparingTo(new BigDecimal("800.00"));
    }

    @Test
    @DisplayName("should_skipSku_when_noExistingProfileForDecreasePct")
    void should_skipSku_when_noExistingProfileForDecreasePct() {
      long skuId = 200L;
      when(costProfileRepository.findCurrentBySkuAndWorkspace(skuId, WORKSPACE_ID))
          .thenReturn(Optional.empty());

      BulkFormulaCostRequest request = new BulkFormulaCostRequest(
          List.of(skuId), CostUpdateOperation.DECREASE_PCT,
          new BigDecimal("20"), VALID_FROM);

      BulkFormulaCostResponse response = service.bulkFormula(request, WORKSPACE_ID, USER_ID);

      assertThat(response.skipped()).isEqualTo(1);
      verify(costProfileRepository, never()).createVersion(any());
    }
  }

  @Nested
  @DisplayName("MULTIPLY formula")
  class Multiply {

    @Test
    @DisplayName("should_createNewVersionWithMultipliedCost_when_existingProfileFound")
    void should_createNewVersionWithMultipliedCost_when_existingProfileFound() {
      long skuId = 100L;
      CostProfileEntity existing = costProfile(skuId, new BigDecimal("500.00"));

      when(costProfileRepository.findCurrentBySkuAndWorkspace(skuId, WORKSPACE_ID))
          .thenReturn(Optional.of(existing));

      BulkFormulaCostRequest request = new BulkFormulaCostRequest(
          List.of(skuId), CostUpdateOperation.MULTIPLY,
          new BigDecimal("1.25"), VALID_FROM);

      BulkFormulaCostResponse response = service.bulkFormula(request, WORKSPACE_ID, USER_ID);

      assertThat(response.updated()).isEqualTo(1);
      verify(costProfileRepository).createVersion(entityCaptor.capture());
      assertThat(entityCaptor.getValue().getCostPrice())
          .isEqualByComparingTo(new BigDecimal("625.00"));
    }

    @Test
    @DisplayName("should_skipSku_when_noExistingProfileForMultiply")
    void should_skipSku_when_noExistingProfileForMultiply() {
      long skuId = 200L;
      when(costProfileRepository.findCurrentBySkuAndWorkspace(skuId, WORKSPACE_ID))
          .thenReturn(Optional.empty());

      BulkFormulaCostRequest request = new BulkFormulaCostRequest(
          List.of(skuId), CostUpdateOperation.MULTIPLY,
          new BigDecimal("1.25"), VALID_FROM);

      BulkFormulaCostResponse response = service.bulkFormula(request, WORKSPACE_ID, USER_ID);

      assertThat(response.skipped()).isEqualTo(1);
      verify(costProfileRepository, never()).createVersion(any());
    }
  }

  @Nested
  @DisplayName("FIXED formula")
  class Fixed {

    @Test
    @DisplayName("should_createNewVersion_when_fixedWithExistingProfile")
    void should_createNewVersion_when_fixedWithExistingProfile() {
      long skuId = 100L;
      CostProfileEntity existing = costProfile(skuId, new BigDecimal("500.00"));

      when(costProfileRepository.findCurrentBySkuAndWorkspace(skuId, WORKSPACE_ID))
          .thenReturn(Optional.of(existing));

      BulkFormulaCostRequest request = new BulkFormulaCostRequest(
          List.of(skuId), CostUpdateOperation.FIXED,
          new BigDecimal("650.00"), VALID_FROM);

      BulkFormulaCostResponse response = service.bulkFormula(request, WORKSPACE_ID, USER_ID);

      assertThat(response.updated()).isEqualTo(1);
      verify(costProfileRepository).createVersion(entityCaptor.capture());
      assertThat(entityCaptor.getValue().getCostPrice())
          .isEqualByComparingTo(new BigDecimal("650.00"));
    }

    @Test
    @DisplayName("should_createNewVersion_when_fixedWithoutExistingProfile")
    void should_createNewVersion_when_fixedWithoutExistingProfile() {
      long skuId = 200L;
      when(costProfileRepository.findCurrentBySkuAndWorkspace(skuId, WORKSPACE_ID))
          .thenReturn(Optional.empty());

      BulkFormulaCostRequest request = new BulkFormulaCostRequest(
          List.of(skuId), CostUpdateOperation.FIXED,
          new BigDecimal("650.00"), VALID_FROM);

      BulkFormulaCostResponse response = service.bulkFormula(request, WORKSPACE_ID, USER_ID);

      assertThat(response.updated()).isEqualTo(1);
      assertThat(response.skipped()).isZero();
      verify(costProfileRepository).createVersion(entityCaptor.capture());
      CostProfileEntity created = entityCaptor.getValue();
      assertThat(created.getSellerSkuId()).isEqualTo(skuId);
      assertThat(created.getCostPrice()).isEqualByComparingTo(new BigDecimal("650.00"));
      assertThat(created.getValidFrom()).isEqualTo(VALID_FROM);
    }
  }

  @Nested
  @DisplayName("Mixed batch scenarios")
  class MixedBatch {

    @Test
    @DisplayName("should_processPartially_when_mixOfExistingAndMissingProfiles")
    void should_processPartially_when_mixOfExistingAndMissingProfiles() {
      long skuWithProfile = 100L;
      long skuWithoutProfile = 200L;

      when(costProfileRepository.findCurrentBySkuAndWorkspace(skuWithProfile, WORKSPACE_ID))
          .thenReturn(Optional.of(costProfile(skuWithProfile, new BigDecimal("1000.00"))));
      when(costProfileRepository.findCurrentBySkuAndWorkspace(skuWithoutProfile, WORKSPACE_ID))
          .thenReturn(Optional.empty());

      BulkFormulaCostRequest request = new BulkFormulaCostRequest(
          List.of(skuWithProfile, skuWithoutProfile),
          CostUpdateOperation.INCREASE_PCT, new BigDecimal("10"), VALID_FROM);

      BulkFormulaCostResponse response = service.bulkFormula(request, WORKSPACE_ID, USER_ID);

      assertThat(response.updated()).isEqualTo(1);
      assertThat(response.skipped()).isEqualTo(1);
      verify(costProfileRepository, times(1)).createVersion(any());
    }

    @Test
    @DisplayName("should_callCreateVersionForEachSku_when_scd2Versioning")
    void should_callCreateVersionForEachSku_when_scd2Versioning() {
      long sku1 = 100L;
      long sku2 = 200L;
      long sku3 = 300L;

      when(costProfileRepository.findCurrentBySkuAndWorkspace(eq(sku1), eq(WORKSPACE_ID)))
          .thenReturn(Optional.of(costProfile(sku1, new BigDecimal("100"))));
      when(costProfileRepository.findCurrentBySkuAndWorkspace(eq(sku2), eq(WORKSPACE_ID)))
          .thenReturn(Optional.of(costProfile(sku2, new BigDecimal("200"))));
      when(costProfileRepository.findCurrentBySkuAndWorkspace(eq(sku3), eq(WORKSPACE_ID)))
          .thenReturn(Optional.of(costProfile(sku3, new BigDecimal("300"))));

      BulkFormulaCostRequest request = new BulkFormulaCostRequest(
          List.of(sku1, sku2, sku3),
          CostUpdateOperation.INCREASE_PCT, new BigDecimal("10"), VALID_FROM);

      BulkFormulaCostResponse response = service.bulkFormula(request, WORKSPACE_ID, USER_ID);

      assertThat(response.updated()).isEqualTo(3);
      verify(costProfileRepository, times(3)).createVersion(any());
    }

    @Test
    @DisplayName("should_countErrors_when_repositoryThrows")
    void should_countErrors_when_repositoryThrows() {
      long skuOk = 100L;
      long skuFail = 200L;

      when(costProfileRepository.findCurrentBySkuAndWorkspace(skuOk, WORKSPACE_ID))
          .thenReturn(Optional.of(costProfile(skuOk, new BigDecimal("1000"))));
      when(costProfileRepository.findCurrentBySkuAndWorkspace(skuFail, WORKSPACE_ID))
          .thenThrow(new RuntimeException("DB error"));

      BulkFormulaCostRequest request = new BulkFormulaCostRequest(
          List.of(skuOk, skuFail),
          CostUpdateOperation.INCREASE_PCT, new BigDecimal("10"), VALID_FROM);

      BulkFormulaCostResponse response = service.bulkFormula(request, WORKSPACE_ID, USER_ID);

      assertThat(response.updated()).isEqualTo(1);
      assertThat(response.errors()).hasSize(1);
    }
  }

  private CostProfileEntity costProfile(long sellerSkuId, BigDecimal costPrice) {
    var entity = new CostProfileEntity();
    entity.setId(sellerSkuId);
    entity.setSellerSkuId(sellerSkuId);
    entity.setCostPrice(costPrice);
    entity.setCurrency("RUB");
    entity.setValidFrom(VALID_FROM.minusDays(30));
    entity.setUpdatedByUserId(USER_ID);
    return entity;
  }
}
