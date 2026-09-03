package com.solace.samples.abc.testextension.container.simple;

import com.solace.samples.abc.client.AbcClient;
import com.solace.samples.abc.client.AbcClient.AbcClientBuilder;
import com.solace.samples.abc.client.AbcClient.BasicAuthenticationProvider;
import com.solace.samples.abc.testextension.container.from_binaries.AbcTestContainer;

/**
 * Test container with a connected client. This class extends the basic AbcTestContainer and adds
 * functionality to create and manage a client that can interact with the service running inside the
 * container.
 * <br> For this example, we are assuming (pretending) that AbcTestContainer is a test container
 * class that is provided by community and pulled by maven dependency and is not under our control,
 * so we are extending it to add the client management functionality without modifying the original
 * class.
 * <br> The client is created and initialized after the container starts and is ready to accept
 * connections. The client is configured to connect to the service using the connection URL
 * provided. The client uses test container methods to access the connection URL and credentials to
 * connect to the service.
 * <br> The client is also closed when the container stops to ensure proper cleanup of resources.
 */
public class AbcTestContainerWithConnectedClient extends AbcTestContainer {

  private volatile AbcClient client;

  public AbcTestContainerWithConnectedClient() {
    super();
  }

  @Override
  public void start() {
    super.start();
    // After the container starts and is ready, we can create the client to interact with the service.
    this.client = createClient();
  }


  @Override
  public void stop() {
    // Optionally, you can add any cleanup logic for the client
    // when api requires it when the container stops.
    try {
      if (this.client != null) {
        // Perform any necessary cleanup for the client here
        this.client.close();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    super.stop();

  }


  private AbcClient createClient() {
    // prepare ready to use client with appropriate configuration,
    // for example setting authentication credentials if needed.
    final AbcClientBuilder clientBuilder = AbcClient.builder().withBaseUrl(getConnectionUrl())
        .withConnectionTimeout(30).withReadTimeout(30).withAuthenticationProvider(
            new BasicAuthenticationProvider(getBasicAuthUsername(), getBasicAuthPassword()));
    return clientBuilder.build();
  }

  public AbcClient getClient() {
    return client;
  }

}
