package com.solace.samples.queuingservice.exception;

/**
 * Exception thrown when attempting to access a destination that does not exist.
 */
public class DestinationNotFoundException extends RuntimeException {
    
    public DestinationNotFoundException(String message) {
        super(message);
    }
    
    public DestinationNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
