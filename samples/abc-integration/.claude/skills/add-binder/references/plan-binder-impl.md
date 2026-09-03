# Plan Binder Implementation

Instructions for analyzing the target technology's SDK and generating the binder module's CLAUDE.md — the design document that drives all subsequent code generation.

The output of this step is `{BINDER_TARGET_MODULE_DIR}/CLAUDE.md`. No source code is generated here.

---

## Step Execution Rules

**Step execution order is mandatory.** Each step must complete successfully before the next step begins. Do not skip ahead, parallelize steps, or begin a step while a previous step is incomplete. If a step fails, stop and report the failure — do not proceed to subsequent steps.

---

## Step 1: Inspect the test-support project

**Blocked by:** Nothing — this is the first step.

The `add-test-support` skill creates a CLAUDE.md at `{TEST_SUPPORT_MODULE_DIR}/CLAUDE.md` (the module root, next to `pom.xml`). `TEST_SUPPORT_MODULE_DIR` is a derived variable defined in SKILL.md.

If the file exists, extract:

| Information | CLAUDE.md section |
|---|---|
| Test utility type (Testcontainers module, GenericContainer, proxy, Jib) | "Test utility type" |
| SDK client class and builder/factory pattern | "SDK client" |
| Connection parameter mapping — how each value flows from container/proxy to SDK client | "Connection parameter mapping" |
| Getter methods on the wrapper/proxy class | "Getter methods exposed" |
| Container details (image, ports, credentials) or remote backend details (env vars) | "Container details" / "Remote backend details" |

The CLAUDE.md is the starting point, but also **inspect the generated test-support source code** under `{TEST_SUPPORT_MODULE_DIR}/src/main/java/`. Find where and how the SDK client instance is created. This reveals:

- Which SDK builder methods are used (and their parameter types)
- Which connection values the SDK actually needs (some may be hardcoded in test-support but need to become configurable in the binder)
- The full client creation pattern the binder's `IOUtil` must replicate

#### Classify test-support architecture

The test-support module uses one of two architectural patterns. Determine which by inspecting the source:

**Pattern 1 — Container-with-connected-client**: The wrapper class extends a `GenericContainer` (or similar Testcontainer). It manages a Docker container lifecycle and creates an SDK client connected to the container. Connection values (URL, port, credentials) are derived from the running container's mapped ports and configured environment variables.

Indicators:
- Wrapper class extends `GenericContainer` or a Testcontainer subclass
- `start()`/`stop()` methods manage container lifecycle
- `getConnectionUrl()` uses `getHost()` + `getMappedPort()`
- Credentials may be hardcoded constants set as container environment variables
- Extension uses `SharedContainerResource` or similar for lazy shared-container creation

**Pattern 2 — Proxy-service-with-client**: The wrapper is a plain Java class (no container). It connects to a pre-running backend service. Connection values are read from environment variables (`.env` file or system env).

Indicators:
- Wrapper class implements `AutoCloseable` (not a container subclass)
- Uses `Dotenv` or `System.getenv()` for configuration
- `getConnectionUrl()` returns an environment variable value
- No Docker/container management
- Extension creates proxy in `beforeAll()`, closes in `afterAll()`

Store the classified pattern as `TEST_SUPPORT_ARCHITECTURE` (`container-with-client` or `proxy-service-with-client`).

If no test-support CLAUDE.md or source code exists, all connection parameters must be derived from the overview report alone (Step 2), and `TEST_SUPPORT_ARCHITECTURE` remains unclassified.

---

## Step 2: Inspect SDK capabilities from the overview report

**Blocked by:** Step 1 must complete successfully.

Re-read the technology overview report at `{TECH_OVERVIEW_PATH}` (resolved relative to the workspace root).

If the file does not exist, print: **"Technology overview report not found at `{TECH_OVERVIEW_PATH}` — run `/analyze-integration-tech {TECH_NAME_LOWER}` first and update `TECH_OVERVIEW_PATH` in configuration.md"** and stop.

Extract the **full SDK surface** regardless of `BINDER_TYPE`. CLAUDE.md is a complete technology reference — it documents all SDK capabilities so it remains valid if `BINDER_TYPE` changes later.

Extract:

