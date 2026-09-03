package com.solace.samples.microintegration;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Utility class to assert health status of components and workflows.
 */
public class HealthAssertions {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  public static void assertComponentsUp(WebApplicationContext context) {
    checkHealth(context, "UP");
  }

  public static void assertComponentsDown(WebApplicationContext context) {
    checkHealth(context, "DOWN");
  }

  public static void assertWorkflowsUp(WebApplicationContext context) {
    checkHealthWorkFlows(context, "UP");
  }

  public static void assertWorkFlowsDown(WebApplicationContext context) {
    checkHealthWorkFlows(context, "DOWN");
  }

  private static void checkHealth(WebApplicationContext context, String status) {
    var mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    await("Wait until status is " + status).pollDelay(TIMEOUT).pollInterval(TIMEOUT)
        .atMost(Duration.ofMinutes(1)).untilAsserted(() -> {
          mockMvc.perform(get("/actuator/health"))
              .andDo(print()).
              andDo(result -> {// may be used for manual inspection
              }).andExpectAll(
                  status().isOk(),
                  jsonPath("components.binders.components.solace").exists(),
                  jsonPath("components.binders.components.solace.status").value(status),
                  jsonPath("components.binders.components.abc").exists(),
                  jsonPath("components.binders.components.abc.status").value(status)

              );
        });
  }

  private static void checkHealthWorkFlows(WebApplicationContext context, String status) {
    var mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    await("Wait until status is " + status).pollDelay(TIMEOUT).pollInterval(TIMEOUT)
        .atMost(Duration.ofMinutes(1)).untilAsserted(() -> {
          mockMvc.perform(get("/actuator/health")).andDo(print())
              .andExpectAll(jsonPath("$.status").value(status),
                  jsonPath("$.components.workflows.status").value(status));
        });
  }
}
