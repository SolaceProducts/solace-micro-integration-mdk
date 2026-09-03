package com.solace.samples.queuingservice.model;

import java.util.Map;
import java.util.UUID;

/**
 * Internal message representation for the queuing system.
 */
public class Message {
    private final String id;
    private final String destination;
    private final Object payload;
    private final Map<String, String> headers;
    private boolean acknowledged;
    private boolean polled;

    public Message(String destination, Object payload, Map<String, String> headers) {
        this.id = UUID.randomUUID().toString();
        this.destination = destination;
        this.payload = payload;
        this.headers = headers;
        this.acknowledged = false;
        this.polled = false;
    }

    public String getId() {
        return id;
    }

    public String getDestination() {
        return destination;
    }

    public Object getPayload() {
        return payload;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public boolean isAcknowledged() {
        return acknowledged;
    }

    public void setAcknowledged(boolean acknowledged) {
        this.acknowledged = acknowledged;
    }

    public boolean isPolled() {
        return polled;
    }

    public void setPolled(boolean polled) {
        this.polled = polled;
    }

    /**
     * Convert to a DTO for external representation
     */
    public MessageResponse toMessageResponse() {
        return new MessageResponse(id, destination, payload, headers);
    }
}
