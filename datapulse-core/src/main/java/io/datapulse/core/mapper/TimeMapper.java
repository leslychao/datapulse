package io.datapulse.core.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TimeMapper {

  DateTimeFormatter HUMAN_READABLE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
  DateTimeFormatter DATE_ONLY = DateTimeFormatter.ISO_LOCAL_DATE;
  DateTimeFormatter DATE_TIME_LOCAL = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

  default OffsetDateTime map(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return OffsetDateTime.parse(value);
    } catch (DateTimeParseException ignored) {
    }
    try {
      LocalDate d = LocalDate.parse(value);
      return d.atStartOfDay().atZone(ZoneId.systemDefault()).toOffsetDateTime();
    } catch (DateTimeParseException ignored) {
    }
    try {
      LocalDateTime dt = LocalDateTime.parse(value);
      return dt.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    } catch (DateTimeParseException ignored) {
    }
    return null;
  }

  default LocalDate mapToLocalDate(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(value, DATE_ONLY);
    } catch (DateTimeParseException ignored) {
    }
    try {
      return LocalDateTime.parse(value, DATE_TIME_LOCAL).toLocalDate();
    } catch (DateTimeParseException ignored) {
    }
    try {
      return OffsetDateTime.parse(value).toLocalDate();
    } catch (DateTimeParseException ignored) {
    }
    return null;
  }

  default LocalDateTime mapToLocalDateTime(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return LocalDateTime.parse(value, DATE_TIME_LOCAL);
    } catch (DateTimeParseException ignored) {
    }
    try {
      return OffsetDateTime.parse(value)
          .atZoneSameInstant(ZoneId.systemDefault())
          .toLocalDateTime();
    } catch (DateTimeParseException ignored) {
    }
    try {
      return LocalDate.parse(value).atStartOfDay();
    } catch (DateTimeParseException ignored) {
    }
    return null;
  }

  default String asString(LocalDate value) {
    return value == null ? null : value.format(DATE_ONLY);
  }

  default String asString(LocalDateTime value) {
    return value == null ? null : value.format(DATE_TIME_LOCAL);
  }

  default String asString(OffsetDateTime value) {
    if (value == null) {
      return null;
    }
    return value.withOffsetSameInstant(ZoneOffset.UTC).format(HUMAN_READABLE);
  }
}
