package com.solace.samples.microintegration;


import com.solace.connector.core.customizer.ProducerBindingMessageInterceptor;
import com.solace.connector.core.customizer.ProducerBindingMessageInterceptorFactory;
import org.springframework.cloud.stream.binder.ProducerProperties;
import org.springframework.core.Ordered;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;

/**
 * Factory for creating Abc producer binding message interceptors.
 */
public class AbcProducerBindingInterceptorFactory implements
    ProducerBindingMessageInterceptorFactory {


  public AbcProducerBindingInterceptorFactory() {
  }

  @Nullable
  @Override
  public ProducerBindingMessageInterceptor createIfNecessary(String binderType,
      ProducerProperties properties) {
    // can null or an interceptor if message needs modification, interceptor can be dependent on binderType or producer properties
    //    if ("abc".equalsIgnoreCase(binderType)) {
    //      return new ExampleInterceptor();
    //    }
    return null;
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }

  /**
   * Example interceptor that can modify messages before they are sent to the binder. Interceptors
   * can be used for a variety of use cases such as adding headers, modifying payloads, etc. This
   * example interceptor does not modify the message and simply returns it as is.
   */
  @SuppressWarnings("unused")
  private static class ExampleInterceptor implements ProducerBindingMessageInterceptor {

    @Override
    public Message<?> before(Message<?> message) {
      // code here has a chance to modify the message before it is sent to the binder
      return message;
    }
  }
}
