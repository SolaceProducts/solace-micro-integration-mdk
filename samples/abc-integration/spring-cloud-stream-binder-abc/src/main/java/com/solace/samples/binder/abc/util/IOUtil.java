package com.solace.samples.binder.abc.util;

import com.solace.samples.abc.client.AbcClient;
import com.solace.samples.abc.client.AbcClient.BasicAuthenticationProvider;
import com.solace.samples.binder.abc.properties.AbcBinderConnectionProperties;

/**
 * Utility class for creating instances of AbcClient based on binder connection properties.
 */
public class IOUtil {


  private IOUtil() {
  }

  /**
   * Creates an instance of AbcClient based on the provided connection properties.
   *
   * @param connectionProperties Abc service connection properties
   * @return A configured instance of AbcClient
   */
  public static AbcClient createAbcClient(AbcBinderConnectionProperties connectionProperties) {
    BasicAuthenticationProvider authProvider = null;
    if (connectionProperties.getAuthentication() != null && "basic".equals(
        connectionProperties.getAuthentication().getType())) {
      authProvider = new BasicAuthenticationProvider(
          connectionProperties.getAuthentication().getUsername(),
          connectionProperties.getAuthentication().getPassword());
    }
    // one likely to enforce authentication if credentials are provided,
    // but not strictly required for all use cases
    //    assert authProvider
    //        != null : "Authentication provider must be configured for basic authentication";

    return new AbcClient.AbcClientBuilder().withBaseUrl(
            connectionProperties.getBaseUrl())
        .withConnectionTimeout(connectionProperties.getConnectionTimeoutMs())
        .withAuthenticationProvider(authProvider).build();
  }

}
