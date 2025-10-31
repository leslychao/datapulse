package io.datapulse.core.mapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TimeMapper {

  DateTimeFormatter HUMAN_READABLE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  default LocalDate toLocalDate(OffsetDateTime value) {
    return value == null ? null : value.toLocalDate();
  }

  default String asString(OffsetDateTime date) {
    if (date == null) {
      return null;
    }
    return date.withOffsetSameInstant(ZoneOffset.UTC).format(HUMAN_READABLE);
  }

  default String toStringSafe(Long value) {
    return value == null ? null : value.toString();
  }
}
