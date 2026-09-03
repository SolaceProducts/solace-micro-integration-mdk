package com.solace.samples.binder.abc.inbound;

import com.solace.samples.abc.client.AbcClient.AbcInboundMessage;
import com.solace.samples.binder.abc.util.IOUtil;
import com.solace.samples.binder.abc.inbound.acknowledge.AbcAcknowledgmentCallback;
import com.solace.samples.binder.abc.properties.AbcBinderConnectionProperties;
import com.solace.samples.binder.abc.properties.AbcConsumerProperties;
import com.solace.samples.abc.client.AbcClient;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.binder.ExtendedConsumerProperties;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.integration.acks.AckUtils;
import org.springframework.integration.endpoint.MessageProducerSupport;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import java.util.concurrent.TimeUnit;

public class AbcInboundChannelAdapter extends MessageProducerSupport {

  private final Logger log = LoggerFactory.getLogger(
      AbcInboundChannelAdapter.class);

  private final AbcBinderConnectionProperties connectionProperties;
  private final AbcConsumerDestination destination;
  private final ExtendedConsumerProperties<AbcConsumerProperties> consumerProperties;
  private final AtomicReference<AbcClient> abcClientRef = new AtomicReference<>();
  private ScheduledExecutorService scheduledExecutorService;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final List<ScheduledFuture<?>> scheduledTasks = new CopyOnWriteArrayList<>();


  /**
   * Creates a new inbound channel adapter for consuming messages from abc service.
   *
   * @param connectionProperties The binder properties
   * @param destination          The consumer destination
   * @param properties           The consumer properties
   */
  public AbcInboundChannelAdapter(AbcBinderConnectionProperties connectionProperties,
      AbcConsumerDestination destination,
      ExtendedConsumerProperties<AbcConsumerProperties> properties) {
    this.connectionProperties = connectionProperties;
    this.destination = destination;
    this.consumerProperties = properties;
  }

  @Override
  protected void doStart() {
    if (isRunning()) {
      log.warn("Nothing to do. AbcInboundChannelAdapter is already running");
      return;
    }

    abcClientRef.set(IOUtil.createAbcClient(connectionProperties));

    log.info("[destination: {}] Starting abc inbound channel adapter, concurrency: {}",
        destination.getName(), consumerProperties.getConcurrency());

    // Create a ScheduledExecutorService with size based on concurrency
    // Use core pool size = concurrency for optimal thread utilization
    int concurrency = consumerProperties.getConcurrency();
    scheduledExecutorService = new ScheduledThreadPoolExecutor(
        concurrency,
        new CustomizableThreadFactory(
            "abc-consumer-%s".formatted(consumerProperties.getBindingName())
        ),
        new ThreadPoolExecutor.CallerRunsPolicy() // Backpressure handling
    );

    // Performance optimization: Remove tasks from queue immediately when cancelled
    ((ScheduledThreadPoolExecutor) scheduledExecutorService).setRemoveOnCancelPolicy(true);

    running.set(true);

    // Schedule each polling worker
    // Using scheduleWithFixedDelay to avoid pile-up if processing takes longer than interval
    AbcConsumerProperties consumerProps = consumerProperties.getExtension();
    long pollingInterval = consumerProps.getPollingInterval();

    for (int i = 0; i < concurrency; i++) {
      // Stagger initial delays to spread load across workers
      long initialDelay = i * (pollingInterval / concurrency);

      ScheduledFuture<?> future = scheduledExecutorService.scheduleWithFixedDelay(
          new PollingWorker(pollingInterval),
          initialDelay,
          pollingInterval,
          TimeUnit.MILLISECONDS
      );
      scheduledTasks.add(future);
    }

    log.info("[destination: {}] Scheduled {} polling workers with {}ms interval",
        destination.getName(), concurrency, pollingInterval);
  }

