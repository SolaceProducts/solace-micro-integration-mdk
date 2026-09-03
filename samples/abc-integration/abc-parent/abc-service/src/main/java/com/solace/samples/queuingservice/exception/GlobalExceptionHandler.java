package com.solace.samples.queuingservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler to handle application-specific exceptions.
 */
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final String MESSAGE_KEY = "message";
  private static final String ERROR_KEY = "error";

    @ExceptionHandler(DestinationNotFoundException.class)
  public ResponseEntity<Object> handleDestinationNotFoundException(
      DestinationNotFoundException ex) {
        Map<String, String> body = new HashMap<>();
    body.put(MESSAGE_KEY, ex.getMessage());
    body.put(ERROR_KEY, "Destination Not Found");
        
        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(MessageNotFoundException.class)
    public ResponseEntity<Object> handleMessageNotFoundException(MessageNotFoundException ex) {
        Map<String, String> body = new HashMap<>();
    body.put(MESSAGE_KEY, ex.getMessage());
    body.put(ERROR_KEY, "Message Not Found");
        
        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(DestinationAlreadyExistsException.class)
  public ResponseEntity<Object> handleDestinationAlreadyExistsException(
      DestinationAlreadyExistsException ex) {
        Map<String, String> body = new HashMap<>();
    body.put(MESSAGE_KEY, ex.getMessage());
    body.put(ERROR_KEY, "Destination Already Exists");
        
        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGenericException(Exception ex) {
        Map<String, String> body = new HashMap<>();
    body.put(MESSAGE_KEY
        , ex.getMessage());
    body.put(ERROR_KEY, "Internal Server Error");
        
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
