package com.solace.samples.abc.client;

import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Client API for interacting with ABC service. Provides methods for publishing messages, polling
 * for messages, acknowledging messages, and managing destinations.
 */
public interface AbcClient {

  /**
   * Publishes asynchronously message to abc service.
   *
   * @param destination        The destination name
   * @param abcOutboundMessage The message request containing payload and headers
   * @return A CompletableFuture that will complete with message id assigned by the service or an
   * exception if the publishing fails
   */
  CompletableFuture<String> publishMessageAsync(String destination,
      AbcOutboundMessage abcOutboundMessage);


  /**
   * Publishes asynchronously a batch of messages to abc service.
   *
   * @param destination         The destination name
   * @param abcOutboundMessages The list of message requests containing payload and headers
   * @return A CompletableFuture that will complete with list of message ids assigned by the service
   * or an exception if the publishing fails
   */
  CompletableFuture<List<String>> publishMessagesAsync(String destination,
      List<AbcOutboundMessage> abcOutboundMessages);

  /**
   * Publishes a message on the specified destination on the abc service.
   *
   * @param destination        The destination name
   * @param abcOutboundMessage The message containing payload and headers
   * @return The published message ID assigned form the service
   * @throws RestClientException if there's an error communicating with the service
   */
  String publishMessage(String destination, AbcOutboundMessage abcOutboundMessage);


  /**
   * Publishes a batch of messages on the specified destination on the abc service.
   *
   * @param destination         The destination name
   * @param abcOutboundMessages The list of messages containing payload and headers
   * @return The list of published message IDs assigned form the service
   * @throws RestClientException if there's an error communicating with the service
   */
  List<String> publishMessages(String destination, List<AbcOutboundMessage> abcOutboundMessages);

  /**
   * Poll for a message from the specified destination on abc service. It can retrieve same message
   * multiple times if it is not acknowledged or rejected, so the client should ensure to ack or
   * reject
   *
   * @param destination The destination name
   * @return Optional containing the message response if available
   */
  Optional<AbcInboundMessage> pollMessage(String destination);

  /**
   * Acknowledge a message on the specified destination.
   *
   * @param destination The destination name
   * @param messageId   The message ID to acknowledge
   * @return true if the message was successfully acknowledged, false if the message ID was not
   * found
   * @throws RestClientException if there's an error communicating with the service
   */
  boolean acknowledgeMessage(String destination, String messageId);

  /**
   * List all available destinations.
   *
   * @return List of destinations
   */
  List<Destination> listDestinations();

  /**
   * Create a new destination.
   *
   * @param name The destination name
   * @return {@code true} if the destination was successfully created, {@code false} if a
   * destination
   * @throws RestClientException if there's an error communicating with the service
   */
  boolean createDestination(String name);

  /**
   * Delete a destination.
   *
   * @param destination The name of the destination to delete
   * @return true if the destination was successfully deleted, false if it didn't exist
   * @throws RestClientException if there's an error communicating with the service
   */
  boolean deleteDestination(String destination);

  /**
   * Check if the Abc service is available.
   *
   * @return true if the service is available, false otherwise
   */
  boolean isHealthy();

  /**
   * Delete all messages from the specified destination.
   *
   * @param destination The destination name
   * @return The number of messages deleted
   * @throws RestClientException if there's an error communicating with the service
   */
  int deleteAllMessages(String destination);

  /**
   * Reject a message on the specified destination, moving it to the dead letter queue.
   *
   * @param destination The destination name
   * @param messageId   The message ID to reject
   * @return The rejected message response
   * @throws RestClientException if there's an error communicating with the service
   */
  AbcInboundMessage rejectMessage(String destination, String messageId);

  /**
   * Get all rejected messages from the dead letter queue.
   *
   * @return List of all rejected messages
   */
  List<AbcInboundMessage> getRejectedMessages();

  /**
   * Close the client and release resources.
   */
  void close();

  /**
   * Data Transfer Object for outbound message requests.
   */
  class AbcOutboundMessage {

    private String payload;
    private Map<String, Object> headers;

    public AbcOutboundMessage() {
    }

    public AbcOutboundMessage(String payload, Map<String, Object> headers) {
      this.payload = payload;
      this.headers = headers;
    }

    public String getPayload() {
      return payload;
    }

    public void setPayload(String payload) {
      this.payload = payload;
    }

    public Map<String, Object> getHeaders() {
      return headers;
    }

    public void setHeaders(Map<String, Object> headers) {
      this.headers = headers;
    }
  }

  /**
   * Data Transfer Object for inbound messages.
   */
  class AbcInboundMessage {

    private String id;
    private String destination;
    private String payload;
    private Map<String, Object> headers;

    public AbcInboundMessage() {
    }

    public AbcInboundMessage(String id, String destination, String payload,
        Map<String, Object> headers) {
      this.id = id;
      this.destination = destination;
      this.payload = payload;
      this.headers = headers;
    }

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getDestination() {
      return destination;
    }

    public void setDestination(String destination) {
      this.destination = destination;
    }

    public String getPayload() {
      return payload;
    }

    public void setPayload(String payload) {
      this.payload = payload;
    }

    public Map<String, Object> getHeaders() {
      return headers;
    }

    public void setHeaders(Map<String, Object> headers) {
      this.headers = headers;
    }
  }

  class Destination {

    private String name;
    private Integer messageCount;

    public Destination() {
    }

    public Destination(String name, Integer messageCount) {
      this.name = name;
      this.messageCount = messageCount;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public Integer getMessageCount() {
      return messageCount;
    }

    public void setMessageCount(Integer messageCount) {
      this.messageCount = messageCount;
    }
  }

  class DestinationRequest {

    private String name;

    public DestinationRequest() {
    }

    public DestinationRequest(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }

  static AbcClientBuilder builder() {
    return new AbcClientBuilder();
  }

  interface AuthenticationProvider {

  }

  class BasicAuthenticationProvider implements AuthenticationProvider {

    private final String username;
    private final String password;

    public BasicAuthenticationProvider(String username, String password) {
      this.username = username;
      this.password = password;
    }

    public String getUsername() {
      return username;
    }

    public String getPassword() {
      return password;
    }

  }


  class AbcClientBuilder {

    private String baseUrl;
    private long connectionTimeout = 30;
    private long readTimeout = 30;
    private AuthenticationProvider authenticationProvider;

    public AbcClientBuilder withBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
      return this;
    }

    public AbcClientBuilder withConnectionTimeout(long connectionTimeout) {
      this.connectionTimeout = connectionTimeout;
      return this;
    }

    public AbcClientBuilder withAuthenticationProvider(
        AuthenticationProvider authenticationProvider) {
      this.authenticationProvider = authenticationProvider;
      return this;
    }


    public AbcClientBuilder withReadTimeout(long readTimeout) {
      this.readTimeout = readTimeout;
      return this;
    }

    public AbcClient build() {
      if (baseUrl == null || baseUrl.isEmpty()) {
        throw new IllegalStateException("Base URL must be provided");
      }
      return new SimpleAbcClient(baseUrl, connectionTimeout, readTimeout,
          authenticationProvider);
    }
  }
}