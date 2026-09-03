package com.solace.samples.queuingservice.service;

import com.solace.samples.queuingservice.model.MessageRequest;
import com.solace.samples.queuingservice.model.MessageResponse;

import java.util.List;

/**
 * Service interface for managing message operations.
 */
public interface MessageService {

    /**
     * Queue a new message on a specific destination.
     *
     * @param destination the destination to queue the message on
     * @param messageRequest the request containing message data
     * @return the response containing the queued message info
     */
    MessageResponse queueMessage(String destination, MessageRequest messageRequest);

    /**
     * Poll for a message from a specific destination.
     *
     * @param destination the destination to poll from
     * @return the next available message, or null if none are available
     */
    MessageResponse pollMessage(String destination);

    /**
     * Get all messages from a specific destination.
     *
     * @param destination the destination to get messages from
     * @return a list of all messages in the destination
     */
    List<MessageResponse> getAllMessages(String destination);

    /**
     * Delete all messages from a specific destination.
     *
     * @param destination the destination to delete messages from
     * @return the number of messages deleted
     */
    int deleteAllMessages(String destination);

    /**
     * Acknowledge a message has been processed.
     *
     * @param destination the destination the message belongs to
     * @param messageId the unique ID of the message
     * @return the acknowledged message
     */
    MessageResponse acknowledgeMessage(String destination, String messageId);
    
    /**
     * Reject a message; the message is moved to the DLQ destination.
     *
     * @param destination the destination the message belongs to
     * @param messageId the unique ID of the message
     * @return the rejected message
     */
    MessageResponse rejectMessage(String destination, String messageId);
}
