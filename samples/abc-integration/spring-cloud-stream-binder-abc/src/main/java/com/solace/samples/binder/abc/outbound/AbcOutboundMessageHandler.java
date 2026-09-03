package com.solace.samples.binder.abc.outbound;

import com.solace.connector.core.io.header.ConnectorBinderHeaders;
import com.solace.connector.core.io.outbound.PublishAcknowledgmentCallback;
import com.solace.samples.abc.client.AbcClient.AbcOutboundMessage;
import com.solace.samples.binder.abc.properties.AbcBinderConnectionProperties;
import com.solace.samples.binder.abc.properties.AbcProducerProperties;
import com.solace.samples.abc.client.AbcClient;
import com.solace.samples.binder.abc.util.IOUtil;
import java.util.List;
import java.util.stream.IntStream;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.binder.BinderHeaders;
import org.springframework.cloud.stream.binder.ExtendedProducerProperties;
import org.springframework.context.Lifecycle;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.ErrorMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MessageHandler that sends messages to the abc service.
 */
public class AbcOutboundMessageHandler implements MessageHandler, Lifecycle {

  private static final Logger logger = LoggerFactory.getLogger(
      AbcOutboundMessageHandler.class);

  private final AbcProducerDestination destination;
  @Nullable
  private final MessageChannel errorChannel;
  private final ExtendedProducerProperties<AbcProducerProperties> producerProperties;
  private final AbcBinderConnectionProperties connectionProperties;
  private final AtomicReference<AbcClient> abcClientRef = new AtomicReference<>();

  private final AtomicBoolean running = new AtomicBoolean(false);

  /**
   * Create a new outbound message handler.
   *
   * @param destination          The producer destination
   * @param errorChannel         The error channel for failed sends
   * @param producerProperties   The producer properties
   * @param connectionProperties The connection properties
   */
  public AbcOutboundMessageHandler(
      AbcProducerDestination destination,
      @Nullable MessageChannel errorChannel,
      ExtendedProducerProperties<AbcProducerProperties> producerProperties,
      AbcBinderConnectionProperties connectionProperties) {
    this.destination = destination;
    this.errorChannel = errorChannel;
    this.producerProperties = producerProperties;
    this.connectionProperties = connectionProperties;
  }

  @Override
  public void handleMessage(Message<?> message) throws MessagingException {

    if (!isRunning()) {
      throw new MessagingException(
          "Cannot send messages since message handler is not running or has been instructed to stop");
    }

    String dynamicDestination = message.getHeaders()
        .get(BinderHeaders.TARGET_DESTINATION, String.class);
    String actualDestination =
        dynamicDestination != null ? dynamicDestination : destination.getName();

    // removes leading or tailing spaces any
    actualDestination = actualDestination.trim();

    // If producer is configured for async and has support for async and the callback header is present, use async publishing, otherwise use sync.
    if (producerProperties.getExtension().isAsync()) {
      publishAsync(actualDestination, message);
    } else {
      publishSync(actualDestination, message);
    }
  }

  private void publishAsync(String actualDestination, Message<?> message) {
    // ack call back is required for async sending to allow the producer binding to signal when messages have been acknowledged by the target system.
    PublishAcknowledgmentCallback publishAckCallback =
        message.getHeaders().get(ConnectorBinderHeaders.PUBLISH_ACKNOWLEDGMENT_CALLBACK,
            PublishAcknowledgmentCallback.class);

    if (message.getHeaders().containsKey(BinderHeaders.BATCH_HEADERS)) {
      publishBatchAsync(actualDestination, message, publishAckCallback);
    } else {
      publishSingleAsync(actualDestination, message, publishAckCallback);
    }
  }


  private void publishSync(String actualDestination, Message<?> message) {

    if (message.getHeaders().containsKey(BinderHeaders.BATCH_HEADERS)) {
      publishBatchSync(actualDestination, message);
    } else {
      publishSingleSync(actualDestination, message);
    }
  }

