package com.solace.samples.microintegration;

import com.solace.connector.core.io.provider.PayloadDataType;
import com.solace.connector.core.io.provider.PayloadDataTypePolicy;
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

    /**
     * Declares the payload data-type narrowing policy for this producer binding.
     * The ABC SDK's AbcOutboundMessage.payload is typed as String, so this binder
     * can only produce String or Binary payloads natively.
     *
     * <p>Effect of this policy:
     * <ul>
     *   <li>OBJECT (Map) payloads → narrowed to STRING (framework JSON-serializes via ObjectMapper)</li>
     *   <li>ARRAY (Collection) payloads → narrowed to STRING (framework JSON-serializes via ObjectMapper)</li>
     *   <li>STRING payloads → narrowed to STRING (pass-through, already the correct type)</li>
     *   <li>BINARY (byte[]) payloads → kept as-is (binder handles binary payload separately)</li>
     * </ul>
     */
    @Override
    public PayloadDataTypePolicy payloadDataTypePolicy() {
      return PayloadDataTypePolicy.narrowAllTo(PayloadDataType.STRING)
          .keep(PayloadDataType.BINARY)
          .build();
    }
  }
}
