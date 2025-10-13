package org.proj4.wkt;

/**
 * Exception thrown when WKT parsing fails.
 */
public class WKTParseException extends Exception {
    
    public WKTParseException(String message) {
        super(message);
    }
    
    public WKTParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
