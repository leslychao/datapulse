package io.datapulse.tenancy.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@Getter
@ConfigurationProperties(prefix = "datapulse.mail")
public class MailProperties {

    private final String from;
    private final String invitationBaseUrl;

    public MailProperties(
            @DefaultValue("noreply@datapulse.io") String from,
            @DefaultValue("http://localhost:4200/invitation/accept") String invitationBaseUrl) {
        this.from = from;
        this.invitationBaseUrl = invitationBaseUrl;
    }
}
