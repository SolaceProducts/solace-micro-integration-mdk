package com.solace.samples.queuingservice.model;

/**
 * Represents a destination (queue) in the system.
 */
public class Destination {
    private String name;
    private int messageCount;

    public Destination() {
    }

    public Destination(String name, int messageCount) {
        this.name = name;
        this.messageCount = messageCount;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }
}
