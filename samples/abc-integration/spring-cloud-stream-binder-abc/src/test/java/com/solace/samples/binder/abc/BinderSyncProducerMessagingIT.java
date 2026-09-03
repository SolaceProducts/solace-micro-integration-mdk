package com.solace.samples.binder.abc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.solace.connector.core.io.header.ConnectorBinderHeaders;
import com.solace.connector.core.io.outbound.PublishAcknowledgmentCallback;
import com.solace.samples.abc.client.AbcClient.AbcInboundMessage;
import com.solace.samples.abc.testextension.container.simple.AbcTestContainerWithConnectedClient;
import com.solace.samples.binder.abc.app.TestApplication;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.stream.binder.BinderHeaders;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.messaging.support.MessageBuilder;

/**
 * Binder integration tests that use a TestApplication to test the synchronous blocking producer
 * bindings. These tests create an application context and an instance of the abc service running in
 * a container.
 * <p>
 */
@ExtendWith(MockitoExtension.class)
@ExtendWith(OutputCaptureExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BinderSyncProducerMessagingIT extends AbstractBaseWithOnTheFlyContainerIT {

  private TestApplication application;
  private StreamBridge streamBridge;

  private final String outputBindingName = "testSupplier-out-0";

  @BeforeEach
  void testCleanup() {
    application = getBean(TestApplication.class);
    streamBridge = getBean(StreamBridge.class);
    application.clearReceivedMessages();
    application.clearErrorChannelMessages();
  }

  @Override
  protected String[] getActiveProfiles() {
    return new String[]{"sync-producer"};
  }

  /**
   * Test that the producer binding can successfully push messages in a synchronous manner to the
   * Abc service.
   * <p>
   * A spring message is sent to the producer binding which is expected to convert and publish it to
   * the Abc service. The test retrieves the message from the Abc service to confirm the message was
   * successfully published.
   */
  @Test
  @Order(1)
  void testProducerBindingSuccessfulPublish(AbcTestContainerWithConnectedClient containerWrapper) {
    String testPayload = "{\"payload\": \"%s\"}".formatted(UUID.randomUUID().toString());
    Message<String> testMessage = MessageBuilder.withPayload(testPayload)
        .setHeader("test-header", "abcd")
        .build();

    // Send the message through the StreamBridge to the producer binding
    boolean sent = streamBridge.send(outputBindingName, testMessage);
    assertTrue(sent, "Message should have been accepted by StreamBridge");

    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(() -> {
          Optional<AbcInboundMessage> messageResponse = containerWrapper.getClient()
              .pollMessage(
                  TARGET_DESTINATION);
          assertThat(messageResponse)
              .isPresent()
              .hasValueSatisfying(resp -> {
                assertThat(resp.getPayload()).isEqualTo(testPayload);
                assertThat(resp.getHeaders())
                    .containsEntry("test-header", "abcd")
                    .doesNotContainKeys("id", "timestamp",
                        "target-protocol"); // Spring Cloud Stream adds these headers but the binder is expected to filter them out
              });
        });

  }

  @Test
  @Order(2)
  void testProducerBindingSuccessfulBatchPublish(@Mock PublishAcknowledgmentCallback ackCallback,
      AbcTestContainerWithConnectedClient containerWrapper) {

    List<String> testPayloads = Stream.generate(
            () -> "{\"payload\": \"%s\"}".formatted(UUID.randomUUID().toString()))
        .limit(2)
        .toList();

    List<MessageHeaders> batchedHeaders = Stream.generate(
            () -> new MessageHeaders(Map.of("test-header", "abcd")))
        .limit(2)
        .toList();

    Message<?> testMessageBatch = MessageBuilder.withPayload(testPayloads)
        .setHeader("test-batch-header", "abcd")
        .setHeader(ConnectorBinderHeaders.PUBLISH_ACKNOWLEDGMENT_CALLBACK, ackCallback)
        .setHeader(BinderHeaders.BATCH_HEADERS, batchedHeaders)
        .build();
    // Send the batch message through the StreamBridge to the producer binding
    boolean sent = streamBridge.send(outputBindingName, testMessageBatch);
    assertTrue(sent, "Message should have been accepted by StreamBridge");

    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(() -> {
          for (String expectedPayload : testPayloads) {
            Optional<AbcInboundMessage> messageResponse = containerWrapper.getClient()
                .pollMessage(TARGET_DESTINATION);
            assertThat(messageResponse)
                .isPresent()
                .hasValueSatisfying(resp -> {
                  assertThat(resp.getPayload()).isEqualTo(expectedPayload);
                  assertThat(resp.getHeaders())
                      .containsEntry("test-header", "abcd")
                      .doesNotContainKeys("id", "timestamp",
                          "target-protocol");
                });
          }
        });
  }

  /**
   * Test that the producer binding can handle a failed synchronous blocking publish scenario.
   * <p>
   * An invalid destination is used to trigger a failure in the publish operation. The test
   * verifies: - that the PublishAcknowledgmentCallback is invoked with an error. - that the
   * producer binding published to the error channel.
   */
  @Test
  @Order(3)
  void testProducerBindingFailedPublish()
      throws InterruptedException {

    String invalidDestination = "invalid-destination";

    String testPayload = "{\"payload\": \"%s\"}".formatted(UUID.randomUUID().toString());
    Message<String> testMessage =
        MessageBuilder.withPayload(testPayload)
            .setHeader(BinderHeaders.TARGET_DESTINATION, invalidDestination)
            .build();

    assertThatThrownBy(() -> streamBridge.send(outputBindingName, testMessage))
        .isInstanceOf(MessagingException.class);

    ErrorMessage errorMessage = application.readMessageReceivedOnErrorChannel(10, TimeUnit.SECONDS);
    assertThat(errorMessage).satisfies(errorMsg -> {
      assertThat(errorMsg).isNotNull();
      assertThat(errorMsg.getOriginalMessage().getPayload()).isEqualTo(testPayload);
    });
  }

  /**
   * Test that the producer binding can successfully synchronously push messages to a dynamic
   * destination. The presence of the scst_targetDestination header indicates that the message
   * should be sent to a dynamic destination.
   */
  @Test
  @Order(4)
  void testDynamicDestinations(AbcTestContainerWithConnectedClient containerWrapper) {
    String testPayload = "{\"payload\": \"%s\"}".formatted(UUID.randomUUID().toString());
    Message<String> testMessage =
        MessageBuilder.withPayload(testPayload)
            .setHeader(BinderHeaders.TARGET_DESTINATION, DYNAMIC_DESTINATION)
            .build();

    boolean sent = streamBridge.send(outputBindingName, testMessage);
    assertTrue(sent, "Message should have been accepted by StreamBridge");

    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(() -> {
          Optional<AbcInboundMessage> messageResponse = containerWrapper.getClient()
              .pollMessage(
                  DYNAMIC_DESTINATION);
          assertThat(messageResponse)
              .isPresent()
              .satisfies(resp -> assertThat(resp.get().getPayload()).isEqualTo(testPayload));
        });

    // Verify that no message was sent to the configured target destination
    assertThat(containerWrapper.getClient().pollMessage(TARGET_DESTINATION)).isNotPresent();

  }

  @Test
  // binder health for producer is lazy and only comes up after the first publish attempt, so we run this test last to ensure it doesn't impact the other tests
  @Order(5)
  void testHealth() throws Exception {
    getMockMvc().perform(get("/actuator/health"))
        .andDo(print())
        .andExpectAll(
            status().isOk(),
            jsonPath("components.binders.components.abc").exists(),
            jsonPath("components.binders.components.abc.status").value("UP")
        );
  }

}
