package com.solace.samples.queuingservice.exception;

/**
 * Exception thrown when attempting to access a message that does not exist.
 */
public class MessageNotFoundException extends RuntimeException {
    
    public MessageNotFoundException(String message) {
        super(message);
    }
    
    public MessageNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
