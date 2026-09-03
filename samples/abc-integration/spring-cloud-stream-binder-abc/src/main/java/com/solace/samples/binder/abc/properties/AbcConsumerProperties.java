package com.solace.samples.binder.abc.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for abc message consumers.
 */
@SuppressWarnings("ConfigurationProperties")
@ConfigurationProperties(AbcExtendedBindingProperties.DEFAULTS_PREFIX + ".consumer")
public class AbcConsumerProperties {

  /**
   * Pulling interval for messages in milliseconds.
   */
  private Long pollingInterval = 5000L;

  public Long getPollingInterval() {
    return pollingInterval;
  }

  public void setPollingInterval(Long pollingInterval) {
    this.pollingInterval = pollingInterval;
  }

}
