package io.datapulse.domain.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;

public class AccountDto extends LongBaseDto {

  @NotBlank(message = "{account.name.required}")
  private String name;
  OffsetDateTime createdAt;
  OffsetDateTime updatedAt;
}
