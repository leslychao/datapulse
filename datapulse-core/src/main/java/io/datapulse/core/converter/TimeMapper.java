package io.datapulse.core.converter;

import java.time.OffsetDateTime;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TimeMapper {

  default String asString(OffsetDateTime date) {
    return date == null ? null : date.toString();
  }
}
