package io.datapulse.domain.dto.userprofile;

import io.datapulse.domain.dto.LongBaseDto;
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
  private String fullName;
  private String username;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
}
