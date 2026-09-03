package com.solace.samples.binder.abc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.solace.samples.abc.client.AbcClient.AbcOutboundMessage;
import com.solace.samples.abc.testextension.container.simple.AbcTestContainerWithConnectedClient;
import com.solace.samples.binder.abc.app.TestApplication;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;

/**
 * Binder integration tests that use a TestApplication to test the consumer bindings. These tests
 * create an application context and an instance of the abc service running in a container.
 * <p>
 */
@ExtendWith(MockitoExtension.class)
@ExtendWith(OutputCaptureExtension.class)
class BinderConsumerMessagingIT extends AbstractBaseWithOnTheFlyContainerIT {

  private TestApplication application;

  @BeforeEach
  void testCleanup() {
    application = getBean(TestApplication.class);
    application.clearReceivedMessages();
    application.clearErrorChannelMessages();
  }

  @Override
  protected String[] getActiveProfiles() {
    return new String[]{"consumer"};
  }

  /**
   * Test that the consumer binding can successfully poll messages from the Abc service.
   * <p>
   * A message is queued on the abc service, then the consumer binding is expected to receive it
   * from the source-destination. This test uses TestApplication.receiveMessage() to confirm the
   * consumer binding received and processed the message successfully.
   */
  @Test
  void testConsumerBindingSuccessfulProcess(AbcTestContainerWithConnectedClient containerWrapper)
      throws InterruptedException {
    String testPayload = "{\"payload\": \"%s\"}".formatted(UUID.randomUUID().toString());
    Message<String> message = MessageBuilder.withPayload(testPayload)
        .setHeader("test-header", "1234")
        .build();
    containerWrapper.getClient().publishMessage(SOURCE_DESTINATION,
        new AbcOutboundMessage(testPayload, message.getHeaders()));

    // Verify the message was received by the consumer binding and properly processed
    Message<?> receivedMessage = application.receiveMessage(10, TimeUnit.SECONDS);
    assertThat(receivedMessage)
        .isNotNull()
        .satisfies(msg -> {
          assertThat(msg.getPayload()).isEqualTo(testPayload);
          assertThat(msg.getHeaders()).containsEntry("test-header", "1234");
        });

    //Ensures that the message was acknowledged and not re-delivered
    assertThat(application.receiveMessage(1, TimeUnit.SECONDS)).as(
        "Message was unexpectedly redelivered").isNull();
  }

  /**
   * Test that the consumer binding can handle a failed process scenario.
   * <p>
   * A message is published to the Abc Service with a header that indicates it should be rejected.
   * The test verifies that the message was rejected and sent to the DLQ.
   */
  @Test
  void testConsumerBindingFailedProcess(AbcTestContainerWithConnectedClient containerWrapper) {
    String testPayload = "{\"payload\": \"%s\"}".formatted(UUID.randomUUID().toString());
    Message<String> message = MessageBuilder.withPayload(testPayload)
        .setHeader("reject_me", "true") // TestApplication REJECTs messages with this header
        .build();
    String messageId = containerWrapper.getClient().publishMessage(
        SOURCE_DESTINATION,
        new AbcOutboundMessage(testPayload, message.getHeaders()));

    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(() -> {
          assertThat(containerWrapper.getClient().getRejectedMessages()).anyMatch(
              msg -> msg.getId().equals(messageId));
        });
  }


  /**
   * Test that the default binding consumer configuration works as expected.
   * <p>
   * This test verifies that the polling interval is set according to the configured binding
   * default.
   */
  @Test
  void testDefaultBindingConfiguration(CapturedOutput capturedOutput) {
    Awaitility.await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(capturedOutput).contains("Polling interval: 1000 ms"));

  }

  /**
   * Test that the consumer binding correctly publishes to the error channel on exception.
   */
  @Test
  void testConsumerBindingPublishesToErrorChannelOnErrors(
      AbcTestContainerWithConnectedClient containerWrapper) throws InterruptedException {
    String payload = UUID.randomUUID().toString();
    MessageHeaderAccessor accessor = new MessageHeaderAccessor();
    accessor.setHeader("cause_error", "true");
    String messageId = containerWrapper.getClient().publishMessage(
        SOURCE_DESTINATION,
        new AbcOutboundMessage(payload, accessor.getMessageHeaders()));

    // Validate the consumer binding sent the message to the error channel
    ErrorMessage errorMessage = application.readMessageReceivedOnErrorChannel(10, TimeUnit.SECONDS);
    assertThat(errorMessage).satisfies(errorMsg -> {
      assertThat(errorMsg).isNotNull();
      assertThat(errorMsg.getOriginalMessage().getPayload()).isEqualTo(payload);
    });

    // Validate the message was REQUEUed, which in our sample, means it was sent to the DLQ
    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(() -> {
          assertThat(containerWrapper.getClient().getRejectedMessages()).anyMatch(
              msg -> msg.getId().equals(messageId));
        });
  }

  @Test
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