- **SDK client class** — fully qualified name, how it's instantiated (builder, factory, constructor)
- **Connection parameters** — every value the SDK needs to establish a connection (URL/endpoint, region, credentials, tokens, connection pool size, timeouts)
- **Authentication pattern** — basic auth, API key, OAuth client credentials, IAM role, connection string, etc.
- **Publish/write API** — catalog **all** publish methods the SDK offers. For each, record the method signature, return type, and whether it is synchronous (returns void/boolean/direct value) or asynchronous (returns `CompletableFuture`/`Future`/`Mono`). Most SDKs provide at least a sync method; some also provide an async variant. Both must be recorded when present — the binder's outbound message handler uses `isAsync()` to branch between them at runtime.
- **Batch publish/write API** — search the SDK for batch-capable publish methods: methods that accept a `List`, array, or collection of messages/records and send them in a single call. For each batch method found, record the method signature, return type, and whether it is synchronous or asynchronous. If no batch publish API exists, record `SDK_HAS_BATCH_PUBLISH: false` — the binder will fall back to sequential single-message publishing within the batch loop. Batch publishing is always supported by the binder; this flag only controls *how* the batch is dispatched to the backend. Record discovered batch methods alongside the single-message publish methods so they appear together in the SDK Client "Publish methods" table of the generated CLAUDE.md.
- **Read/poll API** — method signature, polling vs push, how to consume
- **Data types used by write and read** — the SDK classes or structures passed to the publish method and returned by the poll method. For each type: list its fields, their Java types, and what they represent. This is critical for the binder's message handler — it must know how to convert a Spring Cloud Stream `Message<?>` payload/headers into the SDK's write input, and how to convert the SDK's read output back into a `Message<?>`.
- **Supported Spring Message payload types** — derive from the SDK's publish method input: what Java types can `message.getPayload()` be for the binder's `handleMessage()` to process them? For example: `String` (if the SDK accepts text/JSON), `byte[]` (if the SDK accepts binary), `Map<String, Object>` (if the SDK accepts structured key-value data). List every type the binder will support, with the preferred type first. This list is a **planning output** — it drives the message handler's conversion logic, the Key Design Decisions section, the SDK Client data types section, and the Producer Message Examples in the generated CLAUDE.md.

> **Payload is data, not commands.** The payload of a Spring `Message<?>` flowing through the producer contains only **data** (bytes, strings, serialized objects). No assumption should be made that the payload contains any command-style expression such as a SQL statement, query language expression, API operation name, or DSL expression. All commands, queries, and operational logic belong exclusively in the binder implementation or its configuration — never derived from the message payload. When mapping `Message<?>` to the SDK's write input, treat `message.getPayload()` as opaque data to be stored/published as-is (or serialized), not as an instruction to be interpreted or executed.

- **Health/ping API** — how to verify the connection is alive (for the health indicator)
- **Close/shutdown API** — how to cleanly disconnect
- **Primary exceptions** — what the SDK throws on connection failure, publish failure, auth failure

**Fact-checking:** Every SDK detail extracted here (class names, method signatures, parameter types, return types, exception classes) must be verified against the overview report. Do not guess or infer from naming conventions — if the overview report does not contain the information, re-read it or state what is missing.

Cross-reference with test-support CLAUDE.md (if available from Step 1): the connection parameters must match. If the test-support wrapper exposes `getConnectionUrl()` and `getUsername()`, the binder connection properties must have corresponding fields.

### Identify Spring Boot auto-configuration to exclude

Many backend technologies ship with dedicated Spring Boot auto-configuration starters that detect the SDK client library on the classpath and automatically create client beans, health indicators, and connection pools under their own property namespace (e.g., `spring.{tech}.*`). **The binder and its tests must never rely on these auto-configuration classes.** The binder manages its own client lifecycle through `IOUtil` and its own `@ConfigurationProperties`-backed connection properties (`{TECH_NAME_LOWER}.*`). The test-support module provides containers or proxies with connected clients via JUnit 5 extensions — no Spring-managed client bean is needed.

