package io.datapulse.etl.domain.cursor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Extracts pagination cursor from a raw API response saved as temp file.
 * Three families match three pagination families in the ETL spec.
 */
public sealed interface CursorExtractor
        permits NoCursorExtractor, JsonPathCursorExtractor, TailFieldExtractor,
                WbCatalogCursorExtractor {

    Optional<String> extract(Path tempFile) throws IOException;
}
