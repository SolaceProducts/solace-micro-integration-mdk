package com.solace.samples.abc.testextension.container.simple;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * JUnit 5 extension that starts the ABC service in a container and provides a preconfigured
 * container with a connected client for testing.
 * <p>
 * The container is created once and shared across all test classes that use this extension.
 * It is automatically stopped when the root ExtensionContext store is closed (end of test suite).
 * <p>
 * Usage:
 * <br>1. Add the extension to your test class: @ExtendWith(AbcSimpleContainerTestExtension.class)
 * <br>2. Inject the container wrapper AbcServiceContainerWrapper into your test methods or
 * lifecycle methods to access the client and service URL.
 *
 */
public class AbcSimpleContainerTestExtension implements ParameterResolver, BeforeAllCallback {

  private static final Logger LOGGER = LoggerFactory.getLogger(
      AbcSimpleContainerTestExtension.class);

  public static final ExtensionContext.Namespace EXTENSION_NAMESPACE = ExtensionContext.Namespace.create(
      AbcSimpleContainerTestExtension.class);
  public static final String CONTAINER_KEY = "ABC_CONTAINER_KEY";


  @Override
  public boolean supportsParameter(ParameterContext parameterContext,
      ExtensionContext extensionContext) throws ParameterResolutionException {
    return parameterContext.getParameter().getType()
        .isAssignableFrom(AbcTestContainerWithConnectedClient.class);
  }

  @Override
  public Object resolveParameter(ParameterContext parameterContext,
      ExtensionContext extensionContext) throws ParameterResolutionException {
    Class<?> parameterType = parameterContext.getParameter().getType();
    if (parameterType.isAssignableFrom(AbcTestContainerWithConnectedClient.class)) {
      return getContainer(extensionContext);
    } else {
      throw new ParameterResolutionException(
          "Unsupported parameter type: " + parameterContext.getParameter().getType());
    }
  }

  @Override
  public void beforeAll(ExtensionContext context) {
    // Triggers lazy creation on the first test class; subsequent classes reuse the same container.
    AbcTestContainerWithConnectedClient container = getContainer(context);
    LOGGER.info("Abc service container available on {}", container.getConnectionUrl());
  }


  private AbcTestContainerWithConnectedClient getContainer(ExtensionContext extensionContext) {
    return extensionContext.getRoot().getStore(EXTENSION_NAMESPACE)
        .getOrComputeIfAbsent(CONTAINER_KEY, key -> createContainerResource(),
            SharedContainerResource.class)
        .getContainer();
  }

  private static SharedContainerResource createContainerResource() {
    LOGGER.info("Starting shared abc service container for tests");
    AbcTestContainerWithConnectedClient c = new AbcTestContainerWithConnectedClient();
    c.start();
    LOGGER.info("Shared abc service container started on {}", c.getConnectionUrl());
    return new SharedContainerResource(c);
  }

  /**
   * Wrapper that implements {@link ExtensionContext.Store.CloseableResource} so the container is
   * automatically stopped when the root ExtensionContext store is closed (end of test suite).
   */
  private static class SharedContainerResource
      implements ExtensionContext.Store.CloseableResource {

    private final AbcTestContainerWithConnectedClient container;

    SharedContainerResource(AbcTestContainerWithConnectedClient container) {
      this.container = container;
    }

    AbcTestContainerWithConnectedClient getContainer() {
      return container;
    }

    @Override
    public void close() {
      LOGGER.info("Stopping shared abc service container {}", container.getConnectionUrl());
      container.stop();
      LOGGER.info("Shared abc service container stopped");
    }
  }
}
