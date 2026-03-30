package io.datapulse.platform.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class MetricsFacade {

    private final MeterRegistry meterRegistry;

    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopTimer(Timer.Sample sample, String name, String... tags) {
        sample.stop(Timer.builder(name)
                .tags(tags)
                .register(meterRegistry));
    }

    public void incrementCounter(String name, String... tags) {
        meterRegistry.counter(name, tags).increment();
    }

    public void recordDuration(String name, Duration duration, String... tags) {
        Timer.builder(name)
                .tags(tags)
                .register(meterRegistry)
                .record(duration);
    }
}
