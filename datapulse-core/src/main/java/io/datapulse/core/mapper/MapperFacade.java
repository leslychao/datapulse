package io.datapulse.core.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class MapperFacade {

  private final ConversionService conversionService;

  public <S, T> T to(S source, Class<T> targetType) {
    if (source == null) {
      return null;
    }
    var sourceType = source.getClass();
    if (!conversionService.canConvert(sourceType, targetType)) {
      throw new IllegalStateException(
          "No converter " + sourceType.getName() + " -> " + targetType.getName());
    }
    return conversionService.convert(source, targetType);
  }

  public <S, T> T to(S source, Class<S> sourceType, Class<T> targetType) {
    if (source == null) {
      return null;
    }
    if (!conversionService.canConvert(sourceType, targetType)) {
      throw new IllegalStateException(
          "No converter " + sourceType.getName() + " -> " + targetType.getName());
    }
    return conversionService.convert(source, targetType);
  }
}
