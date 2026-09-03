package com.solace.samples.binder.abc.outbound;

import org.springframework.cloud.stream.provisioning.ProducerDestination;

/**
 * Implementation of ProducerDestination for abc binder.
 */
public record AbcProducerDestination(String name) implements ProducerDestination {
    @Override
    public String getName() {
        return name();
    }

    @Override
    public String getNameForPartition(int partition) {
        return name();
    }
}
