package com.solace.samples.binder.abc;

import com.solace.samples.abc.testextension.container.simple.AbcSimpleContainerTestExtension;
import com.solace.samples.abc.testextension.container.simple.AbcTestContainerWithConnectedClient;
import com.solace.samples.abc.client.AbcClient;
import com.solace.samples.binder.abc.app.TestApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Abstract base class for ABC binder tests that provides common functionality:
 * <br>- Managing the AbcClient instance
 * <br>- Creating/deleting destinations
 * <br>- Starting a Spring application context via {@link SpringApplicationBuilder} with
 * connection properties passed as command-line arguments (no system property pollution)
 * <p>
 * Subclasses customize the context by overriding {@link #getApplicationClass()},
 * {@link #getActiveProfiles()}, and {@link #buildConnectionArgs(AbcTestContainerWithConnectedClient)}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(AbcSimpleContainerTestExtension.class)
public abstract class AbstractBaseWithOnTheFlyContainerIT {

  protected static final String SOURCE_DESTINATION = "source-destination";
  protected static final String TARGET_DESTINATION = "target-destination";
  protected static final String DYNAMIC_DESTINATION = "dynamic-destination";

  static final String BINDER_BASE_URL_PROPERTY = "abc.base-url";
  static final String BINDER_AUTHENTICATION_TYPE_PROPERTY = "abc.authentication.type";
  static final String BINDER_AUTHENTICATION_BASIC_USER_NAME_PROPERTY = "abc.authentication.username";
  static final String BINDER_AUTHENTICATION_BASIC_PASSWORD_PROPERTY = "abc.authentication.password";

  private AbcClient simpleAbcClient;
  private ConfigurableApplicationContext applicationContext;

  @BeforeAll
  void beforeAll(AbcTestContainerWithConnectedClient containerWrapper) {
    simpleAbcClient = containerWrapper.getClient();
    createDestination(SOURCE_DESTINATION);
    createDestination(TARGET_DESTINATION);
    createDestination(DYNAMIC_DESTINATION);

    applicationContext = new SpringApplicationBuilder(getApplicationClass())
        .profiles(getActiveProfiles())
        .run(buildConnectionArgs(containerWrapper));
  }

  @AfterEach
  void cleanup() {
    simpleAbcClient.deleteAllMessages(SOURCE_DESTINATION);
    simpleAbcClient.deleteAllMessages(TARGET_DESTINATION);
    simpleAbcClient.deleteAllMessages(DYNAMIC_DESTINATION);
  }

  @AfterAll
  void cleanupAfterAll() {
    if (applicationContext != null) {
      applicationContext.close();
    }
    simpleAbcClient.deleteDestination(SOURCE_DESTINATION);
    simpleAbcClient.deleteDestination(TARGET_DESTINATION);
    simpleAbcClient.deleteDestination(DYNAMIC_DESTINATION);
  }

  /**
   * Returns the Spring Boot application class to use. Default: {@link TestApplication}.
   */
  protected Class<?> getApplicationClass() {
    return TestApplication.class;
  }

  /**
   * Returns active profiles for the Spring application. Default: none.
   */
  protected String[] getActiveProfiles() {
    return new String[0];
  }

  /**
   * Builds the connection arguments passed to {@link SpringApplicationBuilder#run(String...)}.
   * Override in subclasses that need different property prefixes (e.g. multi-binder).
   */
  protected String[] buildConnectionArgs(AbcTestContainerWithConnectedClient containerWrapper) {
    return new String[]{
        "--" + BINDER_BASE_URL_PROPERTY + "=" + containerWrapper.getConnectionUrl(),
        "--" + BINDER_AUTHENTICATION_TYPE_PROPERTY + "=basic",
        "--" + BINDER_AUTHENTICATION_BASIC_USER_NAME_PROPERTY + "=" + containerWrapper.getBasicAuthUsername(),
        "--" + BINDER_AUTHENTICATION_BASIC_PASSWORD_PROPERTY + "=" + containerWrapper.getBasicAuthPassword(),
        "--server.port=0"
    };
  }

  protected ConfigurableApplicationContext getApplicationContext() {
    return applicationContext;
  }

  protected <T> T getBean(Class<T> beanType) {
    return applicationContext.getBean(beanType);
  }

  protected MockMvc getMockMvc() {
    return MockMvcBuilders.webAppContextSetup((WebApplicationContext) applicationContext).build();
  }

  protected void createDestination(String destinationName) {
    simpleAbcClient.createDestination(destinationName);
  }
}
