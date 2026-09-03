package com.solace.samples.microintegration;

import com.solace.connector.core.customizer.ConsumerBindingMessageInterceptor;
import com.solace.connector.core.customizer.ConsumerBindingMessageInterceptorFactory;
import com.solace.connector.core.properties.ConnectorProperties;
import com.solace.connector.core.service.WorkflowContext;
import org.springframework.cloud.stream.binder.ConsumerProperties;
import org.springframework.core.Ordered;
import org.springframework.lang.Nullable;

/**
 * Example implementation of a ConsumerBindingMessageInterceptorFactory for the abc binder. This
 * factory creates ConsumerBindingMessageInterceptors for abc consumer bindings based on the
 * consumer properties.
 */
public class AbcConsumerBindingMessageInterceptorFactory implements
    ConsumerBindingMessageInterceptorFactory {
//
//  private final ConnectorProperties connectorProperties;
//  private final WorkflowContext workflowContext;

  public AbcConsumerBindingMessageInterceptorFactory(ConnectorProperties connectorProperties,
      WorkflowContext workflowContext) {
//WorkflowContext and ConnectorProperties can be used determining for interceptor creation
// if needed based on the connector properties or to
// provide context to created interceptors if needed
//    this.connectorProperties = connectorProperties;
//    this.workflowContext = workflowContext;
  }

  @Nullable
  @Override
  public ConsumerBindingMessageInterceptor createIfNecessary(String binderType,
      ConsumerProperties consumerProperties) {
    // For demo purposes only does not create any interceptor,
    // but in a real implementation this method could be used to inspect the consumer properties
    // if needed and create a ConsumerBindingMessageInterceptor that adds custom behavior to the consumer binding
    //    if (!"abc".equals(binderType)) {
    //      return null;
    //    }
    //    else {
    //      // could create and return a ConsumerBindingMessageInterceptor here based on the consumer properties if needed
    //    }

    return null;
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
