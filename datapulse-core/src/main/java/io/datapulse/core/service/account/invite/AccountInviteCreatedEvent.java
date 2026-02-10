package io.datapulse.core.service.account.invite;

public record AccountInviteCreatedEvent(String email, String rawToken) {

}