  private void publishBatchAsync(String batchDestination,
      Message<?> message, PublishAcknowledgmentCallback publishAckCallback) {

    Objects.requireNonNull(publishAckCallback,
        () -> "required %s header is missing, cannot do asynchronous publishing"
            .formatted(ConnectorBinderHeaders.PUBLISH_ACKNOWLEDGMENT_CALLBACK));

    List<MessageHeaders> batchedHeaders = (List<MessageHeaders>) message.getHeaders()
        .get(BinderHeaders.BATCH_HEADERS);
    if (batchedHeaders == null) {
      throw new IllegalArgumentException(
          "Received malformed batched message; headers can't be null");
    }
    List<Object> batchedPayloads = (List<Object>) message.getPayload();

    if (batchedHeaders.size() != batchedPayloads.size()) {
      throw new IllegalArgumentException(
          "Received malformed batched message; headers and payloads are not of the same size");
    }

    List<AbcOutboundMessage> batch = IntStream.range(0, batchedPayloads.size())
        .mapToObj(i -> new AbcOutboundMessage(
            (String) batchedPayloads.get(i), filterHeaders(batchedHeaders.get(i))))
        .toList();

    long timeoutMs = producerProperties.getExtension().getWriteTimeoutMs();
    logger.debug("Sending batch of {} messages async to destination: {} (timeout: {}ms)",
        batch.size(), batchDestination, timeoutMs);

    abcClientRef.get().publishMessagesAsync(batchDestination, batch)
        .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .thenAccept(ids -> {
          logger.debug("Successfully sent batch of {} messages to {}, IDs: {}",
              ids.size(), batchDestination, ids);
          publishAckCallback.onPublishSuccess();
        })
        .exceptionally(e -> {
          Throwable cause =
              e instanceof java.util.concurrent.CompletionException ? e.getCause() : e;
          if (cause instanceof TimeoutException) {
            logger.error("Timed out sending batch to {} after {}ms", batchDestination, timeoutMs);
          } else {
            logger.error("Error sending batch of messages to {}", batchDestination, cause);
          }
          if (errorChannel != null) {
            errorChannel.send(new ErrorMessage(cause, message));
          }
          publishAckCallback.onPublishFailure(cause);
          return null;
        });
  }


  private void publishSingleAsync(String actualDestination,
      Message<?> message, PublishAcknowledgmentCallback publishAckCallback) {
    // Create an actual ABC Sdk outbound message to be sent with Abc SDK client,
    // filtering out any headers that should not be sent to the ABC service.
    AbcOutboundMessage request = new AbcOutboundMessage(
        (String) message.getPayload(), filterHeaders(message.getHeaders()));

    Objects.requireNonNull(publishAckCallback,
        () -> "required %s header is missing, cannot do asynchronous publishing"
            .formatted(ConnectorBinderHeaders.PUBLISH_ACKNOWLEDGMENT_CALLBACK));

    long timeoutMs = producerProperties.getExtension().getWriteTimeoutMs();
    logger.debug("Sending message async to destination: {} (timeout: {}ms)", actualDestination,
        timeoutMs);
    abcClientRef.get().publishMessageAsync(actualDestination, request)
        .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .thenAccept(id -> {
          logger.debug("Successfully sent message to {}, ID: {}", actualDestination, id);
          publishAckCallback.onPublishSuccess();
        })
        .exceptionally(e -> {
          Throwable cause =
              e instanceof java.util.concurrent.CompletionException ? e.getCause() : e;
          if (cause instanceof TimeoutException) {
            logger.error("Timed out sending message to {} after {}ms", actualDestination,
                timeoutMs);
          } else {
            logger.error("Error sending message to {}", actualDestination, cause);
          }
          if (errorChannel != null) {
            errorChannel.send(new ErrorMessage(cause, message));
          }
          publishAckCallback.onPublishFailure(cause);
          return null;
        });
  }

