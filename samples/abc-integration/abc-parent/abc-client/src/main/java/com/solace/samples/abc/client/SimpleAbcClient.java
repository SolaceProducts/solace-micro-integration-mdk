package com.solace.samples.abc.client;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Client implementation for interacting with the ABC Service. This implementation is thread safe
 * and can be used concurrently.
 * <p>
 */
public class SimpleAbcClient implements AbcClient {

  private static final Logger logger = LoggerFactory.getLogger(SimpleAbcClient.class);

  private final String baseUrl;
  private final RestTemplate restTemplate;
  private final ExecutorService executorService;
  // NOTE: not used for this particular implementation but can be used for future authentication needs
  @SuppressWarnings("unused")
  private final AuthenticationProvider authenticationProvider;

  SimpleAbcClient(String baseUrl, long connectionTimeout, long readTimeout,
      AuthenticationProvider authenticationProvider) {
    this.baseUrl = baseUrl;
    this.authenticationProvider = authenticationProvider;
    this.restTemplate = new RestTemplateBuilder().connectTimeout(
            Duration.ofSeconds(connectionTimeout))
        .readTimeout(Duration.ofSeconds(readTimeout)).build();

    this.executorService = Executors.newCachedThreadPool(r -> new

        Thread(r, "simple-abc-async-client"));
    logger.info("SimpleAbcClient initialized with base URL: {}", baseUrl);
  }

  @Override
  public CompletableFuture<String> publishMessageAsync(String destination,
      AbcOutboundMessage abcOutboundMessage) {
    return CompletableFuture.supplyAsync(() -> publishMessage(destination, abcOutboundMessage),
        executorService);
  }

  @Override
  public CompletableFuture<List<String>> publishMessagesAsync(String destination,
      List<AbcOutboundMessage> abcOutboundMessages) {
    return CompletableFuture.supplyAsync(() -> publishMessages(destination, abcOutboundMessages),
        executorService);
  }


  @Override
  public String publishMessage(String destination,
      AbcOutboundMessage abcOutboundMessage) {
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);

      HttpEntity<AbcOutboundMessage> requestEntity = new HttpEntity<>(abcOutboundMessage, headers);
      String url = UriComponentsBuilder.fromUriString(baseUrl)
          .path("/destinations/{destination}/messages")
          .buildAndExpand(destination)
          .toUriString();

      ResponseEntity<AbcInboundMessage> response = restTemplate.exchange(
          url,
          HttpMethod.POST,
          requestEntity,
          AbcInboundMessage.class);

