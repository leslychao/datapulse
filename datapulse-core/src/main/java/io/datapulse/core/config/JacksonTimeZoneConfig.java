package io.datapulse.core.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.datapulse.core.json.serializer.InstantWithTimeZoneSerializer;
import io.datapulse.core.json.serializer.OffsetDateTimeWithTimeZoneSerializer;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonTimeZoneConfig {

  @Bean
  public Module timeZoneAwareTimeModule() {
    SimpleModule module = new SimpleModule("time-zone-aware-time-module");
    module.addSerializer(Instant.class, new InstantWithTimeZoneSerializer());
    module.addSerializer(OffsetDateTime.class, new OffsetDateTimeWithTimeZoneSerializer());
    return module;
  }
}
