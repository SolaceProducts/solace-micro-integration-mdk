# Spring Cloud Stream Binder for ABC Service

## What This Is
A Spring Cloud Stream binder that connects Spring Boot apps to an "ABC" messaging service. It implements the full binder SPI: provisioning, polling-based consumption, async production, acknowledgment, health checks, and extended per-binding properties.

## Build & Test
```bash
mvn clean install                    # build + unit tests
mvn verify                           # includes integration tests (needs Docker for Testcontainers)
mvn test -Dtest=BinderMessagingIT    # run a specific IT
```
Java 17 required. Parent POM: `pubsubplus-connector-component-build-parent` (Solace connector framework).

## Project Layout
```
src/main/java/.../abc/
  AbcBinder.java                          # Core binder (extends AbstractMessageChannelBinder)
  config/                                 # Auto-configuration, health indicator, property mappings
  inbound/                                # Polling consumer (AbcInboundChannelAdapter) + ack callback
  outbound/                               # Async producer (AbcOutboundMessageHandler)
  properties/                             # Connection, consumer, producer, binding properties
  provisioning/                           # Minimal provisioner (creates destination objects by name)
  util/IOUtil.java                        # AbcClient factory
src/main/resources/META-INF/
  spring.binders                          # Registers "abc" binder type
  shared.beans                            # Bean allowlist for multi-binder child contexts
  spring/*.AutoConfiguration.imports      # Boot 3 auto-config registration
src/test/java/.../abc/
  AbstractBaseWithOnTheFlyContainerIT.java   # Abstract base — container lifecycle, profile activation, connection injection
  BinderSyncProducerMessagingIT.java           # Sync producer tests — publish, fail (MessagingException), dynamic dest, health (profile: "sync-producer")
  BinderAsyncProducerMessagingIT.java          # Async producer tests — publish, fail, dynamic dest, health (profile: "async-producer")
  BinderConsumerMessagingIT.java              # Consumer tests — consume, reject, config, error channel, health (profile: "consumer")
  MultiBinderIT.java                          # Multi-binder test — two instances, both healthy (profile: "multibinder")
  app/TestApplication.java                    # Test harness with injectable consumer behavior
src/test/resources/
  application-consumer.yml                    # Consumer profile — testConsumer binding + consumer extended props
  application-sync-producer.yml               # Sync producer profile — testSupplier binding + async: false + producer extended props
  application-async-producer.yml              # Async producer profile — testSupplier binding + async: true + producer extended props
  application-multibinder.yml                 # Multi-binder profile — two binder instances
```

## Key Design Decisions
- **Polling consumer, not push** — `AbcInboundChannelAdapter` uses a `ScheduledExecutorService` with configurable polling interval (default 5s) and concurrency-based worker pool.
- **Async publishing** — Producer uses `CompletableFuture` from `publishMessageAsync()` for non-blocking sends.
- **Ack strategy** — Success → auto-ack; processing error → requeue (retry); conversion error → reject (DLQ).
- **Header filtering** — Outbound strips `id`, `timestamp`, `target-protocol`, and ack callback headers before sending.
- **Write timeout** — Producer enforces `writeTimeoutMs` (default 50s) via `CompletableFuture.orTimeout()`. Configurable per binding under `spring.cloud.stream.abc.default.producer.write-timeout-ms`.
- **Two config namespaces** — Connection at root (`abc.*`), binder-specific per-binding settings under `spring.cloud.stream.abc.*`. See Configuration section below.

## Batch Publishing

`SDK_HAS_BATCH_PUBLISH: true`

The ABC SDK provides native batch publish methods that accept a `List<AbcOutboundMessage>` and send all messages in a single call:
- Sync: `List<String> publishMessages(String destination, List<AbcOutboundMessage> batch)`
- Async: `CompletableFuture<List<String>> publishMessagesAsync(String destination, List<AbcOutboundMessage> batch)`

The outbound message handler detects batch messages by checking for the `BinderHeaders.BATCH_HEADERS` header. When present, the payload is `List<Object>` and the header value is `List<MessageHeaders>` — one entry per message. The handler pairs each payload with its corresponding per-message headers via index, filters headers, builds a `List<AbcOutboundMessage>`, and dispatches via the batch API. When `PRODUCER_IS_ASYNC=true`, a single `PublishAcknowledgmentCallback` covers the entire batch (success after all sent, failure on first error). When `PRODUCER_IS_ASYNC=false`, success or failure is determined by whether the sync batch call throws — no callback is involved.

## Configuration

Two separate YAML namespaces. Connection properties are root-level; extended binding properties are under `spring.cloud.stream`.

**Namespace 1 — Connection** (`AbcBinderConnectionProperties`, prefix `abc`):
```yaml
abc:
  base-url: http://localhost:8088      # @NotNull @Pattern — must be http://host:port
  port: 9090
  authentication:                      # @NestedConfigurationProperty → AuthenticationConfig
    type: basic                        # basic | oauth
    username: user
    password: pass
```

**Namespace 2 — Bindings and extended properties**:
```yaml
spring:
  cloud:
    stream:
      bindings:                                          # standard SCS binding config
        myConsumer-in-0:
          destination: source-destination
          binder: abc
          group: my-group
        mySupplier-out-0:
          destination: target-destination
          binder: abc
          producer:                                      # framework ProducerProperties (not binder-specific)
            error-channel-enabled: true
            use-native-encoding: true
      abc:                                               # AbcExtendedBindingProperties (binder-specific)
        default:                                         # DEFAULTS_PREFIX — defaults for all bindings
          consumer:                                      # AbcConsumerProperties
            polling-interval: 5000
          producer:                                      # AbcProducerProperties
            write-timeout-ms: 50000
```

