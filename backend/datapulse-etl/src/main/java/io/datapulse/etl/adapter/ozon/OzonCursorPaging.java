package io.datapulse.etl.adapter.ozon;

import java.util.Objects;

/**
 * Shared pagination guards for Ozon Seller API list endpoints that return a string {@code last_id}
 * cursor.
 */
public final class OzonCursorPaging {

    private OzonCursorPaging() {}

    /**
     * True when the page should be treated as the last one: missing cursor, or the API echoed the
     * same {@code last_id} as in the request (pagination did not advance). Deliberately does not use
     * response byte size — small valid JSON pages must not truncate the crawl.
     */
    public static boolean shouldStopAfterStringPage(String responseLastId, String requestLastId) {
        return responseLastId == null
                || responseLastId.isEmpty()
                || Objects.equals(responseLastId, requestLastId);
    }

    /** Cursor present and identical to the value sent in the request (pagination did not advance). */
    public static boolean isNonAdvancingStringCursor(String responseLastId, String requestLastId) {
        return responseLastId != null
                && !responseLastId.isEmpty()
                && Objects.equals(responseLastId, requestLastId);
    }
}
