package io.datapulse.domain.dto;

import java.time.OffsetDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class UserProfileDto extends LongBaseDto {

  private String keycloakSub;
  private String email;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
}
