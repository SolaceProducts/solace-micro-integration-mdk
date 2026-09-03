package com.solace.samples.abc.testextension.container.proxy;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * JUnit 5 extension that manages the lifecycle of the Abc service proxy for integration tests. It
 * provides the proxy instance to test methods via parameter injection and ensures that the client
 * to the real service is properly configured at the beginning of the tests and cleaned up at the
 * end. To use this extension, simply annotate your test class with
 * {@code @ExtendWith(AbcProxyTestExtension.class)} and include a parameter of type
 * {@code AbcServiceProxy} in your test methods for example void myTest(AbcServiceProxy
 * serviceProxy) or lifecycle methods where you want to access the proxy. The extension will take
 * care of starting the proxy before all tests, injecting it into test methods, and closing it after
 * all tests have completed.
 *
 */
public class AbcProxyTestExtension implements ParameterResolver, BeforeAllCallback,
    AfterAllCallback {

  private static final Logger LOGGER = LoggerFactory.getLogger(
      AbcProxyTestExtension.class);

  public static final ExtensionContext.Namespace EXTENSION_NAMESPACE = ExtensionContext.Namespace.create(
      AbcProxyTestExtension.class);
  public static final String PROXY_KEY = "ABC_PROXY_KEY";


  @Override
  public boolean supportsParameter(ParameterContext parameterContext,
      ExtensionContext extensionContext) throws ParameterResolutionException {
    return parameterContext.getParameter().getType()
        .isAssignableFrom(AbcServiceProxy.class);
  }

  @Override
  public Object resolveParameter(ParameterContext parameterContext,
      ExtensionContext extensionContext) throws ParameterResolutionException {
    Class<?> parameterType = parameterContext.getParameter().getType();
    if (parameterType.isAssignableFrom(AbcServiceProxy.class)) {
      return getProxy(extensionContext);
    } else {
      throw new ParameterResolutionException(
          "Unsupported parameter type: " + parameterContext.getParameter().getType());
    }
  }


  private AbcServiceProxy getProxy(ExtensionContext extensionContext) {
    // Using extension context root since the client has the same lifecycle as the proxy.
    return extensionContext.getRoot().getStore(EXTENSION_NAMESPACE)
        .getOrComputeIfAbsent(PROXY_KEY, key -> createProxy(),
            AbcServiceProxy.class);
  }


  private static AbcServiceProxy createProxy() {
    LOGGER.info("Creating proxy instance ...");
    AbcServiceProxy proxy = new AbcServiceProxy();
    proxy.start();
    LOGGER.info("Abc proxy is initialized and ready to use");
    return proxy;

  }


  @Override
  public void afterAll(ExtensionContext context) {
    AbcServiceProxy c = context.getRoot().getStore(EXTENSION_NAMESPACE)
        .getOrDefault(PROXY_KEY, AbcServiceProxy.class, null);
    if (c != null) {
      LOGGER.info("Stopping abc service proxy {} ", c.getConnectionUrl());
      c.close();
      LOGGER.info("Abc service proxy closed successfully");
    }
  }

  @Override
  public void beforeAll(ExtensionContext context) {
    AbcServiceProxy proxy = createProxy();
    LOGGER.info(
        "Abc service proxy started {}", proxy.getConnectionUrl());
    context.getRoot().getStore(EXTENSION_NAMESPACE)
        .put(PROXY_KEY, proxy);
  }
}
