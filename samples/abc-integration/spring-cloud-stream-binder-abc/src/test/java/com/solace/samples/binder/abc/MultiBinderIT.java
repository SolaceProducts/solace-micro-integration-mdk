package com.solace.samples.binder.abc;

import com.solace.samples.abc.testextension.container.simple.AbcTestContainerWithConnectedClient;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test to verify that multiple binders can be configured and are healthy.
 * Uses the "multibinder" profile to load the appropriate configuration,
 * 'application-multibinder.yml' in this case.
 */
class MultiBinderIT extends AbstractBaseWithOnTheFlyContainerIT {

  @Override
  protected String[] getActiveProfiles() {
    return new String[]{"multibinder"};
  }

  @Override
  protected String[] buildConnectionArgs(AbcTestContainerWithConnectedClient containerWrapper) {
    String connectionUrl = containerWrapper.getConnectionUrl();
    String username = containerWrapper.getBasicAuthUsername();
    String password = containerWrapper.getBasicAuthPassword();

    List<String> args = new ArrayList<>();

    // abc-1 binder environment properties
    String abc1Prefix = "spring.cloud.stream.binders.abc-1.environment.";
    args.add("--" + abc1Prefix + BINDER_BASE_URL_PROPERTY + "=" + connectionUrl);
    args.add("--" + abc1Prefix + BINDER_AUTHENTICATION_TYPE_PROPERTY + "=basic");
    args.add("--" + abc1Prefix + BINDER_AUTHENTICATION_BASIC_USER_NAME_PROPERTY + "=" + username);
    args.add("--" + abc1Prefix + BINDER_AUTHENTICATION_BASIC_PASSWORD_PROPERTY + "=" + password);

    // abc-2 binder environment properties
    String abc2Prefix = "spring.cloud.stream.binders.abc-2.environment.";
    args.add("--" + abc2Prefix + BINDER_BASE_URL_PROPERTY + "=" + connectionUrl);
    args.add("--" + abc2Prefix + BINDER_AUTHENTICATION_TYPE_PROPERTY + "=basic");
    args.add("--" + abc2Prefix + BINDER_AUTHENTICATION_BASIC_USER_NAME_PROPERTY + "=" + username);
    args.add("--" + abc2Prefix + BINDER_AUTHENTICATION_BASIC_PASSWORD_PROPERTY + "=" + password);

    args.add("--server.port=0");
    return args.toArray(new String[0]);
  }

  @Test
  void checkHealthWithMultipleBinders() throws Exception {
    getMockMvc().perform(get("/actuator/health"))
        .andExpectAll(
            status().isOk(),
            jsonPath("components.binders.components.abc-1").exists(),
            jsonPath("components.binders.components.abc-1.status").value("UP"),
            jsonPath("components.binders.components.abc-2").exists(),
            jsonPath("components.binders.components.abc-2.status").value("UP")
        );
  }
}
