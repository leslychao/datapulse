package io.datapulse.etl.adapter.ozon;

import java.util.Objects;

/**
 * Shared pagination guards for Ozon Seller API list endpoints that return a string {@code last_id}
 * cursor.
 */
public final class OzonCursorPaging {

    private OzonCursorPaging() {}

    /**
     * Outcome after one list page: whether to stop crawling, and whether stop was due to a
     * non-advancing cursor (non-empty response {@code last_id} equal to the request), useful for
     * diagnostics.
     */
    public record StringCursorPageOutcome(boolean stop, boolean nonAdvancingCursor) {}

    /**
     * Decides pagination continuation from response vs request {@code last_id}. Does not use
     * response byte size — small valid JSON pages must not truncate the crawl.
     */
    public static StringCursorPageOutcome afterPage(String responseLastId, String requestLastId) {
        boolean sameCursor = Objects.equals(responseLastId, requestLastId);
        boolean stop = responseLastId == null || responseLastId.isEmpty() || sameCursor;
        boolean nonAdvancing =
                responseLastId != null && !responseLastId.isEmpty() && sameCursor;
        return new StringCursorPageOutcome(stop, nonAdvancing);
    }
}
