package com.solace.samples.microintegration;

import com.solace.connector.test.resources.PubSubPlusExtension;
import com.solace.connector.test.resources.resource.ConnectorArgsBuilder;
import com.solace.connector.test.resources.resource.SolaceMessaging;
import com.solace.connector.test.resources.resource.SolaceQueue;
import com.solace.samples.abc.client.AbcClient.AbcInboundMessage;
import com.solace.samples.abc.testextension.container.simple.AbcSimpleContainerTestExtension;
import com.solace.samples.abc.testextension.container.simple.AbcTestContainerWithConnectedClient;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.Queue;
import com.solacesystems.jcsmp.TextMessage;
import java.util.ArrayList;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;


import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith({PubSubPlusExtension.class, AbcSimpleContainerTestExtension.class})
class BasicProducerMessagingIT extends BaseTest {

  // corresponding to the profile used in test/resources/application-messaging-producer.yml which has no transformations configured for most basic testing.
  private static final String BASIC_TEST_PROFILE = "messaging-producer";
  // corresponding to the profile used in test/resources/application-messaging-producer-with-transforms.yml which has the transformations configured.
  private static final String TRANSFORMATIONS_TEST_PROFILE = "messaging-producer-with-transforms";

  private static final String BATCH_TEST_PROFILE = "messaging-batch-producer";

  private static final String SOURCE_DESTINATION = "source-destination";
  private static final String TARGET_DESTINATION = "target-destination";

  private static final String XML_TEST_MESSAGE_PAYLOAD = """
      <?xml version="1.0" encoding="UTF-8"?>
              <?xml-stylesheet type="text/css" href="style.css"?>
              <products>
                  <?sort-order ascending?>
                  <item id="1">
                      <name>Laptop</name>
                      <price>999</price>
                  </item>
                  <?page-break?>
                  <item id="2">
                      <name>Mouse</name>
                      <price>25</price>
                  </item>
                </products>
      """;

  @BeforeAll
  static void setup(@SolaceQueue(name = "input-0") Queue input0,
      @SolaceQueue(name = "output-1") Queue output1,
      AbcTestContainerWithConnectedClient container) {

    container.getClient().createDestination(SOURCE_DESTINATION);
    container.getClient().createDestination(TARGET_DESTINATION);
  }

  @AfterAll
  static void cleanup(AbcTestContainerWithConnectedClient testContainer) {
    testContainer.getClient().deleteDestination(SOURCE_DESTINATION);
    testContainer.getClient().deleteDestination(TARGET_DESTINATION);
  }

  @AfterEach
  void cleanupAfterEach(AbcTestContainerWithConnectedClient testContainer) {
    testContainer.getClient().deleteAllMessages(SOURCE_DESTINATION);
    testContainer.getClient().deleteAllMessages(TARGET_DESTINATION);
  }

