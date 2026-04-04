package io.datapulse.etl.adapter.ozon;

import java.util.Objects;

/**
 * Guards for Ozon Seller API list endpoints that use {@code offset} + {@code limit} pagination.
 * The API may return the same JSON body when offset is ignored or the window is exhausted; clients
 * must stop to avoid an infinite capture loop.
 */
public final class OzonOffsetPaging {

    /**
     * Hard cap on offset pages per adapter run — safety rail even if API ignores {@code offset} and
     * duplicate-page detection misses an edge case.
     */
    public static final int MAX_OFFSET_PAGES_PER_RUN = 5_000;

    /**
     * Ozon posting list responses below this size are treated as last page (legacy heuristic).
     */
    public static final int SMALL_PAGE_THRESHOLD_BYTES = 200;

    private OzonOffsetPaging() {}

    /**
     * True when the raw response body matches the previous page (same SHA-256 over streamed JSON).
     * Call only after at least one page was already accepted; otherwise pass {@code null} for
     * {@code previousContentSha256}.
     */
    public static boolean isRepeatedOffsetPage(
            String previousContentSha256, String currentContentSha256) {
        return previousContentSha256 != null
                && currentContentSha256 != null
                && Objects.equals(previousContentSha256, currentContentSha256);
    }
}
