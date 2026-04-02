package io.datapulse.etl.adapter.ozon;

import java.util.Objects;

/**
 * Shared pagination guards for Ozon Seller API list endpoints that return a string {@code last_id}
 * cursor.
 */
public final class OzonCursorPaging {

    private OzonCursorPaging() {}

    /**
     * True when the page should be treated as the last one: missing cursor, very small payload, or
     * the API echoed the same {@code last_id} as in the request (terminal page with non-trivial JSON).
     */
    public static boolean shouldStopAfterStringPage(
            String responseLastId, String requestLastId, long captureByteSize) {
        return responseLastId == null
                || responseLastId.isEmpty()
                || captureByteSize < 200
                || Objects.equals(responseLastId, requestLastId);
    }

    /** Cursor present and identical to the value sent in the request (pagination did not advance). */
    public static boolean isNonAdvancingStringCursor(String responseLastId, String requestLastId) {
        return responseLastId != null
                && !responseLastId.isEmpty()
                && Objects.equals(responseLastId, requestLastId);
    }
}
