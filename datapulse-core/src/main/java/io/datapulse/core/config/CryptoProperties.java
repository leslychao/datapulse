package io.datapulse.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "crypto")
public class CryptoProperties {

  private String masterKeyBase64;
  private int gcmTagLengthBits = 128;
}
