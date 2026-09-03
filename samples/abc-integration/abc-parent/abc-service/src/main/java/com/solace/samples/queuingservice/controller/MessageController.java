package com.solace.samples.queuingservice.controller;

import com.solace.samples.queuingservice.model.MessageRequest;
import com.solace.samples.queuingservice.model.MessageResponse;
import com.solace.samples.queuingservice.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for message handling endpoints.
 */
@RestController
@RequestMapping("/destinations/{destination}/messages")
public class MessageController {

    private final MessageService messageService;

    @Autowired
    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * Queue a new message to a destination.
     *
     * @param destination the destination to queue the message to
     * @param messageRequest the message to queue
     * @return the queued message
     */
    @PostMapping
    public ResponseEntity<MessageResponse> queueMessage(
            @PathVariable String destination,
            @RequestBody MessageRequest messageRequest) {
        MessageResponse response = messageService.queueMessage(destination, messageRequest);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Poll for a message from a destination.
     *
     * @param destination the destination to poll from
     * @return the next available message, or no content if none available
     */
    @GetMapping
    public ResponseEntity<MessageResponse> pollMessage(@PathVariable String destination) {
        MessageResponse message = messageService.pollMessage(destination);
        if (message == null) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
        return ResponseEntity.ok(message);
    }

    /**
     * Get all messages from a destination.
     *
     * @param destination the destination to get messages from
     * @return list of all messages in the destination
     */
    @GetMapping("/all")
    public ResponseEntity<List<MessageResponse>> getAllMessages(@PathVariable String destination) {
        List<MessageResponse> messages = messageService.getAllMessages(destination);
        if (messages.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
        return ResponseEntity.ok(messages);
    }

    /**
     * Acknowledge a message as processed.
     *
     * @param destination the destination the message belongs to
     * @param id the unique ID of the message
     * @return the acknowledged message
     */
    @PostMapping("/{id}/ack")
    public ResponseEntity<MessageResponse> acknowledgeMessage(
            @PathVariable String destination,
            @PathVariable String id) {
        MessageResponse message = messageService.acknowledgeMessage(destination, id);
        return ResponseEntity.ok(message);
    }

    /**
     * Reject a message and move it to the DLQ.
     *
     * @param destination the destination the message belongs to
     * @param id the unique ID of the message
     * @return the acknowledged message
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<MessageResponse> rejectMessage(
            @PathVariable String destination,
            @PathVariable String id) {
        MessageResponse message = messageService.rejectMessage(destination, id);
        return ResponseEntity.ok(message);
    }
    
    /**
     * Delete all messages from a destination.
     *
     * @param destination the destination to delete messages from
     * @return the number of messages deleted
     */
    @DeleteMapping("/all")
    public ResponseEntity<Object> deleteAllMessages(@PathVariable String destination) {
        int count = messageService.deleteAllMessages(destination);
        
        if (count > 0) {
            return ResponseEntity.ok().body(Map.of("count", count));
        } else {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
    }
}
