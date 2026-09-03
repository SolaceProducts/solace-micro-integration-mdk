package com.solace.samples.queuingservice.model;

import java.util.Map;

/**
 * Data Transfer Object for message responses.
 */
public class MessageResponse {
    private String id;
    private String destination;
    private Object payload;
    private Map<String, String> headers;

    public MessageResponse() {
    }

    public MessageResponse(String id, String destination, Object payload, Map<String, String> headers) {
        this.id = id;
        this.destination = destination;
        this.payload = payload;
        this.headers = headers;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }
}
