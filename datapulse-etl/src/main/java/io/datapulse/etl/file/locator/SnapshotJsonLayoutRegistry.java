package io.datapulse.etl.file.locator;

import io.datapulse.marketplaces.dto.raw.ozon.OzonClusterListRaw;
import io.datapulse.marketplaces.dto.raw.ozon.OzonWarehouseFbsListRaw;
import io.datapulse.marketplaces.dto.raw.wb.WbOfficeFbsListRaw;
import io.datapulse.marketplaces.dto.raw.wb.WbWarehouseFbwListRaw;
import io.datapulse.marketplaces.dto.raw.wb.WbWarehouseSellerListRaw;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.NonNull;
import org.springframework.stereotype.Component;

@Component
public final class SnapshotJsonLayoutRegistry {

  private final Map<Class<?>, JsonArrayLocator> locators = new ConcurrentHashMap<>();

  public SnapshotJsonLayoutRegistry() {
    register(OzonWarehouseFbsListRaw.class, JsonArrayLocators.arrayAtPath("result"));
    register(OzonClusterListRaw.class, JsonArrayLocators.arrayAtPath("clusters"));
    register(WbWarehouseFbwListRaw.class, JsonArrayLocators.arrayAtPath());
    register(WbOfficeFbsListRaw.class, JsonArrayLocators.arrayAtPath());
    register(WbWarehouseSellerListRaw.class, JsonArrayLocators.arrayAtPath());
  }

  public void register(@NonNull Class<?> rawType, @NonNull JsonArrayLocator locator) {
    locators.put(rawType, locator);
  }

  public JsonArrayLocator resolve(@NonNull Class<?> rawType) {
    return locators.getOrDefault(rawType, JsonArrayLocators.arrayAtPath());
  }
}
