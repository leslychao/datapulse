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
    private String errorDetails;
    private String checkpoint;
    private OffsetDateTime createdAt;
}
