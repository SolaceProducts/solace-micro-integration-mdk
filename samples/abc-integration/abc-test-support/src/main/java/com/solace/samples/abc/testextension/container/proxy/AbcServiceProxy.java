package com.solace.samples.abc.testextension.container.proxy;

import com.solace.samples.abc.client.AbcClient;
import com.solace.samples.abc.client.AbcClient.AbcClientBuilder;
import com.solace.samples.abc.client.AbcClient.BasicAuthenticationProvider;
import io.github.cdimascio.dotenv.Dotenv;

/**
 * The AbcServiceProxy class that replaces AbcTestContainer with a real backend.
 * <br>It manages the lifecycle of the AbcClient as well supply connection configuration for the
 * client such as connection URL and authentication credentials.
 * <br>- The Client going to be used in the tests directly for example: to send messages or
 * validate received messages.
 * <br>- The connection configuration details (provided form getter methods like
 * {@code getBasicAuthUsername} ) will be used to connect to in a binder tests and micro-integration
 * tests to configure binding properties in spring configuration.
 * <br> Supplied client is ready to use and can be used to interact with the service, for example
 * to publish messages or create some test resources.
 * <br> The Abc service assumed to be up and running before the tests start, so make sure to start
 * the service before running the tests.
 * <br> IMPORTANT: Needed connection details and credential methods are derived form client sdk
 * capabilities and requirements, so make sure to adjust the implementation of this class according
 * to the client sdk you are using and the connection details required to connect to the service.
 *
 * <p><b>.env file:</b> This class uses <a
 * href="https://github.com/cdimascio/dotenv-java">dotenv-java</a> to load configuration. Create a
 * {@code .env} file in the project root (or module root) with the following variables:
 * <pre>
 * ABC_SERVICE_BASE_URL=http://localhost:8080
 * ABC_SERVICE_BASIC_AUTH_USERNAME=admin
 * ABC_SERVICE_BASIC_AUTH_PASSWORD=changeme
 * </pre>
 * A template {@code .env} file is provided in the {@code abc-test-support/} module root. The
 * {@code .env} file is loaded with {@code ignoreIfMissing()} so the class will not fail if the file
 * is absent — in that case values fall back to system environment variables.
 */
public class AbcServiceProxy implements AutoCloseable {

  // Using dotenv to read configuration from environment variables.
  private final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

  volatile AbcClient client;


  public void start() {
    this.client = createClient();
  }

  @Override
  public void close() {
    if (this.client != null) {
      // Perform any necessary cleanup for the client here
      this.client.close();
    }
  }

  public AbcClient getClient() {
    return client;
  }

  /**
   * Reads the basic authentication username from the {@code ABC_SERVICE_BASIC_AUTH_USERNAME}
   * environment variable (or {@code .env} file).
   *
   * @return username from environment variable
   */
  public String getBasicAuthUsername() {
    return dotenv.get("ABC_SERVICE_BASIC_AUTH_USERNAME");
  }

  /**
   * In this example, we are reading the basic authentication credentials (password) from
   * environment
   *
   * @return password form environment variable or default value if not set.
   */
  public String getBasicAuthPassword() {
    return dotenv.get("ABC_SERVICE_BASIC_AUTH_PASSWORD");
  }

  /**
   * In this example, we are reading the Abc service connection URL from environment variables.
   *
   * @return connection URL form environment variable,
   */
  public String getConnectionUrl() {
    // This particular service is http based
    return dotenv.get("ABC_SERVICE_BASE_URL");
  }


  private AbcClient createClient() {
    // prepare ready to use client with appropriate configuration,
    // for example setting appropriate authentication credentials if needed.
    // in this example, we are using basic authentication and setting the connection URL to connect to the service.
    // Adjust the client configuration as needed based on the client sdk you are using and the connection details required to connect to the service.
    final AbcClientBuilder clientBuilder = AbcClient.builder().withBaseUrl(getConnectionUrl())
        .withConnectionTimeout(30).withReadTimeout(30).withAuthenticationProvider(
            new BasicAuthenticationProvider(getBasicAuthUsername(), getBasicAuthPassword()));
    return clientBuilder.build();
  }


}
