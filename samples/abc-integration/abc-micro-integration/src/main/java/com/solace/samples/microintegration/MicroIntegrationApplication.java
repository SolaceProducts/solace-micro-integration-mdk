package com.solace.samples.microintegration;

import com.solace.connector.core.io.provider.ConsumerBindingCapabilitiesFactory;
import com.solace.connector.core.io.provider.ProducerBindingCapabilitiesFactory;
import com.solace.connector.core.properties.ConnectorProperties;
import com.solace.connector.core.service.WorkflowContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Main application class for the ABC Micro Integration.
 */
@SpringBootApplication
public class MicroIntegrationApplication {

  public static void main(String[] args) {
    SpringApplication.run(MicroIntegrationApplication.class, args);
  }

  @Bean
  public ConsumerBindingCapabilitiesFactory abcConsumerBindingCapabilitiesFactory() {
    return new AbcConsumerBindingCapabilitiesFactory();
  }

  @Bean
  public ProducerBindingCapabilitiesFactory abcProducerBindingCapabilitiesFactory() {
    return new AbcProducerBindingCapabilitiesFactory();
  }

  // Producer interceptors can be used to modify messages before they are sent to the destination.
  @Bean
  public AbcProducerBindingInterceptorFactory abcProducerBindingInterceptor() {
    return new AbcProducerBindingInterceptorFactory();
  }

  // Consumer interceptors can be used to modify messages after they are received from the destination,
  // but before they are passed to the consumer function.
  @Bean
  public AbcConsumerBindingMessageInterceptorFactory abcConsumerBindingMessageInterceptorFactory(
      ConnectorProperties connectorProperties, WorkflowContext workflowContext) {
    return new AbcConsumerBindingMessageInterceptorFactory(connectorProperties, workflowContext);
  }


}
