package io.datapulse.etl.domain.cursor;

import java.nio.file.Path;
import java.util.Optional;

/**
 * For externally-paged endpoints (~11 endpoints: WB Orders/Sales/Returns/Incomes/Offices,
 * WB Prices/Stocks offset, Ozon Orders/Returns/Catalog info).
 * No cursor extraction needed — pagination is controlled by the caller (offset, date range).
 */
public final class NoCursorExtractor implements CursorExtractor {

    public static final NoCursorExtractor INSTANCE = new NoCursorExtractor();

    private NoCursorExtractor() {}

    @Override
    public Optional<String> extract(Path tempFile) {
        return Optional.empty();
    }
}
