package io.datapulse.etl.flow;

import io.datapulse.etl.dto.StreamItem;
import java.util.Collection;
import org.springframework.messaging.Message;

public final class StreamItemUtils {

  private StreamItemUtils() {
  }

  public static boolean containsLast(Collection<Message<?>> messages) {
    for (Message<?> m : messages) {
      StreamItem<?> si = (StreamItem<?>) m.getPayload();
      if (si.last()) {
        return true;
      }
    }
    return false;
  }
}
