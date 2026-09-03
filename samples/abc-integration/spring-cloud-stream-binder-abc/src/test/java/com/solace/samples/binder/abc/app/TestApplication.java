package com.solace.samples.binder.abc.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.StaticMessageHeaderAccessor;
import org.springframework.integration.acks.AcknowledgmentCallback;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.ErrorMessage;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Test Spring Cloud Stream application that defines a simple producer and consumer to test the ABC
 * binder integration with the ABC backend.
 */
@SpringBootApplication
public class TestApplication {

  private static final Logger logger = LoggerFactory.getLogger(TestApplication.class);

  private final BlockingQueue<Message<?>> receivedMessages = new LinkedBlockingQueue<>();
  private final BlockingQueue<ErrorMessage> receivedErrorMessages = new LinkedBlockingQueue<>();

  public static void main(String[] args) {
    SpringApplication.run(TestApplication.class, args);
  }

  /**
   * A consumer function that receives messages from the destination. Used for testing the consumer
   * side of the binder.
   *
   * @return a consumer that stores received messages
   */
  @Bean
  public Consumer<Message<String>> testConsumer() {
    return message -> {
      if (message.getHeaders().containsKey("reject_me")) {
        AcknowledgmentCallback ackCallback = StaticMessageHeaderAccessor.getAcknowledgmentCallback(
            message);
        if (ackCallback != null) {
          ackCallback.acknowledge(AcknowledgmentCallback.Status.REJECT);
        }
        return;
      }

      if (message.getHeaders().containsKey("cause_error")) {
        throw new RuntimeException("Forced error for testing error channel");
      }
      receivedMessages.add(message);
    };
  }

  @Bean
  public Consumer<Message<String>> otherTestConsumer() {
    return receivedMessages::add;
  }

  /**
   * Subscribes to the error channel to capture error messages for testing. This allows tests to
   * verify that errors are properly published to the error channel.
   */
  @Bean
  public DirectChannel errorChannel() {
    DirectChannel channel = new DirectChannel();
    channel.subscribe(message -> {
      logger.info("Error channel received message: {}", message);
      if (message instanceof ErrorMessage errorMessage) {
        receivedErrorMessages.add(errorMessage);
      }
    });
    return channel;
  }

  /**
   * Returns the next received message, waiting up to the specified timeout if necessary.
   *
   * @param timeout the maximum time to wait
   * @param unit    the time unit of the timeout argument
   * @return the received message, or null if the specified waiting time elapses
   * @throws InterruptedException if interrupted while waiting
   */
  public Message<?> receiveMessage(long timeout, TimeUnit unit) throws InterruptedException {
    return receivedMessages.poll(timeout, unit);
  }

  /**
   * Clears all received messages from the queue.
   */
  public void clearReceivedMessages() {
    receivedMessages.clear();
  }

  /**
   * Returns the next received error message, waiting up to the specified timeout if necessary.
   *
   * @param timeout the maximum time to wait
   * @param unit    the time unit of the timeout argument
   * @return the received error message, or null if the specified waiting time elapses
   * @throws InterruptedException if interrupted while waiting
   */
  public ErrorMessage readMessageReceivedOnErrorChannel(long timeout, TimeUnit unit)
      throws InterruptedException {
    return receivedErrorMessages.poll(timeout, unit);
  }

  /**
   * Clears all received error messages from the queue.
   */
  public void clearErrorChannelMessages() {
    receivedErrorMessages.clear();
  }
}
