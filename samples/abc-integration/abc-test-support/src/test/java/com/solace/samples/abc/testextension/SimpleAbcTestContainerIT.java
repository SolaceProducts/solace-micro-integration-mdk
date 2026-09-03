package com.solace.samples.abc.testextension;


import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

import com.solace.samples.abc.testextension.container.simple.AbcTestContainerWithConnectedClient;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Basic integration test to verify that the AbcTestContainerWithConnectedClient is working as expected.
 * This test ensures that the container starts successfully, the base URL is correctly set, and the client is initialized.
 * <p>
 * This serves as a sanity check for the test container setup before running more complex tests that depend on it.
 */
@Testcontainers
public class SimpleAbcTestContainerIT {


  @Container
  private static AbcTestContainerWithConnectedClient abcContainer = new AbcTestContainerWithConnectedClient();

  @Test
  void baseUrlShouldNotBeNull() {
    String baseUrl = abcContainer.getConnectionUrl();
    assertThat("Base URL should not be null or empty", baseUrl, is(not(emptyOrNullString())));
  }

  @Test
  void baseUrlShouldStartWithHttp() {
    String baseUrl = abcContainer.getConnectionUrl();
    assertThat("Base URL should be an HTTP URL", baseUrl, startsWith("http://"));
  }

  @Test
  void clientShouldBeInitialized() {
    assertThat("Client should be created after container start", abcContainer.getClient(), is(notNullValue()));
  }

}
