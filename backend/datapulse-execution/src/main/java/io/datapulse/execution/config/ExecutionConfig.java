package io.datapulse.execution.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ExecutionProperties.class)
public class ExecutionConfig {
}
