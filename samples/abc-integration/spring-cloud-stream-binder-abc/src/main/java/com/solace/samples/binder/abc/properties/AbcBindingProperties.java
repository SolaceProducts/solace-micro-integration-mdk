package com.solace.samples.binder.abc.properties;

import org.springframework.cloud.stream.binder.BinderSpecificPropertiesProvider;

public class AbcBindingProperties implements BinderSpecificPropertiesProvider {

    private AbcConsumerProperties consumer = new AbcConsumerProperties();

    private AbcProducerProperties producer = new AbcProducerProperties();

    @Override
    public Object getConsumer() {
        return consumer;
    }

    public void setConsumer(AbcConsumerProperties consumer) {
        this.consumer = consumer;
    }

    @Override
    public Object getProducer() {
        return producer;
    }

    public void setProducer(AbcProducerProperties producer) {
        this.producer = producer;
    }
}
