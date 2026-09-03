package com.solace.samples.microintegration;

import com.solace.connector.core.io.provider.ProducerBindingCapabilities;
import com.solace.connector.core.io.provider.ProducerBindingCapabilities.ProducerAckMode;
import com.solace.connector.core.io.provider.ProducerBindingCapabilitiesFactory;
import com.solace.samples.binder.abc.properties.AbcProducerProperties;
import org.springframework.cloud.stream.binder.ExtendedProducerProperties;
import org.springframework.cloud.stream.binder.ProducerProperties;

/**
 * Factory to create {@link ProducerBindingCapabilities} for abc producer bindings.
 */
class AbcProducerBindingCapabilitiesFactory implements ProducerBindingCapabilitiesFactory {

  @Override
  public String getBinderType() {
    return "abc";
  }

  @Override
  public ProducerBindingCapabilities create(ProducerProperties producerProperties) {

    ExtendedProducerProperties<AbcProducerProperties> extendedProducerProperties =
        (ExtendedProducerProperties<AbcProducerProperties>) producerProperties;

    // Determine the acknowledgment mode based on the producer properties.
    // In this example, we check if the producer is configured for asynchronous publishing
    // and set the acknowledgment mode accordingly.
    // To signal synchronous publishing instead, return ProducerAckMode.SYNC.
    // NOTE: If producer does not support async publishing (extended producer properties do not expose async flag) at all
    //       then this factory can simply return ProducerAckMode.SYNC without checking producer properties
    //      If Producer does not support sync publishing at all then this factory can simply return
    //      ProducerAckMode.ASYNC_BY_CALLBACK_HEADER without checking producer properties
    ProducerAckMode producerAckMode = extendedProducerProperties.getExtension().isAsync() ?
        ProducerAckMode.ASYNC_BY_CALLBACK_HEADER : ProducerAckMode.SYNC;

    return new AbcProducerBindingCapabilities(producerProperties.getBindingName(), producerAckMode);
  }

  private static class AbcProducerBindingCapabilities implements ProducerBindingCapabilities {

    private final String bindingName;
    private final ProducerAckMode producerAckMode;

    private AbcProducerBindingCapabilities(String bindingName, ProducerAckMode producerAckMode) {
      this.bindingName = bindingName;
      this.producerAckMode = producerAckMode;
    }

    @Override
    public String getBindingName() {
      return bindingName;
    }

    @Override
    public ProducerAckMode getAcknowledgmentMode() {
      // Indicates that this producer binding publishes messages asynchronously.
      // The MI framework will provide a callback header that allows the binding to
      // signal when the target system has acknowledged each message.
      // To signal synchronous publishing instead, return ProducerAckMode.SYNC.
      return this.producerAckMode;
    }
  }
}
