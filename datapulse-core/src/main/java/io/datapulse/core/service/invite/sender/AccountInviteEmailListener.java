package io.datapulse.core.service.invite.sender;

import io.datapulse.core.service.invite.AccountInviteCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AccountInviteEmailListener {

  private final InviteEmailSender inviteEmailSender;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onInviteCreated(AccountInviteCreatedEvent event) {
    inviteEmailSender.sendInvite(event.email(), event.rawToken());
  }
}
