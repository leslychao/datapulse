package io.datapulse.analytics.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import io.datapulse.analytics.api.ProvenanceEntryResponse;
import io.datapulse.analytics.api.ProvenanceRawResponse;
import io.datapulse.analytics.persistence.ProvenanceRepository;
import io.datapulse.analytics.persistence.ProvenanceRepository.RawFileInfo;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.platform.storage.RawStorageUrlProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProvenanceService")
class ProvenanceServiceTest {

  @Mock private ProvenanceRepository provenanceRepository;
  @Mock private RawStorageUrlProvider rawStorageUrlProvider;

  @InjectMocks
  private ProvenanceService service;

  private static final long ENTRY_ID = 42L;
  private static final long WORKSPACE_ID = 1L;

  private ProvenanceEntryResponse sampleEntry(Long jobExecutionId) {
    return new ProvenanceEntryResponse(
        ENTRY_ID, 10L, "WB", "EXT-001", "SALE_ACCRUAL",
        "P-001", "O-001", 1L,
        new BigDecimal("5000.00"), new BigDecimal("-750.00"),
        new BigDecimal("-150.00"), new BigDecimal("-400.00"),
        new BigDecimal("-100.00"), new BigDecimal("-25.00"),
        new BigDecimal("-50.00"), BigDecimal.ZERO,
        new BigDecimal("-30.00"), BigDecimal.ZERO,
        BigDecimal.ZERO, new BigDecimal("3495.00"),
        OffsetDateTime.now(), jobExecutionId);
  }

  @Nested
  @DisplayName("getCanonicalEntry")
  class GetCanonicalEntry {

    @Test
    @DisplayName("should return entry when found")
    void should_returnEntry_when_entryExists() {
      var entry = sampleEntry(100L);
      when(provenanceRepository.findCanonicalEntry(ENTRY_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(entry));

      ProvenanceEntryResponse result = service.getCanonicalEntry(ENTRY_ID, WORKSPACE_ID);

      assertThat(result.id()).isEqualTo(ENTRY_ID);
      assertThat(result.sourcePlatform()).isEqualTo("WB");
      assertThat(result.revenueAmount()).isEqualByComparingTo("5000.00");
    }

    @Test
    @DisplayName("should throw NotFoundException when entry not found")
    void should_throwNotFound_when_entryMissing() {
      when(provenanceRepository.findCanonicalEntry(ENTRY_ID, WORKSPACE_ID))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.getCanonicalEntry(ENTRY_ID, WORKSPACE_ID))
          .isInstanceOf(NotFoundException.class);
    }
  }

  @Nested
  @DisplayName("getRawUrl")
  class GetRawUrl {

    @Test
    @DisplayName("should return presigned URL when full chain exists")
    void should_returnPresignedUrl_when_fullChainExists() {
      var entry = sampleEntry(100L);
      when(provenanceRepository.findCanonicalEntry(ENTRY_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(entry));

      var rawFile = new RawFileInfo("raw/wb/finance/2025/01/page-001.json", 524288L);
      when(provenanceRepository.findRawFileInfo(100L, WORKSPACE_ID))
          .thenReturn(Optional.of(rawFile));
      when(rawStorageUrlProvider.generatePresignedUrl(rawFile.s3Key()))
          .thenReturn("https://s3.example.com/presigned-url");

      ProvenanceRawResponse result = service.getRawUrl(ENTRY_ID, WORKSPACE_ID);

      assertThat(result.presignedUrl()).isEqualTo("https://s3.example.com/presigned-url");
      assertThat(result.s3Key()).isEqualTo("raw/wb/finance/2025/01/page-001.json");
      assertThat(result.byteSize()).isEqualTo(524288L);
    }

    @Test
    @DisplayName("should throw NotFoundException when entry not found")
    void should_throwNotFound_when_entryMissing() {
      when(provenanceRepository.findCanonicalEntry(ENTRY_ID, WORKSPACE_ID))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.getRawUrl(ENTRY_ID, WORKSPACE_ID))
          .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("should throw NotFoundException when jobExecutionId is null")
    void should_throwNotFound_when_noJobExecutionId() {
      var entry = sampleEntry(null);
      when(provenanceRepository.findCanonicalEntry(ENTRY_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(entry));

      assertThatThrownBy(() -> service.getRawUrl(ENTRY_ID, WORKSPACE_ID))
          .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("should throw NotFoundException when raw file expired")
    void should_throwNotFound_when_rawFileExpired() {
      var entry = sampleEntry(100L);
      when(provenanceRepository.findCanonicalEntry(ENTRY_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(entry));
      when(provenanceRepository.findRawFileInfo(100L, WORKSPACE_ID))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.getRawUrl(ENTRY_ID, WORKSPACE_ID))
          .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("should call generatePresignedUrl with correct s3Key")
    void should_callPresignedUrl_when_rawFileFound() {
      var entry = sampleEntry(100L);
      when(provenanceRepository.findCanonicalEntry(ENTRY_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(entry));

      var rawFile = new RawFileInfo("raw/wb/finance/page.json", 1024L);
      when(provenanceRepository.findRawFileInfo(100L, WORKSPACE_ID))
          .thenReturn(Optional.of(rawFile));
      when(rawStorageUrlProvider.generatePresignedUrl("raw/wb/finance/page.json"))
          .thenReturn("https://s3.example.com/signed");

      service.getRawUrl(ENTRY_ID, WORKSPACE_ID);

      verify(rawStorageUrlProvider).generatePresignedUrl("raw/wb/finance/page.json");
    }
  }
}
