package io.datapulse.core.json.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.datapulse.domain.CommonConstants;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public class InstantWithTimeZoneSerializer extends JsonSerializer<Instant> {

  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

  @Override
  public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers)
      throws IOException {
    if (value == null) {
      gen.writeNull();
      return;
    }

    OffsetDateTime zoned = value.atZone(CommonConstants.ZONE_ID_DEFAULT).toOffsetDateTime();
    gen.writeString(FORMATTER.format(zoned));
  }
}
