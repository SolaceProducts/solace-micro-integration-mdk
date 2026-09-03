package com.solace.samples.queuingservice.model;

import java.util.Map;

/**
 * Data Transfer Object for incoming message requests.
 */
public class MessageRequest {
    private String payload;
    private Map<String, String> headers;

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }
}
