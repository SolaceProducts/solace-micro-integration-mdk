package com.solace.samples.binder.abc.config;

import com.solace.samples.binder.abc.util.IOUtil;
import com.solace.samples.binder.abc.properties.AbcBinderConnectionProperties;
import com.solace.samples.abc.client.AbcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for abc binder health check. Provides health indicators for the abc service
 * connection.
 */
@Configuration
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnEnabledHealthIndicator("binders")
@EnableConfigurationProperties({AbcBinderConnectionProperties.class})
public class AbcBinderHealthConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(
      AbcBinderHealthConfiguration.class);

  /**
   * Creates an instance of AbcClient.
   *  Note: Spring will call close() on the bean during application context shutdown,
   *  which triggers SimpleAbcClient.close() and its executor service teardown.
   * @param connectionProperties The binder properties containing base URL
   * @return The AbcClient instance
   */
  @Bean (destroyMethod = "close")
  AbcClient abcClient(AbcBinderConnectionProperties connectionProperties) {
    return IOUtil.createAbcClient(connectionProperties);
  }


  /**
   * Creates the health indicator for the abc binder. Health indicator checks the health of the
   * connection to the abc service by calling the isHealthy() some sdk have method isConnected() or
   * similar method to check the connection health. If the call is successful, the health status is
   * set to UP. If an exception occurs, the health status is set to DOWN and the exception message
   * is included in the details.
   *
   * @return The health indicator bean
   */
  @Bean
  public HealthIndicator abcBinderHealthIndicator(AbcClient client) {
    return () -> {
      logger.debug("Performing abc binder health check...");
      Health.Builder builder = Health.unknown();

      try {
        client.isHealthy();
        builder.up();
        logger.debug("Abc binder is UP");
      } catch (Exception e) {
        builder.down().withDetail("message", e.getMessage());
        logger.info("Abc binder is DOWN: {}", e.getMessage());
      }

      return builder.build();
    };
  }
}
