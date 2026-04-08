package io.datapulse.sellerops.domain;

/**
 * Maps between DB statuses (shared {@code alert_event.status}) and the
 * mismatch-specific API contract expected by the frontend.
 *
 * <p>DB uses {@code OPEN} for new alerts; the mismatch UI uses {@code ACTIVE}.
 * DB uses {@code RESOLVED} with {@code resolved_reason = 'IGNORED'} for ignored
 * mismatches; the mismatch UI uses a dedicated {@code IGNORED} status.
 */
public final class MismatchStatus {

  public static final String DB_OPEN = "OPEN";
  public static final String API_ACTIVE = "ACTIVE";
  public static final String ACKNOWLEDGED = "ACKNOWLEDGED";
  public static final String RESOLVED = "RESOLVED";
  public static final String AUTO_RESOLVED = "AUTO_RESOLVED";
  public static final String IGNORED = "IGNORED";

  private MismatchStatus() {
  }

  public static String toApi(String dbStatus, String resolvedReason) {
    if (DB_OPEN.equals(dbStatus)) {
      return API_ACTIVE;
    }
    if (RESOLVED.equals(dbStatus) && resolvedReason != null
        && resolvedReason.startsWith(IGNORED)) {
      return IGNORED;
    }
    return dbStatus;
  }

  public static String toDb(String apiStatus) {
    if (API_ACTIVE.equals(apiStatus)) {
      return DB_OPEN;
    }
    if (IGNORED.equals(apiStatus)) {
      return RESOLVED;
    }
    return apiStatus;
  }
}
