package io.datapulse.etl.event;

import static io.datapulse.domain.MessageCodes.ETL_EVENT_REQUIRED;
import static io.datapulse.domain.ValidationKeys.ETL_EVENT_SOURCES_MISSING;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.exception.AppException;
import io.datapulse.etl.MarketplaceEvent;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

@Component
public final class EtlSourceRegistry {

  private final Map<MarketplaceEvent, List<RegisteredSource>> sourcesByEvent;

  public EtlSourceRegistry(
      @NotNull(message = ETL_EVENT_SOURCES_MISSING)
      List<EventSource> sources
  ) {
    Map<MarketplaceEvent, List<RegisteredSource>> tmp = new LinkedHashMap<>();

    for (EventSource source : sources) {
      if (source == null) {
        continue;
      }

      Class<?> targetClass = AopUtils.getTargetClass(source);
      EtlSourceMeta meta = AnnotationUtils.findAnnotation(targetClass, EtlSourceMeta.class);
      if (meta == null) {
        continue;
      }

      MarketplaceEvent event = meta.event();
      MarketplaceType marketplace = meta.marketplace();

      RegisteredSource registeredSource = new RegisteredSource(
          event,
          marketplace,
          meta.order(),
          targetClass.getSimpleName(),
          source,
          meta.rawTableName()
      );

      tmp.computeIfAbsent(event, e -> new ArrayList<>()).add(registeredSource);
    }

    tmp.replaceAll((event, list) ->
        list.stream()
            .sorted(Comparator.comparingInt(RegisteredSource::order))
            .toList()
    );

    this.sourcesByEvent = Map.copyOf(tmp);
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
