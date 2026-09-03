package com.solace.samples.binder.abc.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.stream.binder.AbstractExtendedBindingProperties;
import org.springframework.cloud.stream.binder.BinderSpecificPropertiesProvider;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

/**
 * Extended binding properties for abc binder. Groups abc binder specific producer/consumer
 * properties per-binding
 */
@ConfigurationProperties("spring.cloud.stream.abc")
@Validated
public class AbcExtendedBindingProperties extends
    AbstractExtendedBindingProperties<
        AbcConsumerProperties,
        AbcProducerProperties,
        AbcBindingProperties> {

  protected static final String DEFAULTS_PREFIX = "spring.cloud.stream.abc.default";

  @Override
  public String getDefaultsPrefix() {
    return DEFAULTS_PREFIX;
  }

  @Override
  public Map<String, AbcBindingProperties> getBindings() {
    return super.doGetBindings();
  }

  @Override
  public Class<? extends BinderSpecificPropertiesProvider> getExtendedPropertiesEntryClass() {
    return AbcBindingProperties.class;
  }
}
