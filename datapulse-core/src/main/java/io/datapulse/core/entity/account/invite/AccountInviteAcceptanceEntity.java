package io.datapulse.core.entity.account.invite;

import io.datapulse.core.entity.LongBaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "account_invite_acceptance")
@Getter
@Setter
public class AccountInviteAcceptanceEntity extends LongBaseEntity {

  private long inviteId;

  private long acceptedProfileId;

  private OffsetDateTime acceptedAt;
}
