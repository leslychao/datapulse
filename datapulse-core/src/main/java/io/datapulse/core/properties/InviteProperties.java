package io.datapulse.core.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "app.invitation")
public class InviteProperties {

  @NotBlank
  private final String publicBaseUrl;

  @Valid
  private final Email email;

  @Getter
  @RequiredArgsConstructor
  public static class Email {

    @NotBlank
    private final String from;
  }
}
