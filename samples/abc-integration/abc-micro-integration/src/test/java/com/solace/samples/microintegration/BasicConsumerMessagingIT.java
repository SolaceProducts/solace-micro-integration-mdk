package com.solace.samples.microintegration;

import static org.assertj.core.api.Assertions.assertThat;

import com.solace.connector.test.resources.PubSubPlusExtension;
import com.solace.connector.test.resources.resource.ConnectorArgsBuilder;
import com.solace.connector.test.resources.resource.SolaceMessaging;
import com.solace.connector.test.resources.resource.SolaceQueue;
import com.solace.samples.abc.client.AbcClient.AbcOutboundMessage;
import com.solace.samples.abc.testextension.container.simple.AbcSimpleContainerTestExtension;
import com.solace.samples.abc.testextension.container.simple.AbcTestContainerWithConnectedClient;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.Queue;
import com.solacesystems.jcsmp.TextMessage;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.context.WebApplicationContext;
/**
 * Integration tests for basic messaging functionality of the application using the Abc service and Solace event broker messaging. These tests
 * create an application context and an instance of the abc service running in a container.
 * <p>
 * The tests in this class cover basic end-to-end messaging scenarios for consuming from Abc service and publishing to Solace, with and without transformations.
 */
@ExtendWith({PubSubPlusExtension.class, AbcSimpleContainerTestExtension.class})
class BasicConsumerMessagingIT extends BaseTest {

  // corresponding to the profile used in test/resources/application-messaging-consumer.yml which has no transformations configured for most basic testing.
  private static final String BASIC_TEST_PROFILE = "messaging-consumer";
  // corresponding to the profile used in test/resources/application-messaging-consumer-with-transforms.yml which has the transformations configured.
  private static final String TRANSFORMATIONS_TEST_PROFILE = "messaging-consumer-with-transforms";

  private static final String SOURCE_DESTINATION = "source-destination";

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
  static void setup(@SolaceQueue(name = "output-0") Queue output0,
      AbcTestContainerWithConnectedClient container) {
    // abc consumer use case, creating the source destination/queue in abc to consume messages from in the tests
    container.getClient().createDestination(SOURCE_DESTINATION);
  }

  @AfterAll
  static void cleanup(AbcTestContainerWithConnectedClient testContainer) {
    // abc consumer use case
    testContainer.getClient().deleteDestination(SOURCE_DESTINATION);
  }

  @AfterEach
  void cleanupAfterEach(AbcTestContainerWithConnectedClient testContainer) {
    // abc consumer use case
    testContainer.getClient().deleteAllMessages(SOURCE_DESTINATION);
  }


  @Test
  void abcToSolaceMessagingBasicTest(SolaceMessaging solaceMessaging,
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
      Message<String> message = MessageBuilder.withPayload(testPayload)
          .build();
      testContainer.getClient().publishMessage(SOURCE_DESTINATION,
          new AbcOutboundMessage(testPayload, message.getHeaders()));

      //Retrieves the processed message from workflow-0's output queue and assert for correctness
      solaceMessaging.consumeAndAssert(1, 0, msg -> {
        assertThat(msg).isInstanceOf(TextMessage.class);
        TextMessage textMessage = (TextMessage) msg;
        assertThat(textMessage.getText()).isEqualTo(testPayload);
      });
    }
  }

  @Test
  void abcToSolaceMessagingXml2JsonTransformationTest(SolaceMessaging solaceMessaging,
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
      Map<String, Object> noHeaders = Map.of();
      testContainer.getClient().publishMessage(SOURCE_DESTINATION,
          new AbcOutboundMessage(XML_TEST_MESSAGE_PAYLOAD, noHeaders));
      // expected payload after transformation in the application
      final String expectedPayload = "{\"static\":\"new-payload-value\",\"product_name_from_payload\":\"Laptop\"}";
      //Retrieves the processed message from workflow-0's output queue and assert for correctness
      solaceMessaging.consumeAndAssert(1, 0, msg -> {
        assertThat(msg).isInstanceOf(TextMessage.class);
        TextMessage textMessage = (TextMessage) msg;
        assertThat(textMessage.getText()).isEqualTo(expectedPayload);
      });
    }
  }


  @Test
  void testHealthIndicatorsUp(ConnectorArgsBuilder argsBuilder,
      AbcTestContainerWithConnectedClient testContainer) {

    // Start the application with single workflow enabled also set ABC base URL
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
