package io.datapulse.etl.file.locator;

import io.datapulse.marketplaces.dto.raw.ozon.OzonLogisticClustersRaw;
import io.datapulse.marketplaces.dto.raw.ozon.OzonWarehouseListRaw;
import io.datapulse.marketplaces.dto.raw.wb.WbWarehouseListRaw;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.NonNull;
import org.springframework.stereotype.Component;

@Component
public final class SnapshotJsonLayoutRegistry {

  private final Map<Class<?>, JsonArrayLocator> locators = new ConcurrentHashMap<>();

  public SnapshotJsonLayoutRegistry() {
    register(OzonWarehouseListRaw.class, JsonArrayLocators.resultArray());
    register(WbWarehouseListRaw.class, JsonArrayLocators.rootArray());
    register(OzonLogisticClustersRaw.class, JsonArrayLocators.clustersArray());
  }

  public void register(
      @NonNull Class<?> rawType,
      @NonNull JsonArrayLocator locator
  ) {
    locators.put(rawType, locator);
  }

  public JsonArrayLocator resolve(@NonNull Class<?> rawType) {
    return locators.getOrDefault(rawType, JsonArrayLocators.rootArray());
  }
}
