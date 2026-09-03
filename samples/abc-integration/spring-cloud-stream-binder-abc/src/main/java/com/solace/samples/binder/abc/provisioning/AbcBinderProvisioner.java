package com.solace.samples.binder.abc.provisioning;

import com.solace.samples.binder.abc.inbound.AbcConsumerDestination;
import com.solace.samples.binder.abc.outbound.AbcProducerDestination;
import com.solace.samples.binder.abc.properties.AbcConsumerProperties;
import com.solace.samples.binder.abc.properties.AbcProducerProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.binder.ExtendedConsumerProperties;
import org.springframework.cloud.stream.binder.ExtendedProducerProperties;
import org.springframework.cloud.stream.provisioning.ConsumerDestination;
import org.springframework.cloud.stream.provisioning.ProducerDestination;
import org.springframework.cloud.stream.provisioning.ProvisioningException;
import org.springframework.cloud.stream.provisioning.ProvisioningProvider;

/**
 * Provisioner for creating consumer and producer destinations.
 * <br>Note: this is a very basic implementation that just creates destinations based on the name.
 * In a real implementation, this would likely involve creating queues/topics in the Abc service and
 * returning destinations that reference those resources.
 */
public class AbcBinderProvisioner implements
    ProvisioningProvider<ExtendedConsumerProperties<AbcConsumerProperties>,
        ExtendedProducerProperties<AbcProducerProperties>> {

  private static final Logger logger = LoggerFactory.getLogger(AbcBinderProvisioner.class);


  @Override
  public ProducerDestination provisionProducerDestination(String name,
      ExtendedProducerProperties<AbcProducerProperties> properties) throws ProvisioningException {
    logger.info("Creating producer destination: {}", name);

    return new AbcProducerDestination(name);
  }

  @Override
  public ConsumerDestination provisionConsumerDestination(String name,

      String group,
      ExtendedConsumerProperties<AbcConsumerProperties> properties) throws ProvisioningException {
    logger.info("Creating consumer destination: {} group {}", name, group);

    return new AbcConsumerDestination(name);
  }

}
