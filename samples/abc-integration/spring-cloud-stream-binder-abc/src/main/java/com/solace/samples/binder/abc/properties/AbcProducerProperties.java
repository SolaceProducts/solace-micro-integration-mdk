package com.solace.samples.binder.abc.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for abc message producers.
 */
@SuppressWarnings("ConfigurationProperties") // only used to generate auto-config metadata file
@ConfigurationProperties(AbcExtendedBindingProperties.DEFAULTS_PREFIX + ".producer")
public class AbcProducerProperties {

  // some binders may support async sending of messages, this is just
  // an example property to signal that to the binder implementation can attempt
  // to send messages asynchronously OR synchronously based on this property.
  // Remove this property if the binder does not support async sending.
  boolean async = false;

  private long writeTimeoutMs = 50_000L;

  /** The timeout for sending messages, in milliseconds. This is just an example property to signal that the
   * binder implementation can attempt to send messages with a timeout based on this property.
   * <br>When the binder does not support timeouts for sending messages, this property is not needed and can be skipped entirely.
   *
   * @return the write timeout in milliseconds
   */
  public long getWriteTimeoutMs() {
    return writeTimeoutMs;
  }

  public void setWriteTimeoutMs(long writeTimeoutMs) {
    this.writeTimeoutMs = writeTimeoutMs;
  }

  /**
   * Whether to send messages asynchronously. This is just an example property to signal that the
   * binder implementation can attempt to send messages asynchronously or synchronously based on
   * this property.
   * <br>Asynchronous sending can provide better performance but may not be supported by
   * all binders, it also requires the consumer binder (i.e. Solace binder) to provide a callback
   * header that allows the binding from this particular binder to signal when the target system has
   * acknowledged each message. Synchronous sending may be simpler but can have lower performance.
   *
   * @param async
   */
  public void setAsync(boolean async) {
    this.async = async;
  }

  /**
   * Whether to send messages asynchronously.
   *
   * @return true if messages should be sent asynchronously, false for synchronous sending. DEFAULT:
   * false (synchronous sending).
   */
  public boolean isAsync() {
    return async;
  }

}
