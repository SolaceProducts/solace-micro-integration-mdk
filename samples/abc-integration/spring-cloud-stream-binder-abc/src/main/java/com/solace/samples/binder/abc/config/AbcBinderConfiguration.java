package com.solace.samples.binder.abc.config;

import com.solace.samples.binder.abc.AbcBinder;
import com.solace.samples.binder.abc.properties.AbcBinderConnectionProperties;
import com.solace.samples.binder.abc.properties.AbcExtendedBindingProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.stream.binder.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Configuration class for the abc binder. Creates and configures the binder and its
 * dependencies.
 */
@Configuration
@ConditionalOnMissingBean(Binder.class)
@EnableConfigurationProperties({AbcExtendedBindingProperties.class,
    AbcBinderConnectionProperties.class})
@Import(AbcBinderHealthConfiguration.class)
public class AbcBinderConfiguration {

  /**
   * Creates the abc binder.
   *
   * @param connectionProperties      The binder properties
   * @param extendedBindingProperties The extended binding properties
   * @return The abc binder
   */
  @Bean
  AbcBinder abcBinder(AbcBinderConnectionProperties connectionProperties,
      AbcExtendedBindingProperties extendedBindingProperties) {
    return new AbcBinder(connectionProperties, extendedBindingProperties);
  }

}
