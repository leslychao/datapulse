package ru.vkim.datapulse.common;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("datapulse.files")
public class FileStorageProperties {
    private String baseDir;
    private int keepDays = 90;
}
