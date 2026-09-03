package com.solace.samples.abc.testextension;


import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

import com.solace.samples.abc.testextension.container.proxy.AbcProxyTestExtension;
import com.solace.samples.abc.testextension.container.proxy.AbcServiceProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Basic integration test to verify that the AbcProxy injection using AbcProxyTestExtension is
 * working as expected. This test ensures that the proxy injected and client is not null and other
 * methods form proxy return non-null values.
 * <p>
 * This serves as a sanity check for the proxy setup before running more complex tests that depend
 * on it.
 */
@ExtendWith(AbcProxyTestExtension.class)
public class SimpleAbcProxyIT {


  @Test
  void baseUrlShouldNotBeNull(AbcServiceProxy abcServiceProxy) {
    String baseUrl = abcServiceProxy.getConnectionUrl();
    assertThat("Base URL should not be null or empty", baseUrl, is(not(emptyOrNullString())));
  }

  @Test
  void baseUrlShouldStartWithHttp(AbcServiceProxy abcServiceProxy) {
    String baseUrl = abcServiceProxy.getConnectionUrl();
    assertThat("Base URL should be an HTTP URL", baseUrl, startsWith("http://"));
  }

  @Test
  void clientShouldBeInitialized(AbcServiceProxy abcServiceProxy) {
    assertThat("Client should be created and not be null", abcServiceProxy.getClient(),
        is(notNullValue()));
  }

}
