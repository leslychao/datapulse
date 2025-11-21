package io.datapulse.domain.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class EtlSyncAuditDto extends LongBaseDto {

  private String requestId;
  private Long accountId;
  private String event;
  private LocalDate dateFrom;
  private LocalDate dateTo;
}
