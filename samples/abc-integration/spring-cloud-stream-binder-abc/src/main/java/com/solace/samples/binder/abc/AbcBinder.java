package com.solace.samples.binder.abc;

import com.solace.samples.binder.abc.inbound.AbcConsumerDestination;
import com.solace.samples.binder.abc.inbound.AbcInboundChannelAdapter;
import com.solace.samples.binder.abc.outbound.AbcOutboundMessageHandler;
import com.solace.samples.binder.abc.outbound.AbcProducerDestination;
import com.solace.samples.binder.abc.properties.AbcBinderConnectionProperties;
import com.solace.samples.binder.abc.properties.AbcConsumerProperties;
import com.solace.samples.binder.abc.properties.AbcExtendedBindingProperties;
import com.solace.samples.binder.abc.properties.AbcProducerProperties;
import com.solace.samples.binder.abc.provisioning.AbcBinderProvisioner;
import org.springframework.cloud.stream.binder.AbstractMessageChannelBinder;
import org.springframework.cloud.stream.binder.BinderSpecificPropertiesProvider;
import org.springframework.cloud.stream.binder.ExtendedConsumerProperties;
import org.springframework.cloud.stream.binder.ExtendedProducerProperties;
import org.springframework.cloud.stream.binder.ExtendedPropertiesBinder;
import org.springframework.cloud.stream.provisioning.ConsumerDestination;
import org.springframework.cloud.stream.provisioning.ProducerDestination;
import org.springframework.integration.core.MessageProducer;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

/**
 * The core {@link org.springframework.cloud.stream.binder.Binder} implementation. Creates producers
 * and consumers for message channels to integrate with the Abc service.
 */
public class AbcBinder extends
    AbstractMessageChannelBinder<ExtendedConsumerProperties<AbcConsumerProperties>,
        ExtendedProducerProperties<AbcProducerProperties>, AbcBinderProvisioner>
    implements
    ExtendedPropertiesBinder<MessageChannel, AbcConsumerProperties, AbcProducerProperties> {

  private final AbcBinderConnectionProperties connectionProperties;
  private final AbcExtendedBindingProperties extendedBindingProperties;


  /**
   * Create a new Abc binder.
   *
   * @param connectionProperties      The abc connection properties
   * @param extendedBindingProperties The extended binding properties
   */
  public AbcBinder(AbcBinderConnectionProperties connectionProperties,
      AbcExtendedBindingProperties extendedBindingProperties) {
    super(null, new AbcBinderProvisioner());
    this.connectionProperties = connectionProperties;
    this.extendedBindingProperties = extendedBindingProperties;
  }

  @Override
  protected MessageHandler createProducerMessageHandler(ProducerDestination destination,
      ExtendedProducerProperties<AbcProducerProperties> producerProperties,
      MessageChannel errorChannel) {
    return new AbcOutboundMessageHandler((AbcProducerDestination) destination,
        errorChannel, producerProperties, connectionProperties);
  }

  @Override
  protected MessageProducer createConsumerEndpoint(ConsumerDestination destination,
      String group,
      ExtendedConsumerProperties<AbcConsumerProperties> consumerProperties) {
    AbcConsumerDestination restDestination = (AbcConsumerDestination) destination;
    AbcInboundChannelAdapter channelAdapter = new AbcInboundChannelAdapter(
        connectionProperties, restDestination, consumerProperties);

    ErrorInfrastructure errorInfrastructure = registerErrorInfrastructure(destination, group,
        consumerProperties);
    channelAdapter.setErrorChannel(errorInfrastructure.getErrorChannel());

    return channelAdapter;
  }

  @Override
  public AbcConsumerProperties getExtendedConsumerProperties(String channelName) {
    return this.extendedBindingProperties.getExtendedConsumerProperties(channelName);
  }

  @Override
  public AbcProducerProperties getExtendedProducerProperties(String channelName) {
    return this.extendedBindingProperties.getExtendedProducerProperties(channelName);
  }

  @Override
  public String getDefaultsPrefix() {
    return this.extendedBindingProperties.getDefaultsPrefix();
  }

  @Override
  public Class<? extends BinderSpecificPropertiesProvider> getExtendedPropertiesEntryClass() {
    return this.extendedBindingProperties.getExtendedPropertiesEntryClass();
  }

  @Override
  public String getBinderIdentity() {
    return "abc-" + super.getBinderIdentity();
  }
}
