package io.datapulse.sellerops.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "datapulse.queue")
public class QueueProperties {

    private int defaultPageSize = 20;
    private int maxPageSize = 100;
}
