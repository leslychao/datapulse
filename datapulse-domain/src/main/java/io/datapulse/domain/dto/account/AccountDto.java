package io.datapulse.domain.dto.account;

import io.datapulse.domain.dto.LongBaseDto;
import java.time.OffsetDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class AccountDto extends LongBaseDto {

  private String name;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
  private boolean active;
}
