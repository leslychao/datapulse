package io.datapulse.etl.v1.execution;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EtlExecutionPayloadCodecTest {

  @Test
  void invalidJsonPayloadIsDroppedWithoutPersistenceSideEffects() {
    EtlExecutionPayloadCodec codec = new EtlExecutionPayloadCodec();
    assertTrue(codec.parseExecution("{bad-json".getBytes()).isEmpty());
  }
}
