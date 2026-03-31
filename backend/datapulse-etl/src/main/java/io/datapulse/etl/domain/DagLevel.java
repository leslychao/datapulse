package io.datapulse.etl.domain;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

public record DagLevel(
        int level,
        Set<EtlEventType> events
) {

    public DagLevel filterTo(Set<EtlEventType> scope) {
        Set<EtlEventType> filtered = events.stream()
                .filter(scope::contains)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(EtlEventType.class)));
        return new DagLevel(level, filtered);
    }

    public boolean isEmpty() {
        return events.isEmpty();
    }
}
