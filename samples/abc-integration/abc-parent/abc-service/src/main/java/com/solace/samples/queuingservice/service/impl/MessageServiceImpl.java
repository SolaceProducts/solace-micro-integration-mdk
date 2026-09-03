package com.solace.samples.queuingservice.service.impl;

import com.solace.samples.queuingservice.exception.DestinationNotFoundException;
import com.solace.samples.queuingservice.exception.MessageNotFoundException;
import com.solace.samples.queuingservice.model.DestinationRequest;
import com.solace.samples.queuingservice.model.Message;
import com.solace.samples.queuingservice.model.MessageRequest;
import com.solace.samples.queuingservice.model.MessageResponse;
import com.solace.samples.queuingservice.service.DestinationService;
import com.solace.samples.queuingservice.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Implementation of MessageService that uses BlockingQueue to store messages.
 */
@Service
public class MessageServiceImpl implements MessageService {

  private static final Logger logger = LoggerFactory.getLogger(MessageServiceImpl.class);
  private static final String DLQ_DESTINATION = "DLQ"; // Dead Letter Queue destination name

  private final Map<String, BlockingQueue<Message>> messageQueues = new ConcurrentHashMap<>();
  private final Map<String, Map<String, Message>> messageRegistry = new ConcurrentHashMap<>();

  private final DestinationService destinationService;

  @Autowired
  public MessageServiceImpl(DestinationService destinationService) {
    this.destinationService = destinationService;
  }

  @Override
  public synchronized MessageResponse queueMessage(String destination,
      MessageRequest messageRequest) {
    logger.info("Received message to destination: {}", destination);
    ensureDestinationExists(destination);

    // Create the message
    Message message = new Message(destination, messageRequest.getPayload(),
        messageRequest.getHeaders());

    // Get or create the queue for this destination
    BlockingQueue<Message> queue = messageQueues.computeIfAbsent(destination,
        k -> new LinkedBlockingQueue<>());

    // Get or create the message registry for this destination for acking purposes
    Map<String, Message> registry = messageRegistry.computeIfAbsent(destination,
        k -> new ConcurrentHashMap<>());

    // Add to the queue and registry
    queue.add(message);
    registry.put(message.getId(), message);

    // Update the destination count
    destinationService.updateMessageCount(destination, 1);

    logger.info("Message queued successfully with ID: {} to destination: {}", message.getId(),
        destination);
    return message.toMessageResponse();
  }

  @Override
  public synchronized MessageResponse pollMessage(String destination) {
    logger.info("Polling message from destination: {}", destination);
    ensureDestinationExists(destination);

    BlockingQueue<Message> queue = messageQueues.get(destination);
    if (queue == null || queue.isEmpty()) {
      logger.debug("No messages available in destination: {}", destination);
      return null;
    }

    // Find the first non-polled message
    Message message = queue.poll();
    if (message == null) {
      return null;
    }

    // Mark as polled
    message.setPolled(true);

    logger.info("Message with ID: {} polled from destination: {}", message.getId(), destination);
    return message.toMessageResponse();
  }

  @Override
  public synchronized List<MessageResponse> getAllMessages(String destination) {
    logger.info("Getting all messages for destination: {}", destination);
    ensureDlqExists();
    ensureDestinationExists(destination);

    Map<String, Message> registry = messageRegistry.get(destination);
    if (registry == null) {
      logger.debug("No message registry found for destination: {}", destination);
      return new ArrayList<>();
    }

    List<MessageResponse> messages = registry.values().stream()
        .map(Message::toMessageResponse)
        .toList();
    logger.info("Retrieved {} messages from destination: {}", messages.size(), destination);
    return messages;
  }

