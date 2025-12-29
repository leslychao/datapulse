package io.datapulse.etl.event;

import static io.datapulse.domain.MessageCodes.ETL_EVENT_REQUIRED;
import static io.datapulse.domain.MessageCodes.ETL_EVENT_SOURCES_MISSING;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.exception.AppException;
import io.datapulse.etl.MarketplaceEvent;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

@Component
public final class EtlSourceRegistry {

  private static final Comparator<RegisteredSource> BY_ORDER =
      Comparator.comparingInt(RegisteredSource::order);

  private final Map<MarketplaceEvent, List<RegisteredSource>> sourcesByEvent;

  public EtlSourceRegistry(
      @NotNull(message = ETL_EVENT_SOURCES_MISSING)
      List<EventSource> sources
  ) {
    Map<MarketplaceEvent, List<RegisteredSource>> registry =
        new EnumMap<>(MarketplaceEvent.class);

    for (EventSource source : sources) {
      if (source == null) {
        continue;
      }

      EtlSourceMeta meta = resolveMeta(source);
      if (meta == null) {
        continue;
      }

      MarketplaceEvent[] events = meta.events();

      for (MarketplaceEvent event : events) {
        registry
            .computeIfAbsent(event, e -> new ArrayList<>())
            .add(toRegisteredSource(event, meta, source));
      }
    }

    registry.replaceAll((event, list) ->
        list.stream()
            .sorted(BY_ORDER)
            .toList()
    );

    this.sourcesByEvent = Map.copyOf(registry);
  }

  public List<RegisteredSource> getSources(
      @NotNull(message = ETL_EVENT_REQUIRED) MarketplaceEvent event
  ) {
    List<RegisteredSource> registeredSources = sourcesByEvent.get(event);
    if (registeredSources == null || registeredSources.isEmpty()) {
      throw new AppException(
          ETL_EVENT_SOURCES_MISSING,
          "No ETL sources registered for event " + event.name()
      );
    }
    return registeredSources;
  }

  private static EtlSourceMeta resolveMeta(EventSource source) {
    Class<?> targetClass = AopUtils.getTargetClass(source);
    return AnnotationUtils.findAnnotation(targetClass, EtlSourceMeta.class);
  }

  private static RegisteredSource toRegisteredSource(
      MarketplaceEvent event,
      EtlSourceMeta meta,
      EventSource source
  ) {
    Class<?> targetClass = AopUtils.getTargetClass(source);
    MarketplaceType marketplace = meta.marketplace();
    int order = meta.order();
    String sourceId = targetClass.getSimpleName();
    String rawTableName = meta.rawTableName();

    return new RegisteredSource(
        event,
        marketplace,
        order,
        sourceId,
        source,
        rawTableName
    );
  }

  public record RegisteredSource(
      MarketplaceEvent event,
      MarketplaceType marketplace,
      int order,
      String sourceId,
      EventSource source,
      String rawTable
  ) {

  }
}