  // publishes a batch of messages synchronously, waiting for the publishing to complete before returning.
  // Note: batchDestination is used to publish all messages in the batch to the same destination.
  // The actualDestination header is ignored for batch messages since the batch is published to a single destination.
  // When it is not desired to publish all messages in the batch to the same destination,
  // Retrieve per message destination form a wrapped per message header stored in BinderHeaders.BATCH_HEADERS
  private void publishBatchSync(String batchDestination, Message<?> message) {

    List<MessageHeaders> batchedHeaders = (List<MessageHeaders>) message.getHeaders()
        .get(BinderHeaders.BATCH_HEADERS);
    if (batchedHeaders == null) {
      throw new IllegalArgumentException(
          "Received malformed batched message; headers can't be null");
    }
    List<Object> batchedPayloads = (List<Object>) message.getPayload();
    try {
      if (batchedHeaders.size() != batchedPayloads.size()) {
        throw new IllegalArgumentException(
            "Received malformed batched message; headers and payloads are not of the same size");
      }

      List<AbcOutboundMessage> batch = IntStream.range(0, batchedPayloads.size())
          .mapToObj(i -> new AbcOutboundMessage(
              (String) batchedPayloads.get(i), filterHeaders(batchedHeaders.get(i))))
          .toList();

      List<String> ids = abcClientRef.get().publishMessages(batchDestination, batch);
      logger.debug("Successfully sent batch of {} messages to {}, IDs: {}",
          ids.size(), batchDestination, ids);

    } catch (Exception e) {
      if (errorChannel != null) {
        errorChannel.send(new ErrorMessage(e, message));
      }
      throw new MessagingException(message, e);
    }
  }

  // publishes a single message synchronously, waiting for the publishing to complete before returning.
  private void publishSingleSync(String actualDestination, Message<?> message) {
    // Create an actual ABC Sdk outbound message to be sent with Abc SDK client,
    // filtering out any headers that should not be sent to the ABC service.
    AbcOutboundMessage request = new AbcOutboundMessage(
        (String) message.getPayload(), filterHeaders(message.getHeaders()));

    logger.debug("Sending message sync to destination: {} ", actualDestination);
    try {
      String id = abcClientRef.get().publishMessage(actualDestination, request);
      logger.debug("Successfully sent message to {}, ID: {}", actualDestination, id);
    } catch (Exception e) {
      if (errorChannel != null) {
        errorChannel.send(new ErrorMessage(e, message));
      }
      throw new MessagingException(message, e);
    }
  }


  public static Map<String, Object> filterHeaders(MessageHeaders headers) {
    Map<String, Object> filteredHeaders = new HashMap<>(headers);
    filteredHeaders.remove(ConnectorBinderHeaders.PUBLISH_ACKNOWLEDGMENT_CALLBACK);
    filteredHeaders.remove(MessageHeaders.ID);
    filteredHeaders.remove(MessageHeaders.TIMESTAMP);
    return filteredHeaders;
  }

  @Override
  public void start() {
    if (isRunning()) {
      logger.warn("Nothing to do. AbcOutboundMessageHandler is already running");
      return;
    }

    abcClientRef.set(IOUtil.createAbcClient(connectionProperties));

    logger.debug("[destination: {}] Outbound message handler started", destination.getName());
    running.set(true);
  }

  @Override
  public void stop() {

    final AbcClient client = abcClientRef.getAndSet(null);
    if (client != null) {
      try {
        client.close();

      } catch (Exception e) {
        logger.warn("Error closing ABC client", e);
      }
    } else {
      logger.warn("[destination: {}] Outbound message handler was not started, nothing to stop",
          destination.getName());
    }

    running.set(false);
    logger.debug("[destination: {}] Outbound message handler stopped", destination.getName());
  }

  @Override
  public boolean isRunning() {
    return running.get();
  }
}
