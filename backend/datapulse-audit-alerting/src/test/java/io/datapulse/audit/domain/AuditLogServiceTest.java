package io.datapulse.audit.domain;

import io.datapulse.audit.api.AuditLogFilter;
import io.datapulse.audit.api.AuditLogResponse;
import io.datapulse.audit.persistence.AuditLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

  @Mock
  private AuditLogRepository auditLogRepository;

  @InjectMocks
  private AuditLogService auditLogService;

  private AuditLogResponse buildLogResponse(long id) {
    return new AuditLogResponse(
        id, 1L, "USER", 10L, "workspace.create", "workspace",
        "1", "SUCCESS", null, null, null, OffsetDateTime.now());
  }

  @Nested
  @DisplayName("listAuditLog")
  class ListAuditLog {

    @Test
    @DisplayName("should_return_paginated_results_when_data_exists")
    void should_return_paginated_results_when_data_exists() {
      var filter = new AuditLogFilter(null, null, null, null, null, null);
      Pageable pageable = PageRequest.of(0, 10);
      var log1 = buildLogResponse(1L);
      var log2 = buildLogResponse(2L);

      when(auditLogRepository.findAll(eq(1L), eq(filter), eq("createdAt"), eq(10), eq(0L)))
          .thenReturn(List.of(log1, log2));
      when(auditLogRepository.count(1L, filter)).thenReturn(2L);

      Page<AuditLogResponse> result = auditLogService.listAuditLog(1L, filter, pageable);

      assertThat(result.getContent()).hasSize(2);
      assertThat(result.getTotalElements()).isEqualTo(2L);
    }

    @Test
    @DisplayName("should_use_default_sort_when_unsorted")
    void should_use_default_sort_when_unsorted() {
      var filter = new AuditLogFilter(null, null, null, null, null, null);
      Pageable pageable = PageRequest.of(0, 20);

      when(auditLogRepository.findAll(eq(1L), any(), eq("createdAt"), eq(20), eq(0L)))
          .thenReturn(List.of());
      when(auditLogRepository.count(eq(1L), any())).thenReturn(0L);

      auditLogService.listAuditLog(1L, filter, pageable);

      verify(auditLogRepository).findAll(1L, filter, "createdAt", 20, 0L);
    }

    @Test
    @DisplayName("should_use_provided_sort_when_specified")
    void should_use_provided_sort_when_specified() {
      var filter = new AuditLogFilter(null, null, null, null, null, null);
      Pageable pageable = PageRequest.of(0, 10, Sort.by("actionType"));

      when(auditLogRepository.findAll(eq(1L), any(), eq("actionType"), eq(10), eq(0L)))
          .thenReturn(List.of());
      when(auditLogRepository.count(eq(1L), any())).thenReturn(0L);

      auditLogService.listAuditLog(1L, filter, pageable);

      verify(auditLogRepository).findAll(1L, filter, "actionType", 10, 0L);
    }

    @Test
    @DisplayName("should_return_empty_page_when_no_data")
    void should_return_empty_page_when_no_data() {
      var filter = new AuditLogFilter(null, null, null, null, null, null);
      Pageable pageable = PageRequest.of(0, 10);

      when(auditLogRepository.findAll(anyLong(), any(), anyString(), eq(10), eq(0L)))
          .thenReturn(List.of());
      when(auditLogRepository.count(anyLong(), any())).thenReturn(0L);

      Page<AuditLogResponse> result = auditLogService.listAuditLog(1L, filter, pageable);

      assertThat(result.getContent()).isEmpty();
      assertThat(result.getTotalElements()).isZero();
    }
  }
}
