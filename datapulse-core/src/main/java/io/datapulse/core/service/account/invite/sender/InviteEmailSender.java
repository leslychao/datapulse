package io.datapulse.core.service.account.invite.sender;

public interface InviteEmailSender {

    void sendInvite(String email, String rawToken);
  }