  @Override
  protected void doStop() {
    log.info("[destination: {}] Stopping abc inbound channel adapter",
        destination.getName());

    running.set(false);

    // Cancel all scheduled tasks (don't interrupt if running to allow graceful completion)
    for (ScheduledFuture<?> future : scheduledTasks) {
      future.cancel(false);
    }
    scheduledTasks.clear();

    // Shutdown the scheduled executor service
    if (scheduledExecutorService != null) {
      scheduledExecutorService.shutdown();
      try {
        // Wait for tasks to complete or timeout
        if (!scheduledExecutorService.awaitTermination(30, TimeUnit.SECONDS)) {
          log.warn("Executor did not terminate gracefully, forcing shutdown");
          List<Runnable> pendingTasks = scheduledExecutorService.shutdownNow();
          log.warn("Cancelled {} pending tasks", pendingTasks.size());

          if (!scheduledExecutorService.awaitTermination(10, TimeUnit.SECONDS)) {
            log.error("Executor did not terminate after forced shutdown");
          }
        }
      } catch (InterruptedException e) {
        log.warn("Interrupted while waiting for executor termination");
        scheduledExecutorService.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }

    final AbcClient client = abcClientRef.getAndSet(null);
    if (client != null) {
      try {
        client.close();
      } catch (Exception e) {
        log.warn("Error closing ABC client", e);
      }
    }

    log.info("[destination: {}] Abc inbound channel adapter stopped", destination.getName());
  }

  /**
   * The polling worker that actually fetches messages from the Abc service.
   */
  private class PollingWorker implements Runnable {

    private final long basePollingInterval;

    public PollingWorker(long basePollingInterval) {
      this.basePollingInterval = basePollingInterval;
    }

    @Override
    public void run() {
      // Fast-path: Don't process if we're shutting down
      if (!running.get()) {
        return;
      }
      log.debug("Trying to pull for new messages, Polling interval: {} ms", basePollingInterval);
      final AbcClient client = abcClientRef.get();
      if (client == null) {
        log.error("AbcClient is not initialized, skipping poll");
        return;
      }

      try {
        Optional<AbcInboundMessage> messageOpt = client.pollMessage(destination.getName());

        if (messageOpt.isEmpty()) {
          return;
        }

        AbcInboundMessage abcInboundMessage = messageOpt.get();

        if (log.isDebugEnabled()) {
          log.debug("ABC Message received {} with ID {}",
              abcInboundMessage.getPayload(), abcInboundMessage.getId());
        }

        processMessage(abcInboundMessage, client);

      } catch (Exception e) {
        log.warn("[destination: {}] Unexpected processing error in polling worker",
            destination.getName(), e);
      }
    }

    /**
     * Process a single message. Extracted for clarity and potential override.
     */
    private void processMessage(AbcInboundMessage abcInboundMessage, AbcClient client) {
      AbcAcknowledgmentCallback acknowledgmentCallback =
          new AbcAcknowledgmentCallback(
              abcInboundMessage.getDestination(),
              abcInboundMessage.getId(),
              client
          );

      Message<?> message;

      try {
        // Convert the message - reuse builder to reduce allocations
        message = MessageBuilder.withPayload(abcInboundMessage.getPayload())
            .copyHeaders(abcInboundMessage.getHeaders())
            .setHeader(
                IntegrationMessageHeaderAccessor.ACKNOWLEDGMENT_CALLBACK,
                acknowledgmentCallback
            )
            .build();
      } catch (Exception e) {
        log.warn("Error converting message {} to a spring message",
            abcInboundMessage.getId(), e);
        handleConversionError(acknowledgmentCallback, e);
        return;
      }

      try {
        // Send the message to the channel for processing by the application
        sendMessage(message);

        if (log.isDebugEnabled()) {
          log.debug("Acking ABC Message with ID {}", abcInboundMessage.getId());
        }

        // Auto-ack if needed
        AckUtils.autoAck(acknowledgmentCallback);

        if (log.isDebugEnabled()) {
          log.debug("ABC Message with ID {} acked= {}",
              abcInboundMessage.getId(), acknowledgmentCallback.isAcknowledged());
        }
      } catch (Exception e) {
        if (log.isDebugEnabled()) {
          log.debug(
              "Republishing message after processing of ABC Message with ID {} failed. error: {}",
              abcInboundMessage.getId(), e.getMessage());
        }
        // Requeue messages which fail processing
        AckUtils.requeue(acknowledgmentCallback);
      }
    }

    private void handleConversionError(AbcAcknowledgmentCallback callback, Exception e) {
      try {
        if (sendErrorMessageIfNecessary(null, e)) {
          AckUtils.autoAck(callback);
        } else {
          // Poison messages should be rejected
          AckUtils.reject(callback);
        }
      } catch (Exception ex) {
        // try to reject the message if sending the error message fails, but log any errors that occur during rejection
        log.warn(
            "Failed to send error message for conversion error, rejecting original message. Original error: {}, error sending error message: {}",
            e.getMessage(), ex.getMessage(), ex);
        AckUtils.reject(callback);
      }
    }
  }
}
