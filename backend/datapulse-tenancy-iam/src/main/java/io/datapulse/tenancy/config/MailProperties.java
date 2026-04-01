package io.datapulse.tenancy.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@ConfigurationProperties(prefix = "datapulse.mail")
public class MailProperties {

    private final String from;
    private final String invitationBaseUrl;

    public MailProperties(String from, String invitationBaseUrl) {
        this.from = from;
        this.invitationBaseUrl = invitationBaseUrl;
    }
}