Search the overview report and SDK documentation for Spring Boot auto-configuration classes related to the target technology. Record each fully-qualified class name as `SPRING_BOOT_AUTOCONFIG_EXCLUDES`. These must be excluded via `@SpringBootApplication(exclude = ...)` in every `@SpringBootApplication`-annotated class (binder `TestApplication`, MI `MicroIntegrationApplication`).

**Negative example — what goes wrong without exclusion:**
```
// SDK client jar on classpath → Spring Boot activates {Tech}AutoConfiguration
// → creates a client bean using spring.{tech}.* properties (not set — binder uses {tech}.*)
// → startup fails: "Factory method '{tech}Client' threw exception"
// → also registers health contributors that conflict with the binder's own health indicator
```

Document the exclusion list in CLAUDE.md **Key Design Decisions** section.

---

## Step 3: Categorize SDK configuration parameters

**Blocked by:** Step 2 must complete successfully.

Sort every configuration parameter discovered in Steps 1–2 into buckets. For each parameter, ask:

| Question | If yes → bucket |
|---|---|
| Is it about **connecting** to the backend (URL, host, port, credentials, TLS, connection timeouts)? | **Connection** → `{Tech}BinderConnectionProperties` |
| Is it **nested/structured** inside a connection concern (auth block, TLS block)? | **Connection sub-object** → separate plain POJO with `@NestedConfigurationProperty` on the parent field |
| Is it about **how the consumer reads** (poll interval, batch size, prefetch, concurrency)? | **Consumer** → `{Tech}ConsumerProperties` |
| Is it about **how the producer writes** (write timeout, retry count, compression, batching)? | **Producer** → `{Tech}ProducerProperties` |

**Key rules:**
- **Connection properties are shared** across all bindings on the same binder instance — one connection, many channels.
- **Consumer/producer properties can be overridden per binding** — the override mechanism is automatic via `ExtendedBindingProperties` + `MappingsProvider`.
- **Both consumer and producer classes must exist** even if empty — `BindingProperties` references them.
- **Start minimal.** Only add fields the binder implementation will actually read. Both consumer and producer classes can start empty.

---

## Step 4: Define the target YAML configuration

**Blocked by:** Step 3 must complete successfully.

Using the categorization from Step 3, define the complete YAML configuration structure this binder will support. Read the ABC template's `src/test/resources/application-consumer.yml` and `src/test/resources/application-async-producer.yml` as structural references — the ABC template splits test configuration into separate profile-specific YAML files (one per capability) rather than a single `application.yml`.

#### How to compose the YAML prefix

All binder YAML prefixes derive from a single value: **`{TECH_NAME_LOWER}`** (e.g., `sqs`, `mongodb`, `influxdb`). In the ABC template this value is `abc`. For the target technology, replace `abc` with `{TECH_NAME_LOWER}` everywhere:

| ABC template uses | Target binder uses | Appears in YAML as |
|---|---|---|
| `abc:` (root-level) | `{TECH_NAME_LOWER}:` | Connection properties prefix — `@ConfigurationProperties("{TECH_NAME_LOWER}")` |
| `binder: abc` | `binder: {TECH_NAME_LOWER}` | Per-binding binder reference |
| `spring.cloud.stream.abc:` | `spring.cloud.stream.{TECH_NAME_LOWER}:` | Extended binding properties — `@ConfigurationProperties("spring.cloud.stream.{TECH_NAME_LOWER}")` |

The derived variables `{BINDER_NAME}`, `{BINDER_CONNECTION_PREFIX}`, and `{EXTENDED_BINDING_PREFIX}` all build on `{TECH_NAME_LOWER}`. When writing the YAML below, substitute the actual technology name — not the variable placeholders.

The YAML has **four sections**:

**Section 1 — Connection** (root-level, backed by `{Tech}BinderConnectionProperties`):

One field per connection parameter from the "Connection" bucket. Nested sub-objects for structured blocks (auth, TLS).

```yaml
{BINDER_CONNECTION_PREFIX}:
  <field>: <example value>        # one per connection parameter
  authentication:                 # nested sub-object (if SDK uses credentials)
    type: <auth type>
    <credential fields>
```

**Section 2 — Bindings and extended properties** (under `spring.cloud.stream`):

