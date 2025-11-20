package io.datapulse.etl.route;

import io.datapulse.domain.MarketplaceEvent;
import io.datapulse.domain.MarketplaceType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.NonNull;
import org.springframework.stereotype.Component;

@Component
public final class EtlSourceRegistry {

  private final Map<Key, List<RegisteredSource>> sourcesByKey;

  public EtlSourceRegistry(@NonNull List<EventSource> sources) {
    Map<Key, List<RegisteredSource>> tmp = new LinkedHashMap<>();

    for (EventSource source : sources) {
      Class<?> type = source.getClass();
      EtlSourceMeta meta = type.getAnnotation(EtlSourceMeta.class);
      if (meta == null) {
        continue;
      }

      Key key = new Key(meta.event(), meta.marketplace());
      tmp.computeIfAbsent(key, k -> new ArrayList<>())
          .add(new RegisteredSource(
              meta.event(),
              meta.marketplace(),
              meta.order(),
              type.getSimpleName(),
              source
          ));
    }

    tmp.replaceAll((key, list) -> list.stream()
        .sorted(Comparator.comparingInt(RegisteredSource::order))
        .toList());

    this.sourcesByKey = Map.copyOf(tmp);
  }

  public List<RegisteredSource> findSources(
      @NonNull MarketplaceEvent event,
      @NonNull MarketplaceType marketplace
  ) {
    Key key = new Key(event, marketplace);
    return sourcesByKey.getOrDefault(key, List.of());
  }

  public record RegisteredSource(
      MarketplaceEvent event,
      MarketplaceType marketplace,
      int order,
      String sourceId,
      EventSource source
  ) {

  }

  private record Key(
      MarketplaceEvent event,
      MarketplaceType marketplace
  ) {

  }
}
