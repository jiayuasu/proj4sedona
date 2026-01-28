package org.datasyslab.proj4sedona.defs;

/**
 * Exception thrown when a CRS definition cannot be fetched from a remote source.
 * 
 * <p>This is an unchecked (runtime) exception that indicates one of the following:</p>
 * <ul>
 *   <li>{@link Reason#NOT_FOUND} - The CRS code does not exist on the remote server (HTTP 404)</li>
 *   <li>{@link Reason#NETWORK_ERROR} - A network error occurred (connection failed, timeout, etc.)</li>
 *   <li>{@link Reason#INVALID_RESPONSE} - The server returned an invalid or unparseable response</li>
 * </ul>
 * 
 * <p>Usage:</p>
 * <pre>
 * try {
 *     ProjectionDef def = Defs.get("ESRI:999999");
 * } catch (CRSFetchException e) {
 *     if (e.getReason() == CRSFetchException.Reason.NOT_FOUND) {
 *         // Handle missing CRS code
 *     } else if (e.getReason() == CRSFetchException.Reason.NETWORK_ERROR) {
 *         // Handle network failure
 *     }
 * }
 * </pre>
 */
public class CRSFetchException extends RuntimeException {

    /**
     * The reason why the CRS fetch failed.
     */
    public enum Reason {
        /**
         * The CRS code was not found on the remote server (HTTP 404).
         */
        NOT_FOUND,

        /**
         * A network error occurred while fetching (connection failed, timeout, server error, etc.).
         */
        NETWORK_ERROR,

        /**
         * The server returned an invalid or unparseable response.
         */
        INVALID_RESPONSE
    }

    private final String crsCode;
    private final Reason reason;

    /**
     * Constructs a new CRSFetchException.
     *
     * @param crsCode The CRS code that failed to fetch (e.g., "EPSG:4326", "ESRI:102001")
     * @param reason The reason for the failure
     * @param message A detailed error message
     */
    public CRSFetchException(String crsCode, Reason reason, String message) {
        super(message);
        this.crsCode = crsCode;
        this.reason = reason;
    }

    /**
     * Constructs a new CRSFetchException with a cause.
     *
     * @param crsCode The CRS code that failed to fetch
     * @param reason The reason for the failure
     * @param message A detailed error message
     * @param cause The underlying exception that caused this failure
     */
    public CRSFetchException(String crsCode, Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.crsCode = crsCode;
        this.reason = reason;
    }

    /**
     * Get the CRS code that failed to fetch.
     *
     * @return The CRS code (e.g., "EPSG:4326", "ESRI:102001")
     */
    public String getCrsCode() {
        return crsCode;
    }

    /**
     * Get the reason for the failure.
     *
     * @return The failure reason
     */
    public Reason getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "CRSFetchException{" +
                "crsCode='" + crsCode + '\'' +
                ", reason=" + reason +
                ", message='" + getMessage() + '\'' +
                '}';
    }
}
