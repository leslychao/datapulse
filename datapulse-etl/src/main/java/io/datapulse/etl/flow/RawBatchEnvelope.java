package io.datapulse.etl.flow;

import java.util.List;

public record RawBatchEnvelope(List<Object> batch, boolean lastBatch) {
}