**Health actuator** (required for binder health endpoint):
```yaml
management:
  endpoint.health:
    show-components: always
    show-details: always
  endpoints.web.exposure.include: health
  health.binders.enabled: true
```

## SDK Client

**Class:** `com.solace.samples.abc.client.AbcClient` (interface)
**Implementation:** `com.solace.samples.abc.client.SimpleAbcClient`

### Client creation

```java
AbcClient client = new AbcClient.AbcClientBuilder()
    .withBaseUrl("http://localhost:8088")
    .withAuthenticationProvider(new BasicAuthenticationProvider("user", "pass"))
    .withConnectionTimeout(30)
    .withReadTimeout(30)
    .build();
```

**`AbcClientBuilder`** methods:

| Method | Parameter | Default |
|---|---|---|
| `withBaseUrl(String)` | base URL | required |
| `withAuthenticationProvider(AuthenticationProvider)` | auth provider | null |
| `withConnectionTimeout(long)` | seconds | 30 |
| `withReadTimeout(long)` | seconds | 30 |
| `build()` | | returns `AbcClient` |

**`BasicAuthenticationProvider`** — constructor: `BasicAuthenticationProvider(String username, String password)`

### Publish methods

| Method | Returns |
|---|---|
| `CompletableFuture<String> publishMessageAsync(String destination, AbcOutboundMessage msg)` | message ID (async) |
| `String publishMessage(String destination, AbcOutboundMessage msg)` | message ID (sync) |
| `CompletableFuture<List<String>> publishMessagesAsync(String destination, List<AbcOutboundMessage> batch)` | list of message IDs (async) |
| `List<String> publishMessages(String destination, List<AbcOutboundMessage> batch)` | list of message IDs (sync) |

### Retrieve methods

| Method | Returns |
|---|---|
| `Optional<AbcInboundMessage> pollMessage(String destination)` | single message or empty |
| `boolean acknowledgeMessage(String destination, String messageId)` | true on success |
| `AbcInboundMessage rejectMessage(String destination, String messageId)` | rejected message (moved to DLQ) |
| `List<AbcInboundMessage> getRejectedMessages()` | all messages from DLQ |

Messages can be polled multiple times until acked or rejected.

### Health and lifecycle

| Method | Returns |
|---|---|
| `boolean isHealthy()` | true if service is up, throws `IllegalStateException` if down |
| `void close()` | releases resources |

### Destination management

| Method | Returns |
|---|---|
| `List<Destination> listDestinations()` | all destinations |
| `boolean createDestination(String name)` | true on success |
| `boolean deleteDestination(String destination)` | true if existed |
| `int deleteAllMessages(String destination)` | count deleted |

### Data types

**`AbcOutboundMessage`** — input to `publishMessage()` / `publishMessageAsync()`:
- `String payload` — message body as a String
- `Map<String, Object> headers` — message metadata as key-value pairs

Binder conversion: `AbcOutboundMessageHandler` casts `Message.getPayload()` to `String`, filters internal headers, wraps both into `AbcOutboundMessage`.

**`AbcInboundMessage`** — returned by `pollMessage()`:
- `String id` — service-assigned unique message ID (used for ack/reject)
- `String destination` — name of the source destination
- `String payload` — message body as a String
- `Map<String, Object> headers` — message metadata as key-value pairs

Binder conversion: `AbcInboundChannelAdapter` takes the polled `AbcInboundMessage`, uses `payload` as the `Message<?>` body and `headers` as message headers. The `id` is stored for acknowledgment.

**`Destination`** — returned by `listDestinations()`:
- `String name` — destination name
- `Integer messageCount` — current queue depth

### Exceptions

All methods throw `RestClientException` on communication failure.

## Dependencies to Know About
- `com.solace.samples:abc-client:1.0.0-SNAPSHOT` — the ABC client library (sibling module)
- `com.solace.samples:abc-test-support:1.0.0-SNAPSHOT` — Testcontainers extension (sibling module)
- `com.solace.connector.core:pubsubplus-connector-io-common` — shared Solace connector types

## Testing Notes
- All ITs extend `AbstractBaseWithOnTheFlyContainerIT` which manages a Dockerized ABC service.
- Container connection is injected via command-line arguments in `@BeforeAll` through `SpringApplicationBuilder.run(buildConnectionArgs(...))`, not `@DynamicPropertySource`.
- **Profile-based test configuration**: Each messaging IT activates its own Spring profile via `getActiveProfiles()`:
  - `BinderSyncProducerMessagingIT` → `"sync-producer"` → loads `application-sync-producer.yml` (sync producer bindings only)
  - `BinderAsyncProducerMessagingIT` → `"async-producer"` → loads `application-async-producer.yml` (async producer bindings only)
  - `BinderConsumerMessagingIT` → `"consumer"` → loads `application-consumer.yml` (consumer bindings only)
  - `MultiBinderIT` → `"multibinder"` → loads `application-multibinder.yml`
- **No standalone HealthBinderIT** — the `testHealth()` method is embedded in `BinderSyncProducerMessagingIT`, `BinderAsyncProducerMessagingIT`, and `BinderConsumerMessagingIT`. For the producer, it runs last via `@Order(4)` due to lazy producer health.
- `TestApplication` exposes queues and flags to control consumer behavior (reject, throw) from tests.
