package com.solace.samples.microintegration;

import com.solace.connector.test.resources.resource.ConnectorArgsBuilder;
import com.solace.samples.abc.testextension.container.simple.AbcTestContainerWithConnectedClient;

public class BaseTest {

  private static final String ABC_BASE_URL_CONFIG = "abc.base-url";
  private static final String BINDER_AUTHENTICATION_TYPE_PROPERTY = "abc.authentication.type";
  private static final String BINDER_AUTHENTICATION_BASIC_USER_NAME_PROPERTY = "abc.authentication.username";
  private static final String BINDER_AUTHENTICATION_BASIC_PASSWORD_PROPERTY = "abc.authentication.password";


  void setupBinderConnectionProperties(ConnectorArgsBuilder argsBuilder,
      AbcTestContainerWithConnectedClient testContainer) {
    argsBuilder.put(ABC_BASE_URL_CONFIG, testContainer.getConnectionUrl());
    argsBuilder.put(BINDER_AUTHENTICATION_TYPE_PROPERTY, "basic");
    argsBuilder.put(BINDER_AUTHENTICATION_BASIC_USER_NAME_PROPERTY,
        testContainer.getBasicAuthUsername());
    argsBuilder.put(BINDER_AUTHENTICATION_BASIC_PASSWORD_PROPERTY,
        testContainer.getBasicAuthPassword());
  }
}
