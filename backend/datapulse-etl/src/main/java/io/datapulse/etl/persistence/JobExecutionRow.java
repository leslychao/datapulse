package io.datapulse.etl.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobExecutionRow {

    private Long id;
    private long connectionId;
    private String eventType;
    private String status;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    /** When the job entered MATERIALIZING; null otherwise. */
    private OffsetDateTime materializingAt;
    private String errorDetails;
    private String checkpoint;
    /** Optional JSON: domains, sourceJobId, trigger, etc. */
    private String params;
    private OffsetDateTime createdAt;
}