      if (response.getBody() != null) {
        logger.debug("Successfully published message on destination: {}, message ID: {}",
            destination, response.getBody().getId());
        return response.getBody().getId();
      } else {
        logger.warn("Message published but received null response from server for destination: {}",
            destination);
        throw new RestClientException(
            "Failed to publish the message to destination %s".formatted(destination));
      }
    } catch (Exception e) {
      e.printStackTrace();
      throw new RestClientException(
          "Error publishing message to destination %s".formatted(destination),
          e);
    }
  }

  @Override
  public List<String> publishMessages(String destination,
      List<AbcOutboundMessage> abcOutboundMessages) {
    return abcOutboundMessages.stream()
        .map(message -> publishMessage(destination, message))
        .toList();
  }

  @Override
  public Optional<AbcInboundMessage> pollMessage(String destination) {
    try {
      String url = UriComponentsBuilder.fromUriString(baseUrl)
          .path("/destinations/{destination}/messages")
          .buildAndExpand(destination)
          .toUriString();

      ResponseEntity<AbcInboundMessage> response = restTemplate.exchange(
          url,
          HttpMethod.GET,
          null,
          AbcInboundMessage.class);

      if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
        logger.debug("Successfully polled message from destination: {}, message ID: {}",
            destination, response.getBody().getId());
        return Optional.of(response.getBody());
      } else {
        logger.debug("No messages available on destination: {}", destination);
        return Optional.empty();
      }
    } catch (Exception e) {
      //e.printStackTrace();
      logger.error("Unexpected error polling message from destination {}", destination, e);
      return Optional.empty();
    }
  }

  @Override
  public boolean acknowledgeMessage(String destination, String messageId) {
    try {
      String url = UriComponentsBuilder.fromUriString(baseUrl)
          .path("/destinations/{destination}/messages/{id}/ack")
          .buildAndExpand(destination, messageId)
          .toUriString();

      ResponseEntity<AbcInboundMessage> response = restTemplate.exchange(
          url,
          HttpMethod.POST,
          null,
          AbcInboundMessage.class);

      if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
        logger.debug("Successfully acknowledged message on destination: {}, message ID: {}",
            destination, messageId);
        return true;
      } else {
        logger.warn(
            "Message to acknowledge was not found on a server for message ID: {}",
            messageId);
        return false;
      }
    } catch (Exception e) {
      // e.printStackTrace();
      throw new RestClientException(
          "Error acknowledging message %s on destination %s".formatted(messageId, destination), e);
    }
  }

  @Override
  public List<Destination> listDestinations() {
    try {
      String url = UriComponentsBuilder.fromUriString(baseUrl)
          .path("/destinations")
          .toUriString();

      ResponseEntity<List<Destination>> response = restTemplate.exchange(
          url,
          HttpMethod.GET,
          null,
          new ParameterizedTypeReference<>() {
          });

      if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
        logger.debug("Successfully retrieved {} destinations", response.getBody().size());
        return response.getBody();
      } else {
        logger.debug("No destinations available");
        return Collections.emptyList();
      }
    } catch (Exception e) {
      // e.printStackTrace();
      logger.error("Unexpected error listing destinations", e);
      return Collections.emptyList();
    }
  }

  @Override
  public boolean createDestination(String name) {
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);

      HttpEntity<DestinationRequest> requestEntity = new HttpEntity<>(new DestinationRequest(name),
          headers);
      String url = UriComponentsBuilder.fromUriString(baseUrl)
          .path("/destinations")
          .toUriString();

      ResponseEntity<Destination> response = restTemplate.exchange(
          url,
          HttpMethod.POST,
          requestEntity,
          Destination.class);

      if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
        logger.debug("Successfully created destination: {}", response.getBody().getName());
        return true;
      } else {
        logger.warn(
            "Created destination but received null response from server for destination: {}",
            name);
        throw new RestClientException(
            "Received null response when creating destination %s".formatted(
                name));
      }
    } catch (Exception e) {
      // e.printStackTrace();
      throw new RestClientException(
          "Error creating destination: %s".formatted(name), e);
    }
  }

  @Override
  public boolean deleteDestination(String destination) {
    try {
      String url = UriComponentsBuilder.fromUriString(baseUrl)
          .path("/destinations/{destination}")
          .buildAndExpand(destination)
          .toUriString();
      ResponseEntity<Void> response = restTemplate.exchange(
          url,
          HttpMethod.DELETE,
          null,
          Void.class);

      if (response.getStatusCode().value() == 204) {
        logger.debug("Successfully deleted destination: {}", destination);
        return true;
      } else {
        logger.warn("Unexpected response code {} when deleting destination: {}",
            response.getStatusCode().value(), destination);
        return false;
      }
    } catch (Exception e) {
      // e.printStackTrace();
      throw new RestClientException("Error deleting destination: %s".formatted(destination), e);
    }
  }

  @Override
  public boolean isHealthy() {
    String url = UriComponentsBuilder.fromUriString(baseUrl)
        .path("/actuator/health")
        .toUriString();

    ResponseEntity<HealthResponse> response = restTemplate.exchange(
        url,
        HttpMethod.GET,
        null,
        HealthResponse.class);

    if (response.getStatusCode().is2xxSuccessful()
        && response.getBody() != null
        && "UP".equalsIgnoreCase(response.getBody().getStatus())) {
      return true;
    } else {
      throw new IllegalStateException(
          "ABC Service is not healthy Code:%d State:%s".formatted(
              response.getStatusCode().value(),
              (response.getBody() != null ? response.getBody().getStatus() : "No response body")));
    }
  }

  @Override
  public int deleteAllMessages(String destination) {
    try {
      String url = UriComponentsBuilder.fromUriString(baseUrl)
          .path("/destinations/{destination}/messages/all")
          .buildAndExpand(destination)
          .toUriString();

      ResponseEntity<DeleteMessagesResponse> response = restTemplate.exchange(
          url,
          HttpMethod.DELETE,
          null,
          DeleteMessagesResponse.class);

      if (response.getStatusCode().is2xxSuccessful()) {
        if (response.getStatusCode().value() == 204) {
          // No content means no messages were in the queue to delete
          logger.info("No messages were in the destination to delete: {}", destination);
          return 0;
        } else if (response.getBody() != null) {
          // 200 OK with count of deleted messages
          logger.info("Successfully deleted {} messages from destination: {}",
              response.getBody().getCount(), destination);
          return response.getBody().getCount();
        } else {
          logger.warn("Deleted messages but received null response from server for destination: {}",
              destination);
          return 0;
        }
      } else {
        logger.warn("Unexpected response code when deleting messages: {}",
            response.getStatusCode().value());
        return 0;
      }
    } catch (Exception e) {
      // e.printStackTrace();
      throw new RestClientException("Error emptying destination %s".formatted(destination), e);
    }
  }

  @Override
  public AbcInboundMessage rejectMessage(String destination, String messageId) {
    try {
      String url = UriComponentsBuilder.fromUriString(baseUrl)
          .path("/destinations/{destination}/messages/{id}/reject")
          .buildAndExpand(destination, messageId)
          .toUriString();

      ResponseEntity<AbcInboundMessage> response = restTemplate.exchange(
          url,
          HttpMethod.POST,
          null,
          AbcInboundMessage.class);

      if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
        logger.debug("Successfully rejected message on destination: {}, message ID: {}",
            destination, messageId);
        return response.getBody();
      } else {
        logger.warn("Rejected message but received null response from server for message ID: {}",
            messageId);
        throw new RestClientException("Received null response when rejecting message");
      }
    } catch (Exception e) {
      // e.printStackTrace();
      throw new RestClientException(
          "Unexpected error rejecting message %s on destination %s".formatted(messageId,
              destination),
          e);
    }
  }

  @Override
  public List<AbcInboundMessage> getRejectedMessages() {
    try {
      String url = UriComponentsBuilder.fromUriString(baseUrl)
          .path("/destinations/DLQ/messages/all")
          .toUriString();

      ResponseEntity<List<AbcInboundMessage>> response = restTemplate.exchange(
          url,
          HttpMethod.GET,
          null,
          new ParameterizedTypeReference<>() {
          });

      if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
        logger.debug("Successfully retrieved {} rejected messages from DLQ",
            response.getBody().size());
        return response.getBody();
      } else {
        logger.debug("No rejected messages available in DLQ");
        return Collections.emptyList();
      }
    } catch (Exception e) {
      logger.error("Unexpected error retrieving rejected messages", e);
      return Collections.emptyList();
    }
  }

  @Override
  public void close() {
    logger.info("Shutting down SimpleAbcClient executor service");
    executorService.shutdown();
    try {
      // Wait a bit for existing tasks to terminate
      if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
        logger.warn("Executor did not terminate in the specified time, forcing shutdown");
        executorService.shutdownNow();
      }
    } catch (InterruptedException e) {
      // e.printStackTrace();
      logger.warn("Executor shutdown interrupted", e);
      executorService.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }


  static class HealthResponse {

    private String status;

    public HealthResponse() {
      // Default constructor for deserialization
    }

    public HealthResponse(String status) {
      this.status = status;
    }

    public String getStatus() {
      return status;
    }

    public void setStatus(String status) {
      this.status = status;
    }
  }

  /**
   * Data Transfer Object for delete messages response.
   */
  static class DeleteMessagesResponse {

    private int count;

    public DeleteMessagesResponse() {
    }

    public DeleteMessagesResponse(int count) {
      this.count = count;
    }

    public int getCount() {
      return count;
    }

    public void setCount(int count) {
      this.count = count;
    }
  }


}
