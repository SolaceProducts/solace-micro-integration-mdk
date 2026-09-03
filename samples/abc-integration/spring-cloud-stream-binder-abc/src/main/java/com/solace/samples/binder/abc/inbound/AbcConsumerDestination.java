package com.solace.samples.binder.abc.inbound;

import org.springframework.cloud.stream.provisioning.ConsumerDestination;

/**
 * Implementation of ConsumerDestination for abc binder.
 */
public record AbcConsumerDestination(String name) implements ConsumerDestination {
    @Override
    public String getName() {
        return name();
    }
}
