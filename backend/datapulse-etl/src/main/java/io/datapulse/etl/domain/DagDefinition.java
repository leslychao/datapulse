package io.datapulse.etl.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Static ETL dependency graph. Topological levels and per-event dependencies
 * are hardcoded based on the spec (etl-pipeline.md §Граф зависимостей).
 *
 * <pre>
 * Level 0:  CATEGORY_DICT ║ WAREHOUSE_DICT
 * Level 1:  PRODUCT_DICT
 * Level 2:  PRICE_SNAPSHOT ║ INVENTORY_FACT ║ SALES_FACT ║ ADVERTISING_FACT ║ PROMO_SYNC
 * Level 3:  FACT_FINANCE (soft dep on SALES_FACT)
 * </pre>
 */
public final class DagDefinition {

    private DagDefinition() {
    }

    private static final Map<EtlEventType, List<EventDependency>> DEPENDENCIES;
    private static final List<DagLevel> LEVELS;

    static {
        DEPENDENCIES = new EnumMap<>(EtlEventType.class);

        DEPENDENCIES.put(EtlEventType.CATEGORY_DICT, List.of());
        DEPENDENCIES.put(EtlEventType.WAREHOUSE_DICT, List.of());

        DEPENDENCIES.put(EtlEventType.PRODUCT_DICT, List.of(
                EventDependency.hard(EtlEventType.CATEGORY_DICT),
                EventDependency.hard(EtlEventType.WAREHOUSE_DICT)
        ));

        DEPENDENCIES.put(EtlEventType.PRICE_SNAPSHOT, List.of(
                EventDependency.hard(EtlEventType.PRODUCT_DICT)
        ));
        DEPENDENCIES.put(EtlEventType.INVENTORY_FACT, List.of(
                EventDependency.hard(EtlEventType.PRODUCT_DICT),
                EventDependency.hard(EtlEventType.WAREHOUSE_DICT)
        ));
        DEPENDENCIES.put(EtlEventType.SALES_FACT, List.of(
                EventDependency.hard(EtlEventType.PRODUCT_DICT)
        ));
        DEPENDENCIES.put(EtlEventType.ADVERTISING_FACT, List.of(
                EventDependency.hard(EtlEventType.PRODUCT_DICT)
        ));
        DEPENDENCIES.put(EtlEventType.PROMO_SYNC, List.of(
                EventDependency.hard(EtlEventType.PRODUCT_DICT)
        ));

        DEPENDENCIES.put(EtlEventType.FACT_FINANCE, List.of(
                EventDependency.hard(EtlEventType.PRODUCT_DICT),
                EventDependency.soft(EtlEventType.SALES_FACT)
        ));

        LEVELS = List.of(
                new DagLevel(0, EnumSet.of(EtlEventType.CATEGORY_DICT, EtlEventType.WAREHOUSE_DICT)),
                new DagLevel(1, EnumSet.of(EtlEventType.PRODUCT_DICT)),
                new DagLevel(2, EnumSet.of(
                        EtlEventType.PRICE_SNAPSHOT,
                        EtlEventType.INVENTORY_FACT,
                        EtlEventType.SALES_FACT,
                        EtlEventType.ADVERTISING_FACT,
                        EtlEventType.PROMO_SYNC
                )),
                new DagLevel(3, EnumSet.of(EtlEventType.FACT_FINANCE))
        );
    }

    public static List<DagLevel> levels() {
        return LEVELS;
    }

    /**
     * Returns levels filtered to only include events from the given scope.
     * Empty levels are excluded.
     */
    public static List<DagLevel> levelsFor(Set<EtlEventType> scope) {
        return LEVELS.stream()
                .map(level -> level.filterTo(scope))
                .filter(level -> !level.isEmpty())
                .toList();
    }

    public static List<EventDependency> dependenciesOf(EtlEventType event) {
        return DEPENDENCIES.getOrDefault(event, List.of());
    }

    /**
     * Returns only hard dependencies for the given event.
     */
    public static List<EtlEventType> hardDependenciesOf(EtlEventType event) {
        return dependenciesOf(event).stream()
                .filter(dep -> dep.type() == DependencyType.HARD)
                .map(EventDependency::event)
                .toList();
    }

    /**
     * Returns only soft dependencies for the given event.
     */
    public static List<EtlEventType> softDependenciesOf(EtlEventType event) {
        return dependenciesOf(event).stream()
                .filter(dep -> dep.type() == DependencyType.SOFT)
                .map(EventDependency::event)
                .toList();
    }

    /**
     * Full FULL_SYNC scope: all events in the DAG.
     */
    public static Set<EtlEventType> fullSyncScope() {
        return EnumSet.allOf(EtlEventType.class);
    }
}
