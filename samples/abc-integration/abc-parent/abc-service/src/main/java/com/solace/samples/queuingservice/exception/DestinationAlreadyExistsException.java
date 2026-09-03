package com.solace.samples.queuingservice.exception;

/**
 * Exception thrown when attempting to create a destination that already exists.
 */
public class DestinationAlreadyExistsException extends RuntimeException {
    
    public DestinationAlreadyExistsException(String message) {
        super(message);
    }
    
    public DestinationAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
