package io.datapulse.platform.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Bean("etlExecutor")
    public TaskExecutor etlExecutor() {
        return buildExecutor("etl-", 2, 5, 50);
    }

    @Bean("pricingExecutor")
    public TaskExecutor pricingExecutor() {
        return buildExecutor("pricing-", 2, 5, 50);
    }

    @Bean("executionExecutor")
    public TaskExecutor executionExecutor() {
        return buildExecutor("execution-", 2, 5, 50);
    }

    @Bean("integrationExecutor")
    public TaskExecutor integrationExecutor() {
        return buildExecutor("integration-", 2, 4, 30);
    }

    @Bean("notificationExecutor")
    public TaskExecutor notificationExecutor() {
        return buildExecutor("notification-", 2, 5, 100);
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
                log.error("Async method failed: method={}, error={}", method.getName(), ex.getMessage(), ex);
    }

    private ThreadPoolTaskExecutor buildExecutor(String prefix, int coreSize, int maxSize, int queueCapacity) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(prefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