Standard Spring Cloud Stream binding configuration plus binder-specific extended properties. Two levels must be distinct:
- `spring.cloud.stream.bindings.<name>.producer.*` — **framework** `ProducerProperties` (e.g., `error-channel-enabled`, `use-native-encoding`)
- `spring.cloud.stream.{BINDER_NAME}.default.producer.*` — **binder-specific** `{Tech}ProducerProperties` (e.g., `write-timeout-ms`)

```yaml
spring:
  cloud:
    stream:
      bindings:
        <function>-out-0:
          destination: <target>
          binder: {BINDER_NAME}
          producer:                          # framework ProducerProperties
            error-channel-enabled: true
            use-native-encoding: true
      {BINDER_NAME}:                         # {Tech}ExtendedBindingProperties
        default:
          consumer:                          # {Tech}ConsumerProperties fields
            <field>: <default>
          producer:                          # {Tech}ProducerProperties fields
            <field>: <default>
```

**Section 3 — Multi-binder** (each binder instance nests connection properties under `environment.{BINDER_CONNECTION_PREFIX}`):

```yaml
spring:
  cloud:
    stream:
      binders:
        {BINDER_NAME}-1:
          type: {BINDER_NAME}
          environment:
            {BINDER_CONNECTION_PREFIX}:
              base-url: http://host1:8088
        {BINDER_NAME}-2:
          type: {BINDER_NAME}
          environment:
            {BINDER_CONNECTION_PREFIX}:
              base-url: http://host2:8088
      bindings:
        output-out-0:
          binder: {BINDER_NAME}-1
```

**Section 4 — Health actuator** (required for binder health endpoint):

```yaml
management:
  endpoint.health:
    show-components: always
    show-details: always
  endpoints.web.exposure.include: health
  health.binders.enabled: true
```

**Validation:** Every field in the YAML must trace back to a categorized parameter from Step 3. Every categorized parameter must appear in the YAML. No placeholder fields — use real SDK parameter names converted to kebab-case.

---

## Step 5: Write CLAUDE.md

**Blocked by:** Step 4 must complete successfully.

Create `{BINDER_TARGET_MODULE_DIR}/CLAUDE.md` (the binder module root, next to `pom.xml`). This file is the canonical reference for all subsequent binder generation steps — property classes, IOUtil, message handler, auto-configuration, provisioner, and integration tests.

Read `spring-cloud-stream-binder-abc/CLAUDE.md` for the exact structure and level of detail. Generate the same sections, filled with the target technology's details from Steps 1–4:

