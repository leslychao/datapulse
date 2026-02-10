package io.datapulse.core.service.invite.sender;

public interface InviteEmailSender {

  void sendInvite(String email, String rawToken);
}