  @Test
  void solaceToAbcMessagingBasicTest(SolaceMessaging solaceMessaging,
      AbcTestContainerWithConnectedClient testContainer,
      ConnectorArgsBuilder argsBuilder) throws JCSMPException {

    argsBuilder.workflowEnable(0, true);
    setupBinderConnectionProperties(argsBuilder, testContainer);

    //Start the connector application
    try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(
        MicroIntegrationApplication.class)
        .profiles(BASIC_TEST_PROFILE)
        .run(argsBuilder.build())) { // Passes properties to the application

      //Produces 1 message to workflow-0's input queue
      String testPayload = "{\"payload\": \"%s\"}".formatted(UUID.randomUUID().toString());
      TextMessage message = JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
      message.setText(testPayload);
      solaceMessaging.produceAsync(1, 0, () -> message);

      //Retrieves the processed message from workflow-0's output queue and assert for correctness
      Awaitility.await()
          .atMost(10, TimeUnit.SECONDS)
          .untilAsserted(() -> {
            Optional<AbcInboundMessage> inboundMessage = testContainer.getClient().pollMessage(
                TARGET_DESTINATION);
            assertThat(inboundMessage)
                .isPresent()
                .satisfies(resp -> assertThat(resp.get().getPayload()).isEqualTo(testPayload));
          });
    }
  }

  @Test
  void solaceToAbcMessagingBatchTest(SolaceMessaging solaceMessaging,
      AbcTestContainerWithConnectedClient testContainer,
      ConnectorArgsBuilder argsBuilder) throws JCSMPException {

    argsBuilder.workflowEnable(0, true);
    setupBinderConnectionProperties(argsBuilder, testContainer);

    //Start the connector application
    try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(
        MicroIntegrationApplication.class)
        .profiles(BATCH_TEST_PROFILE)
        .run(argsBuilder.build())) { // Passes properties to the application

      //Produces 10 messages to workflow-0's input queue, binder configured to use batch mode
      List<String> testPayloads = new ArrayList<>();
      solaceMessaging.produceAsync(10, 0, () -> {
        String payload = "{\"payload\": \"%s\"}".formatted(UUID.randomUUID().toString());
        testPayloads.add(payload);
        TextMessage msg = JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
        msg.setText(payload);
        return msg;
      });

      //Retrieves all 10 processed messages from workflow-0's output queue and assert for correctness
      Awaitility.await()
          .atMost(10, TimeUnit.SECONDS)
          .untilAsserted(() -> {
            List<String> receivedPayloads = new ArrayList<>();
            Optional<AbcInboundMessage> inboundMessage;
            while ((inboundMessage = testContainer.getClient().pollMessage(
                TARGET_DESTINATION)).isPresent()) {
              receivedPayloads.add(inboundMessage.get().getPayload());
            }
            assertThat(receivedPayloads)
                .hasSize(10)
                .containsExactlyInAnyOrderElementsOf(testPayloads);
          });
    }
  }

  @Test
  void solaceToAbcMessagingXml2JsonTransformationTest(SolaceMessaging solaceMessaging,
      AbcTestContainerWithConnectedClient testContainer,
      ConnectorArgsBuilder argsBuilder) throws JCSMPException {

    argsBuilder.workflowEnable(0, true);
    setupBinderConnectionProperties(argsBuilder, testContainer);

    //Start the connector application
    try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(
        MicroIntegrationApplication.class)
        .profiles(TRANSFORMATIONS_TEST_PROFILE)
        .run(argsBuilder.build())) { // Passes properties to the application

      //Produces 1 message to workflow-0's input queue
      TextMessage message = JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
      message.setText(XML_TEST_MESSAGE_PAYLOAD);

      solaceMessaging.produceAsync(1, 0, () -> message);

      // expected payload after transformation in the application
      final String expectedPayload = "{\"static\":\"new-payload-value\",\"product_name_from_payload\":\"Laptop\"}";
      //Retrieves the processed message from workflow-0's output queue and assert for correctness
      Awaitility.await()
          .atMost(10, TimeUnit.SECONDS)
          .untilAsserted(() -> {
            // receives message form the target destination which is the output
            // of workflow-0 and assert the transformation was applied correctly.
            // Target destination is abc service.
            Optional<AbcInboundMessage> inboundMessage = testContainer.getClient().pollMessage(
                TARGET_DESTINATION);
            assertThat(inboundMessage)
                .isPresent()
                .satisfies(resp -> assertThat(resp.get().getPayload())
                    .isEqualTo(expectedPayload));
          });
    }
  }


  @Test
  void testHealthIndicatorsUp(ConnectorArgsBuilder argsBuilder,
      AbcTestContainerWithConnectedClient testContainer) {

    // Start the application with single workflow enabled
    argsBuilder.workflowEnable(0, true);
    setupBinderConnectionProperties(argsBuilder, testContainer);

    try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
        MicroIntegrationApplication.class)
        .profiles(BASIC_TEST_PROFILE)
        .run(argsBuilder.build())) {
      HealthAssertions.assertComponentsUp((WebApplicationContext) context);
    }
  }


}
