package io.datapulse.marketplaces.dto;

import java.net.URI;
import java.nio.file.Path;
import org.springframework.http.HttpMethod;

public record Snapshot<R>(
    Class<R> elementType,
    Path file,
    long sizeBytes,
    URI sourceUri,
    HttpMethod httpMethod
) {

}
