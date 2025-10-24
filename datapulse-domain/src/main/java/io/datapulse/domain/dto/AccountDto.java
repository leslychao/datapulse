package io.datapulse.domain.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class AccountDto extends LongBaseDto {
  private String marketplace;
  private String name;
  private String tokenEncrypted;
  private String login;
  private Boolean active;
}
