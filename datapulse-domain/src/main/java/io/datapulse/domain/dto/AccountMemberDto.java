package io.datapulse.domain.dto;

import io.datapulse.domain.AccountMemberRole;
import io.datapulse.domain.AccountMemberStatus;
import java.time.OffsetDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class AccountMemberDto extends LongBaseDto {

  private Long accountId;
  private Long userId;
  private AccountMemberRole role;
  private AccountMemberStatus status;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
}
