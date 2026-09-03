package com.solace.samples.binder.abc.properties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

/**
 * Connection properties for the abc binder. Contains properties related to connecting to the abc
 * service. Connection Properties Namespace is 'abc'.
 * <br>Nested under environment.abc in the binder
 * configuration
 */
@ConfigurationProperties("abc")
@Validated
public class AbcBinderConnectionProperties {

  @NotNull
  @Pattern(regexp = "^http://[a-zA-Z0-9.-]+:\\d+$", message = "Base URL must be in the format 'http://hostname:port'")
  private String baseUrl;
  private int port;

  private int connectionTimeoutMs = 10_000;
  @NestedConfigurationProperty
  private AuthenticationConfig authentication;

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }


  public AuthenticationConfig getAuthentication() {
    return authentication;
  }

  public void setAuthentication(AuthenticationConfig authentication) {
    this.authentication = authentication;
  }

  public int getConnectionTimeoutMs() {
    return connectionTimeoutMs;
  }

  public void setConnectionTimeoutMs(int connectionTimeoutMs) {
    this.connectionTimeoutMs = connectionTimeoutMs;
  }

}
