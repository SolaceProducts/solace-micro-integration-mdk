package com.solace.samples.queuingservice.controller;

import com.solace.samples.queuingservice.exception.DestinationNotFoundException;
import com.solace.samples.queuingservice.model.Destination;
import com.solace.samples.queuingservice.model.DestinationRequest;
import com.solace.samples.queuingservice.service.DestinationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for destination management endpoints.
 */
@RestController
@RequestMapping("/destinations")
public class DestinationController {

    private final DestinationService destinationService;

    @Autowired
    public DestinationController(DestinationService destinationService) {
        this.destinationService = destinationService;
    }

    /**
     * List all destinations.
     *
     * @return list of all available destinations
     */
    @GetMapping
    public ResponseEntity<List<Destination>> listDestinations() {
        List<Destination> destinations = destinationService.listDestinations();
        return ResponseEntity.ok(destinations);
    }

    /**
     * Create a new destination.
     *
     * @param destinationRequest the request containing destination data
     * @return the created destination
     */
    @PostMapping
    public ResponseEntity<Destination> createDestination(@RequestBody DestinationRequest destinationRequest) {
        Destination destination = destinationService.createDestination(destinationRequest);
        return new ResponseEntity<>(destination, HttpStatus.CREATED);
    }
    
    /**
     * Delete a destination.
     *
     * @param destination the name of the destination to delete
     * @return empty response with appropriate status code
     */
    @DeleteMapping("/{destination}")
    public ResponseEntity<Void> deleteDestination(@PathVariable String destination) {
        boolean deleted = destinationService.deleteDestination(destination);
        if (deleted) {
            return ResponseEntity.noContent().build();
        } else {
            throw new DestinationNotFoundException("Destination not found: " + destination);
        }
    }
}
