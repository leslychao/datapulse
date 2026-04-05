package io.datapulse.etl.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DagDefinitionTest {

  @Nested
  @DisplayName("levels()")
  class Levels {

    @Test
    void should_return4Levels() {
      assertThat(DagDefinition.levels()).hasSize(4);
    }

    @Test
    void should_haveDictsAtLevel0() {
      var level0 = DagDefinition.levels().get(0);

      assertThat(level0.level()).isZero();
      assertThat(level0.events()).containsExactlyInAnyOrder(
          EtlEventType.CATEGORY_DICT, EtlEventType.WAREHOUSE_DICT);
    }

    @Test
    void should_haveProductDictAtLevel1() {
      var level1 = DagDefinition.levels().get(1);

      assertThat(level1.level()).isEqualTo(1);
      assertThat(level1.events()).containsExactly(EtlEventType.PRODUCT_DICT);
    }

    @Test
    void should_haveFactsAtLevel2() {
      var level2 = DagDefinition.levels().get(2);

      assertThat(level2.level()).isEqualTo(2);
      assertThat(level2.events()).containsExactlyInAnyOrder(
          EtlEventType.PRICE_SNAPSHOT,
          EtlEventType.INVENTORY_FACT,
          EtlEventType.SUPPLY_FACT,
          EtlEventType.SALES_FACT,
          EtlEventType.PROMO_SYNC);
    }

    @Test
    void should_haveFinanceAtLevel3() {
      var level3 = DagDefinition.levels().get(3);

      assertThat(level3.level()).isEqualTo(3);
      assertThat(level3.events()).containsExactly(EtlEventType.FACT_FINANCE);
    }
  }

  @Nested
  @DisplayName("levelsFor()")
  class LevelsFor {

    @Test
    void should_filterToScope() {
      Set<EtlEventType> scope = EnumSet.of(
          EtlEventType.CATEGORY_DICT, EtlEventType.PRODUCT_DICT);

      var filtered = DagDefinition.levelsFor(scope);

      assertThat(filtered).hasSize(2);
      assertThat(filtered.get(0).level()).isZero();
      assertThat(filtered.get(0).events()).containsExactly(EtlEventType.CATEGORY_DICT);
      assertThat(filtered.get(1).level()).isEqualTo(1);
      assertThat(filtered.get(1).events()).containsExactly(EtlEventType.PRODUCT_DICT);
    }

    @Test
    void should_excludeEmptyLevels() {
      Set<EtlEventType> scope = EnumSet.of(EtlEventType.FACT_FINANCE);

      var filtered = DagDefinition.levelsFor(scope);

      assertThat(filtered).hasSize(1);
      assertThat(filtered.get(0).level()).isEqualTo(3);
      assertThat(filtered.get(0).events()).containsExactly(EtlEventType.FACT_FINANCE);
    }
  }

  @Nested
  @DisplayName("dependencies")
  class Dependencies {

    @Test
    void should_returnHardDeps_for_productDict() {
      var deps = DagDefinition.hardDependenciesOf(EtlEventType.PRODUCT_DICT);

      assertThat(deps).containsExactlyInAnyOrder(
          EtlEventType.CATEGORY_DICT, EtlEventType.WAREHOUSE_DICT);
    }

    @Test
    void should_returnSoftDep_for_factFinance() {
      var softDeps = DagDefinition.softDependenciesOf(EtlEventType.FACT_FINANCE);

      assertThat(softDeps).containsExactly(EtlEventType.SALES_FACT);
    }

    @Test
    void should_returnEmptyDeps_for_categoryDict() {
      assertThat(DagDefinition.dependenciesOf(EtlEventType.CATEGORY_DICT)).isEmpty();
    }

    @Test
    void should_returnHardDep_for_inventoryFact() {
      var deps = DagDefinition.hardDependenciesOf(EtlEventType.INVENTORY_FACT);

      assertThat(deps).containsExactlyInAnyOrder(
          EtlEventType.PRODUCT_DICT, EtlEventType.WAREHOUSE_DICT);
    }
  }

  @Nested
  @DisplayName("fullSyncScope()")
  class FullSyncScope {

    @Test
    void should_equalUnionOfAllDagLevels() {
      EnumSet<EtlEventType> expected = EnumSet.noneOf(EtlEventType.class);
      for (DagLevel level : DagDefinition.levels()) {
        expected.addAll(level.events());
      }
      assertThat(DagDefinition.fullSyncScope()).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("scopeWithHardDependencyClosure()")
  class HardDependencyClosure {

    @Test
    void should_addProductDict_when_seedIsSalesFact() {
      var scope = DagDefinition.scopeWithHardDependencyClosure(
          EnumSet.of(EtlEventType.SALES_FACT));

      assertThat(scope).containsExactlyInAnyOrder(
          EtlEventType.SALES_FACT,
          EtlEventType.PRODUCT_DICT,
          EtlEventType.CATEGORY_DICT,
          EtlEventType.WAREHOUSE_DICT);
    }

    @Test
    void should_addCategoryAndWarehouse_when_seedIsProductDict() {
      var scope = DagDefinition.scopeWithHardDependencyClosure(
          EnumSet.of(EtlEventType.PRODUCT_DICT));

      assertThat(scope).containsExactlyInAnyOrder(
          EtlEventType.PRODUCT_DICT,
          EtlEventType.CATEGORY_DICT,
          EtlEventType.WAREHOUSE_DICT);
    }
  }
}
