package com.solace.samples.queuingservice.model;

/**
 * Data Transfer Object for creating a new destination.
 */
public class DestinationRequest {
    private String name;

    public DestinationRequest() {
    }

    public DestinationRequest(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
