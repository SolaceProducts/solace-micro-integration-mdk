package com.solace.samples.queuingservice.service;

import com.solace.samples.queuingservice.model.Destination;
import com.solace.samples.queuingservice.model.DestinationRequest;

import java.util.List;

/**
 * Service interface for managing destinations.
 */
public interface DestinationService {

    /**
     * List all available destinations.
     *
     * @return a list of all destinations in the system
     */
    List<Destination> listDestinations();

    /**
     * Create a new destination.
     *
     * @param destinationRequest the request containing destination data
     * @return the created destination
     */
    Destination createDestination(DestinationRequest destinationRequest);

    /**
     * Delete a destination.
     *
     * @param name the name of the destination to delete
     * @return true if the destination was deleted, false if it didn't exist
     */
    boolean deleteDestination(String name);

    /**
     * Check if a destination exists.
     *
     * @param name the name of the destination
     * @return true if the destination exists, false otherwise
     */
    boolean destinationExists(String name);

    /**
     * Get the message count for a destination.
     *
     * @param name the name of the destination
     * @return the number of messages in the destination
     */
    int getMessageCount(String name);

    /**
     * Update the message count for a destination.
     *
     * @param name the name of the destination
     * @param delta the change in message count (positive or negative)
     */
    void updateMessageCount(String name, int delta);
}
