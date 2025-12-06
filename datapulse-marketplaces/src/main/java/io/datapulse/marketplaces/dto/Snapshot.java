package io.datapulse.marketplaces.dto;

import java.nio.file.Path;

public record Snapshot<R>(
    Class<R> elementType,
    Path file
) {

}
