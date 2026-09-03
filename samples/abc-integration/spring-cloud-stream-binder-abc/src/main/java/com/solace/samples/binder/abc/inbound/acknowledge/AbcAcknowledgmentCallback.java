package com.solace.samples.binder.abc.inbound.acknowledge;

import com.solace.samples.abc.client.AbcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.acks.AcknowledgmentCallback;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AcknowledgmentCallback to acknowledge messages to the Abc service.
 */
public class AbcAcknowledgmentCallback implements AcknowledgmentCallback {

  private static final Logger logger = LoggerFactory.getLogger(
      AbcAcknowledgmentCallback.class);

  private boolean autoAckEnabled = true;
  private final String destination;
  private final String messageId;
  private final AbcClient simpleAbcClient;
  private final AtomicBoolean acknowledged = new AtomicBoolean(false);

  /**
   * Create a new acknowledgment callback for ABC client message.
   *
   * @param destination Destination name the message was consumed from
   * @param messageId   ID of the message to acknowledge
   * @param abcClient   Client to ack/nack message on the Abc Service
   */
  public AbcAcknowledgmentCallback(String destination, String messageId,
      AbcClient abcClient) {
    this.destination = destination;
    this.messageId = messageId;
    this.simpleAbcClient = abcClient;
  }

  @Override
  public void acknowledge(Status status) {
    if (isAcknowledged()) {
      logger.debug("Message {} is already acknowledged", messageId);
      return;
    }

    switch (status) {
      case ACCEPT:
        logger.debug("Acknowledging message {}", messageId);
        try {
          simpleAbcClient.acknowledgeMessage(destination, messageId);
          logger.debug("Message acknowledged using client {}", messageId);
        } catch (Exception e) {
          logger.error("Failed to acknowledge message {}: {}", messageId, e.getMessage());
        }
        break;
      case REJECT, REQUEUE:
        // Both REJECT and REQUEUE send the message to a DLQ in this sample.
        logger.info("Rejecting message {}", messageId);
        try {
          simpleAbcClient.rejectMessage(destination, messageId);
        } catch (Exception e) {
          logger.error("Failed to reject message {}: {}", messageId, e.getMessage());
        }
        break;
      default:
        logger.warn("Unknown acknowledgment status {} for message {}", status, messageId);
        return;
    }

    acknowledged.set(true);
    logger.debug("Message acknowledged {}", messageId);
  }

  @Override
  public boolean isAcknowledged() {
    return acknowledged.get();
  }

  @Override
  public void noAutoAck() {
    autoAckEnabled = false;
  }

  @Override
  public boolean isAutoAck() {
    return autoAckEnabled;
  }

}
