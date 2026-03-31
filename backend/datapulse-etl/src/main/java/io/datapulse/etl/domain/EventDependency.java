package io.datapulse.etl.domain;

public record EventDependency(
        EtlEventType event,
        DependencyType type
) {

    public static EventDependency hard(EtlEventType event) {
        return new EventDependency(event, DependencyType.HARD);
    }

    public static EventDependency soft(EtlEventType event) {
        return new EventDependency(event, DependencyType.SOFT);
    }
}
