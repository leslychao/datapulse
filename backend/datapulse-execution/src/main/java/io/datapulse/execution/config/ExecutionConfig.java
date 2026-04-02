package io.datapulse.execution.config;

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ExecutionProperties.class)
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class ExecutionConfig {
}
