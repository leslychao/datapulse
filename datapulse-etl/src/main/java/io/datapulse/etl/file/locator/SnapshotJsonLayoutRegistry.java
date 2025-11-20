package io.datapulse.etl.file.locator;

import io.datapulse.domain.dto.raw.ozon.OzonAnalyticsApiRaw;
import io.datapulse.domain.dto.raw.ozon.OzonProductInfoRaw;
import io.datapulse.domain.dto.raw.wb.WbRealizationRaw;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.NonNull;
import org.springframework.stereotype.Component;

@Component
public final class SnapshotJsonLayoutRegistry {

  private final Map<Class<?>, JsonArrayLocator> locators = new ConcurrentHashMap<>();

  public SnapshotJsonLayoutRegistry() {
    register(WbRealizationRaw.class, JsonArrayLocators.rootArray());
    register(OzonAnalyticsApiRaw.class, JsonArrayLocators.resultDataArray());
    register(OzonProductInfoRaw.class, JsonArrayLocators.itemsArray());
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
