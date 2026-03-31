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
public class JobItemRow {

    private Long id;
    private long jobExecutionId;
    private String requestId;
    private String sourceId;
    private int pageNumber;
    private String s3Key;
    private Integer recordCount;
    private String contentSha256;
    private long byteSize;
    private String status;
    private OffsetDateTime capturedAt;
    private OffsetDateTime processedAt;
}