| Section | Fill with |
|---|---|
| **What This Is** | Technology name, which SPI capabilities this binder implements, active `BINDER_TYPE` |
| **Binder Identity** | Binder name registered in `spring.binders` (derived from `{BINDER_NAME}` and `{BINDER_PACKAGE}` — the file is not yet generated at this step), auto-configuration class (FQ name), connection property prefix, extended binding namespace (`spring.cloud.stream.{binder-name}`) |
| **Capability Modes** | `- **Producer supported**: true/false — {evidence summary}`. `- **Consumer supported**: true/false — {evidence summary}`. Derived from `BINDER_TYPE`: `producer,consumer` → both true; `consumer` → consumer true, producer false; `producer` → producer true, consumer false. Include `### Producer Details` subsection (if supported: message handler class, SDK write method, sync/async pattern; if not: "Producer mode is not implemented by this binder.") and `### Consumer Details` subsection (if supported: message producer class, SDK read/poll method, push/poll pattern; if not: "Consumer mode is not implemented by this binder."). |
| **Batch Publishing** | `SDK_HAS_BATCH_PUBLISH`: true/false. If true: batch publish method signature(s) — sync and/or async — from the SDK Client section. The binder's outbound message handler will call the batch API directly, passing a `List` of SDK outbound messages. If false: the binder iterates the batch and calls the single-message publish method for each item sequentially. In both cases the binder detects a batch by checking for the `BinderHeaders.BATCH_HEADERS` header on the inbound `Message<?>`. When present, the payload is `List<Object>` and the header value is `List<MessageHeaders>` — one entry per message. The binder pairs each payload with its corresponding per-message headers via index, filters headers, builds SDK outbound messages, and dispatches them. When `PRODUCER_IS_ASYNC=true`, a single `PublishAcknowledgmentCallback` covers the entire batch (success after all sent, failure on first error). When `PRODUCER_IS_ASYNC=false`, success or failure is determined by whether the sync batch call throws — no callback is involved. |
| **Ack Modes** | Producer and consumer ack modes with boolean flags. See structured specification below. |
| **Build & Test** | Maven commands, Java version |
| **Project Layout** | Source tree with `{Tech}` class names; omit `inbound/` if producer-only, `outbound/` if consumer-only |
| **Key Design Decisions** | Consumer/producer patterns, header filtering, write timeout, two config namespaces. |
| **Message Payload Requirements** | Per-direction payload constraints from Step 2. **Producer direction (Solace → Technology)**: payload requirement — one of `none`, `string`, `json-string`, `typed:{class}`, `content-type:{type}` — with evidence (what the outbound message handler's SDK publish method accepts). List the supported Spring Message payload types with the preferred type first and explain why each is supported (maps to SDK input). **Consumer direction (Technology → Solace)**: payload format — same classification — with evidence (what the inbound channel adapter places into the outbound Spring Message). |
| **Configuration** | The complete YAML from Step 4 — all four sections (connection, bindings+extended, multi-binder, health actuator) with inline comments mapping each field to its backing Java class. **This is the most important section.** |
| **SDK Client** | Full SDK API surface extracted from the overview report. All details must be fact-checked against the report — do not guess. Include: client class (FQ name), builder/factory methods with parameters and defaults, publish/write method signatures with return types, retrieve/poll method signatures with return types, ack/reject methods (if applicable), health/ping method signature, close method signature, exception classes. **Data types section** — for each SDK class or structure used as input to the publish method or returned by the poll method: list its fields with Java types and a one-line description of what each field carries. State the payload format (String, byte[], Map, custom DTO) and how headers/metadata flow. This drives the message handler's conversion logic. **Important:** The publish/write method must accept the message payload as opaque data — the binder must never assume the payload contains commands, queries, or executable expressions (e.g., SQL, query DSL, API calls). All operational commands belong in the binder implementation or configuration. See `spring-cloud-stream-binder-abc/CLAUDE.md` "SDK Client" section for the exact format. |
| **Connection Properties → Java Mapping** | One row per connection parameter: YAML key, Java field name, type, validation annotations, source (test-support CLAUDE.md or overview report). This table drives `{Tech}BinderConnectionProperties` generation. Include a `### Connection property structure` subsection listing each property's full YAML key path, flat key for `argsBuilder.put()` (e.g., `{binder-name}.authentication.username`), and classification (root/nested/credential). |
| **Extended Binding Properties** | Split into `### Extended consumer properties` and `### Extended producer properties` subsections. Each table: one row per property with YAML key, Java field, default value. Empty tables are valid if starting minimal. Include an `### Other properties` subsection for any binder-level properties that are not direction-specific. |
| **Dependencies to Know About** | Client SDK, test-support module, connector framework |
| **Testing Notes** | See structured specification below. |

#### Ack Modes — decision rules

##### Producer ack mode

Inspect SDK publish methods from Step 2 to determine which pattern applies:

| Condition | `PRODUCER_IS_ASYNC` | Ack mode | `{Tech}ProducerProperties` | Handler pattern |
|---|---|---|---|---|
| SDK provides both sync and async publish (async returns `CompletableFuture`/`Future`/`Mono`) | `true` | `ASYNC_BY_CALLBACK_HEADER` | Add `isAsync` field (default `false`) | Branch in `handleMessage`: if `isAsync` → `publishAsync`, else → `publishSync`. `publishAsync` extracts `PublishAcknowledgmentCallback` from message headers; delegate methods (`publishSingleAsync`, `publishBatchAsync`) enforce non-null via `Objects.requireNonNull` (ABC template pattern) |
| SDK has only sync publish, or async capability is uncertain | `false` | `SYNC` | No `isAsync` field | `publishSync` only — `PublishAcknowledgmentCallback` header will be null and must not be expected |

##### Consumer ack mode

| Condition | `CONSUMER_IS_CLIENT_ACK` | Ack mode |
|---|---|---|
| Binder sets `ACKNOWLEDGMENT_CALLBACK` header on inbound messages | `true` | `CLIENT_ACK_BY_CALLBACK_HEADER` |
| Binder does not set the header (messages are auto-acknowledged) | `false` | `AUTO_ACK` |

When `CONSUMER_IS_CLIENT_ACK` is `true`: confirm that the generated consumer endpoint sets `IntegrationMessageHeaderAccessor.ACKNOWLEDGMENT_CALLBACK` with an `org.springframework.integration.acks.AcknowledgmentCallback` implementation. Record `CONSUMER_ACK_NATIVE_SPRING_HEADER`: `true`. Custom binders always use the standard Spring interface — if the generated consumer code does not set this header, it is a generation defect.

##### Batch dispatch matrix

The combination of `PRODUCER_IS_ASYNC` and `SDK_HAS_BATCH_PUBLISH` determines which batch methods the outbound message handler generates:

| `PRODUCER_IS_ASYNC` | `SDK_HAS_BATCH_PUBLISH` | Batch sync method | Batch async method |
|---|---|---|---|
| `true` | `true` | `publishBatchSync` — native batch API | `publishBatchAsync` — native batch async API |
| `true` | `false` | `publishBatchSync` — sequential loop | `publishBatchAsync` — sequential loop on executor |
| `false` | `true` | `publishBatchSync` — native batch API | not generated |
| `false` | `false` | `publishBatchSync` — sequential loop | not generated |

##### Boolean flags summary

Document in CLAUDE.md: `PRODUCER_IS_ASYNC`: true/false; `CONSUMER_IS_CLIENT_ACK`: true/false; `SDK_HAS_BATCH_PUBLISH`: true/false.

#### Testing Notes — required fields

All information needed by `generate-common-tests.md`, `generate-sync-producer-tests.md`, `generate-async-producer-tests.md`, and `generate-consumer-tests.md` to generate integration tests. See `spring-cloud-stream-binder-abc/CLAUDE.md` Testing Notes for the level of detail expected.

**1. Test-support architecture**
- Value of `TEST_SUPPORT_ARCHITECTURE` from Step 1: `container-with-client` or `proxy-service-with-client`

**2. Test-support extension class**
- Fully qualified name — depends on the architecture pattern:
  - `container-with-client` → e.g., `com.example.tech.testextension.container.simple.TechSimpleContainerTestExtension`
  - `proxy-service-with-client` → e.g., `com.example.tech.testextension.proxy.TechProxyTestExtension`
- Source: test-support CLAUDE.md

**3. Test-support wrapper class**
- Fully qualified name (e.g., `com.example.tech.testextension.container.simple.TechTestContainerWithConnectedClient`)
- Source: test-support CLAUDE.md

**4. Wrapper getter methods for connection injection**
- List all public getter method names exposed by the wrapper class
- Source: test-support CLAUDE.md "Getter methods exposed"

**5. System property key → getter mapping table**
- One row per connection property, with columns:

| System property key | Wrapper getter method | Hardcoded value (if any) |
|---|---|---|
| `{BINDER_CONNECTION_PREFIX}.base-url` | `getConnectionUrl()` | — |
| `{BINDER_CONNECTION_PREFIX}.authentication.type` | — | `"basic"` |

- Derived from: Connection Properties mapping (Step 3) cross-referenced with wrapper getters (field 4 above)

**6. SDK destination management methods for test lifecycle**
- For each lifecycle point below, reference the matching method from the SDK Client section of this CLAUDE.md. If the SDK has no equivalent, state "no-op" with rationale.
  - Create destination (@BeforeAll)
  - Delete destination (@AfterAll)
  - Delete all messages (@AfterEach)

**7. SDK poll/read method for test verification**
- Reference the retrieve/poll method from the SDK Client section of this CLAUDE.md. State which method to use for verifying a published message arrived at the backend.

**8. Environment file requirement (proxy-service-with-client only)**
- When `TEST_SUPPORT_ARCHITECTURE` is `proxy-service-with-client`: state that a `.env` file must be created in the binder module root with the same environment variables as the test-support module's `.env`. List the variable names and their purposes. Source: test-support CLAUDE.md "Remote backend details" or `{TEST_SUPPORT_MODULE_DIR}/.env`
- When `TEST_SUPPORT_ARCHITECTURE` is `container-with-client`: state "Not applicable — container manages its own lifecycle and connection values"

**9. Test configuration approach**
- Test configuration uses **profile-specific YAML files** instead of a single `application.yml`. Each messaging integration test class activates its own Spring profile via `getActiveProfiles()`:
  - `BinderSyncProducerMessagingIT` → profile `"sync-producer"` → loads `application-sync-producer.yml`
  - `BinderAsyncProducerMessagingIT` → profile `"async-producer"` → loads `application-async-producer.yml`
  - `BinderConsumerMessagingIT` → profile `"consumer"` → loads `application-consumer.yml`
  - `MultiBinderIT` → profile `"multibinder"` → loads `application-multibinder.yml`
- Each profile YAML contains only the function definitions, bindings, and extended properties relevant to that capability.
- There is **no standalone `HealthBinderIT`** class. The `testHealth()` method is embedded in each messaging IT class (`BinderSyncProducerMessagingIT`, `BinderAsyncProducerMessagingIT`, and `BinderConsumerMessagingIT`). For the producer, `testHealth()` runs last (`@Order(4)`) because producer health is lazy.

| **Producer Message Examples** | Copy-paste-ready Java snippets showing how to create a Spring `Message<?>` and send it via `streamBridge.send(...)`. Provide **one example per supported payload type** (e.g., `String`, `byte[]`, `Map<String, Object>` — whichever the binder's message handler accepts based on the SDK's publish method input). Each example: build the `Message` with `MessageBuilder` including the payload and any relevant headers, then call `streamBridge.send("bindingName", message)` as a one-liner. Additionally include: one example with a `BinderHeaders.TARGET_DESTINATION` header for dynamic destination routing, one example with custom user headers, and one example of a **batch message** — build a `Message<?>` with a `List<String>` payload and a `BinderHeaders.BATCH_HEADERS` header containing a matching `List<MessageHeaders>`, then call `streamBridge.send("bindingName", message)`. No Spring bean wiring or class boilerplate — just the `Message<?>` construction + `send()` call, 3–6 lines each. |

**This file blocks skill progress.** Do not proceed to property class generation or capability delegation until CLAUDE.md is written and all sections are filled.

---

## Validation

Before finishing, verify:

- [ ] Every connection parameter from the test-support CLAUDE.md appears in the Connection Properties mapping table
- [ ] Every field in the YAML traces to a categorized parameter
- [ ] SDK client class, builder pattern, method signatures, and close method match the overview report
- [ ] Data types for publish input and poll output are documented with field names, Java types, and payload format
- [ ] Supported Spring Message payload types are listed (derived from SDK publish input), appear in Message Payload Requirements, and each has a matching Producer Message Example
- [ ] The YAML configuration section has all four parts (connection, bindings+extended, multi-binder, health actuator)
- [ ] No ABC/abc references remain — all values are technology-specific
- [ ] Consumer properties are present (even if empty) regardless of `BINDER_TYPE`
- [ ] Producer properties are present (even if empty) regardless of `BINDER_TYPE`
- [ ] Producer Message Examples section has one example per supported payload type, each showing `MessageBuilder` + `streamBridge.send()`, plus a dynamic destination example and a custom headers example
- [ ] Both publish/write AND read/poll APIs are documented regardless of `BINDER_TYPE`
- [ ] Test-support architecture classified as `container-with-client` or `proxy-service-with-client` (or noted as unclassified if no test-support exists)
- [ ] Testing Notes section includes all 9 required fields: (1) test-support architecture, (2) extension class FQ name, (3) wrapper class FQ name, (4) wrapper getter methods, (5) system property key → getter mapping table, (6) SDK destination management methods, (7) SDK poll/read method for test verification, (8) environment file requirement (proxy-service-with-client: variable list; container-with-client: "Not applicable"), (9) test configuration approach (profile-split, health-in-messaging-IT)
- [ ] Batch Publishing section documents `SDK_HAS_BATCH_PUBLISH` flag with evidence, and describes which batch strategy the binder will use (native batch API or sequential single-message fallback)
- [ ] Producer Message Examples include a batch message example