  @Override
  public synchronized MessageResponse acknowledgeMessage(String destination, String messageId) {
    logger.info("Acknowledging message with ID: {} in destination: {}", messageId, destination);
    ensureDestinationExists(destination);

    // Get the registry for this destination
    Map<String, Message> registry = messageRegistry.get(destination);
    if (registry == null) {
      logger.warn("Message registry not found for destination: {}", destination);
      throw new MessageNotFoundException("Message not found with ID: " + messageId);
    }

    // Get the message
    Message message = registry.get(messageId);
    if (message == null) {
      logger.warn("Message with ID {} not found", messageId);
      throw new MessageNotFoundException("Message not found with ID: " + messageId);
    }

    // Mark as acknowledged
    message.setAcknowledged(true);

    // Remove from registry
    registry.remove(messageId);

    // Update destination count
    destinationService.updateMessageCount(destination, -1);

    logger.info("Message with ID: {} successfully acknowledged and removed from destination: {}",
        messageId, destination);

    return message.toMessageResponse();
  }

  @Override
  public int deleteAllMessages(String destination) {
    logger.info("Deleting all messages from destination: {}", destination);
    ensureDestinationExists(destination);

    // Get the registry for this destination
    Map<String, Message> registry = messageRegistry.get(destination);
    if (registry == null) {
      logger.debug("No message registry found for destination: {}", destination);
      return 0;
    }

    // Get current message count
    int messageCount = registry.size();

    if (messageCount > 0) {
      // Clear the queue and registry
      BlockingQueue<Message> queue = messageQueues.get(destination);
      if (queue != null) {
        queue.clear();
      }
      registry.clear();

      // Reset the destination message count
      destinationService.updateMessageCount(destination, -messageCount);

      logger.info("Deleted {} messages from destination: {}", messageCount, destination);
    }

    return messageCount;
  }

  @Override
  public MessageResponse rejectMessage(String destination, String messageId) {
    logger.info("Rejecting message with ID: {} in destination: {}", messageId, destination);
    ensureDestinationExists(destination);

    // Get the registry for this destination
    Map<String, Message> registry = messageRegistry.get(destination);
    if (registry == null) {
      logger.warn("Message registry not found for destination: {}", destination);
      throw new MessageNotFoundException("Message not found with ID: " + messageId);
    }

    // Get the message
    Message message = registry.get(messageId);
    if (message == null) {
      throw new MessageNotFoundException("Message not found with ID: " + messageId);
    }

    // Remove from original registry and queue
    registry.remove(messageId);
    messageQueues.get(destination).remove(message);

    // Update destination count
    destinationService.updateMessageCount(destination, -1);

    // Create headers with rejection information
    Map<String, String> rejectedHeaders = new HashMap<>();
    if (message.getHeaders() != null) {
      rejectedHeaders.putAll(message.getHeaders());
    }
    rejectedHeaders.put("x-original-destination", destination);
    rejectedHeaders.put("x-rejection-timestamp", String.valueOf(System.currentTimeMillis()));

    // Ensure DLQ exists
    ensureDlqExists();

    // Add to DLQ queue and registry
    messageQueues.get(DLQ_DESTINATION).add(message);
    messageRegistry.get(DLQ_DESTINATION).put(message.getId(), message);

    // Update DLQ message count
    destinationService.updateMessageCount(DLQ_DESTINATION, 1);

    logger.info("Message with ID: {} successfully rejected and moved to DLQ from destination: {}",
        messageId, destination);
    return message.toMessageResponse();
  }

  private void ensureDestinationExists(String destination) {
    if (!destinationService.destinationExists(destination)) {
      logger.warn("Destination not found: {}", destination);
      throw new DestinationNotFoundException("Destination not found: " + destination);
    }
  }

  private void ensureDlqExists() {
    if (!destinationService.destinationExists(DLQ_DESTINATION)) {
      try {
        DestinationRequest request = new DestinationRequest(DLQ_DESTINATION);
        destinationService.createDestination(request);
        // Initialize queue and registry for DLQ
        messageQueues.computeIfAbsent(DLQ_DESTINATION, k -> new LinkedBlockingQueue<>());
        messageRegistry.computeIfAbsent(DLQ_DESTINATION, k -> new ConcurrentHashMap<>());
      } catch (Exception e) {
        // If another thread already created it, that's fine
        logger.debug("Exception creating DLQ, it may already exist: {}", e.getMessage());
      }
    }
  }



}
