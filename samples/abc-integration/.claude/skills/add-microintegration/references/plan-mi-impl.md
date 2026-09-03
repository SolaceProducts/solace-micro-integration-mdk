# Plan Micro-Integration Implementation

Analyze the target technology's binder and test-support infrastructure, cross-reference their configurations, and produce `{MI_CLAUDE_MD}` — the design document that drives all code generation in `generate-mi.md`.

**Output:** `{MI_CLAUDE_MD}`. No source code is generated here.

---

## Prerequisites

### Resolve binder identity

Read `{TARGET_PROJECT_FOLDER}/{BINDER_ARTIFACT_ID}/src/main/resources/META-INF/spring.binders`. This file exists in both binder modes — created by `add-binder` (custom) or `analyze-3party-binder` (third-party).

The file uses the format `binder-name=fully.qualified.ConfigClass`. Extract the left side of the `=` sign and store as `RESOLVED_BINDER_NAME`.

If the file is not found or the binder name cannot be parsed, print: **"Cannot resolve binder name from `spring.binders` — run `add-binder` or `analyze-3party-binder` first"** and stop.

### Verify upstream design documents

Verify that `{BINDER_CLAUDE_MD}` exists. If not, print: **"Binder CLAUDE.md not found at `{BINDER_CLAUDE_MD}` — run `add-binder` or `analyze-3party-binder` first"** and stop.

Verify that `{TEST_SUPPORT_CLAUDE_MD}` exists. If not, print: **"Test-support CLAUDE.md not found at `{TEST_SUPPORT_CLAUDE_MD}` — run `add-test-support` first"** and stop.

Read both files. They are the sole sources of binder and test-support design information for all sections below.

---

## Section 1: Binder Capabilities & Acknowledgment

What the binder can do and how it confirms message delivery. This drives which factory classes get generated, which beans are registered, and which test classes are included.

**Reference:** `AbcConsumerBindingCapabilitiesFactory.java`, `AbcProducerBindingCapabilitiesFactory.java` in `abc-micro-integration/src/main/java/`.

### Capability modes

Read from the `Capability Modes` section of `{BINDER_CLAUDE_MD}`. The binder must support at least one direction:

- **Consumer supported**: `true` or `false` — look in the `### Consumer Details` subsection for the message producer class and SDK read/poll method
- **Producer supported**: `true` or `false` — look in the `### Producer Details` subsection for the message handler class and SDK write method

Store as `VERIFIED_CONSUMER_SUPPORTED` and `VERIFIED_PRODUCER_SUPPORTED`.

### Acknowledgment modes

Read from the `Ack Modes` section of `{BINDER_CLAUDE_MD}`. Map each to the connector framework enum:

| Binder behavior | Framework enum |
|---|---|
| Producer async — publish returns Future/Mono, callback-based ack | `ProducerAckMode.ASYNC_BY_CALLBACK_HEADER` |
| Producer sync — publish blocks or returns void | `ProducerAckMode.SYNC` |
| Consumer client-ack — sets `ACKNOWLEDGMENT_CALLBACK` header | `ConsumerAckMode.CLIENT_ACK_BY_CALLBACK_HEADER` |
| Consumer auto-ack — no per-message ack control | `ConsumerAckMode.AUTO_ACK` |

### Producer ack mode — factory pattern

The producer capabilities factory must determine how the binder publishes messages.

From `{BINDER_CLAUDE_MD}`, locate the binder's extended producer properties class — search the `Extended Binding Properties` or `Configuration` sections. For custom binders this is `{Tech}ProducerProperties`; for third-party binders, the class discovered by `analyze-3party-binder`. Determine the binder's async publishing capability:

- **No async capability** — the binder only publishes synchronously. The factory hardcodes `ProducerAckMode.SYNC`.
- **Only async** — the binder only publishes asynchronously with no sync option. The factory hardcodes `ProducerAckMode.ASYNC_BY_CALLBACK_HEADER`.
- **Both modes with a control property** — the extended producer properties expose a configuration property that selects between async and sync per binding (e.g., `isAsync()`). The factory determines the ack mode dynamically at runtime: the `create()` method casts `ProducerProperties` to `ExtendedProducerProperties<{Tech}ProducerProperties>`, reads the control property, and returns `ASYNC_BY_CALLBACK_HEADER` or `SYNC` based on the currently configured mode. The inner capabilities class stores the ack mode as a constructor parameter.

Record the determined case and, for the dynamic case, the extended producer properties class (FQ name) and the control getter method.

**Reference:** `AbcProducerBindingCapabilitiesFactory.java` in `abc-micro-integration/src/main/java/`.

### Consumer ack callback bridging

When the consumer ack mode maps to `CLIENT_ACK_BY_CALLBACK_HEADER` (from the table above), determine whether the binder needs ack bridging in the micro-integration layer.

The connector framework expects consumer messages to carry the standard Spring `IntegrationMessageHeaderAccessor.ACKNOWLEDGMENT_CALLBACK` header. Custom binders built by `add-binder` set this header natively. Third-party binders (e.g., GCP Pub/Sub) may use a proprietary ack header instead and need an interceptor to bridge it.

Follow the decision tree:

```
Consumer ack mode = CLIENT_ACK_BY_CALLBACK_HEADER?
├── NO  → CONSUMER_ACK_BRIDGING_REQUIRED = false  (auto-ack, nothing to bridge)
└── YES → Is the binder custom-generated (by add-binder)?
    ├── YES → CONSUMER_ACK_BRIDGING_REQUIRED = false
    │         Custom binders always set the standard Spring
    │         ACKNOWLEDGMENT_CALLBACK header natively.
    └── NO  → Third-party binder (from analyze-3party-binder).
              Read CONSUMER_ACK_NATIVE_SPRING_HEADER from the
              Ack Modes section of {BINDER_CLAUDE_MD}.
              ├── true  → CONSUMER_ACK_BRIDGING_REQUIRED = false  (binder handles it)
              └── false → CONSUMER_ACK_BRIDGING_REQUIRED = true   (need interceptor)
                          Extract from {BINDER_CLAUDE_MD}:
                          - Proprietary ack header name (e.g., GcpPubSubHeaders.ORIGINAL_MESSAGE)
                          - Proprietary ack class — fully qualified name
                          - Ack method signature
                          - Nack method signature
```

When `CONSUMER_ACK_BRIDGING_REQUIRED` is `true`, a `@GlobalChannelInterceptor` must be generated using `.claude/skills/add-microintegration/pseudocode_consumer_ack.txt` as the structural template.

Store as `CONSUMER_ACK_BRIDGING_REQUIRED` (boolean).

### What this determines

- Which `*BindingCapabilitiesFactory` classes to generate (only for supported directions)
- How `*BindingCapabilities.getAcknowledgmentMode()` determines the ack mode — hardcoded `SYNC`, hardcoded `ASYNC_BY_CALLBACK_HEADER`, or dynamic (from extended producer properties) depending on the binder's async capability
- Which bean methods to include in `MicroIntegrationApplication.java`
- Which test classes to generate (`BasicConsumerMessagingIT` and/or `BasicProducerMessagingIT`)
- Whether consumer ack callback bridging code is generated (`CONSUMER_ACK_BRIDGING_REQUIRED`)

---

## Section 2: Connection Configuration

How the binder connects to the target technology at runtime, and how integration tests inject those connection values programmatically.

**Reference:** `BaseTest.java` constants and `setupBinderConnectionProperties()` method in `abc-micro-integration/src/test/java/`.

### Extract connection properties from binder

From the Configuration section of `{BINDER_CLAUDE_MD}`, extract every connection property. For each, record:

- Full YAML key path (e.g., `abc.authentication.username`)
- Java type (String, int, enum, boolean)
- Whether **dynamic** (varies per environment — e.g., URL, port) or **static** (constant across environments — e.g., auth type)
- The flat key format for `argsBuilder.put()` (dots, not nested YAML)

### Catalog test-support connection-value getters

Read the wrapper/proxy class source code under `{TARGET_PROJECT_FOLDER}/{TEST_SUPPORT_ARTIFACT_ID}/src/main/java/`. Identify every public getter that returns a String/int value mapping to a binder connection property (e.g., `getConnectionUrl()`, `getBasicAuthUsername()`). Note whether each is dynamic or static.

### Build alignment table

Cross-reference binder connection properties with test-support getters. This table is the core artifact — it drives `setupBinderConnectionProperties()`, the test YAML comment block, and connection constants.

For every binder connection property, match a test-support getter or hardcoded value:

| Binder property key | Flat key for `argsBuilder` | Test-support getter | Dynamic/Static | Hardcoded? | Notes |
|---|---|---|---|---|---|
| `{prefix}.base-url` | `{RESOLVED_BINDER_NAME}.base-url` | `getConnectionUrl()` | Dynamic | No | URL with mapped port |
| `{prefix}.authentication.type` | `{RESOLVED_BINDER_NAME}.authentication.type` | *(none)* | Static | Yes: `"basic"` | Auth type constant |

### Validate completeness

Check for gaps:
- **Missing getter**: binder property has no test-support getter — must be hardcoded. Document the hardcoded value and rationale.
- **Type mismatch**: getter return type doesn't match property type — document the required conversion.

Every binder connection property must have an injection strategy (getter or hardcoded).

### Plan argsBuilder injection

For each alignment table row, plan the `argsBuilder.put()` call:
- Getter value: `argsBuilder.put("{key}", wrapper.{getter}())`
- Hardcoded value: `argsBuilder.put("{key}", "{value}")`
- Type conversion: `argsBuilder.put("{key}", String.valueOf(wrapper.{getter}()))`

---

## Section 3: Extended Binding Properties

Consumer and producer property keys that tune binder behavior beyond connection — e.g., polling interval, batch size, timeout. These appear in test profile YAML under `spring.cloud.stream.{RESOLVED_BINDER_NAME}.default`.

**Reference:** `application-messaging-consumer.yml` and `application-messaging-producer.yml` in `abc-micro-integration/src/test/resources/`.

### Extract from binder CLAUDE.md

From the Extended Binding Properties section of `{BINDER_CLAUDE_MD}`, extract consumer and producer property keys. For each, record:
- Property key
- Default value
- Purpose

### Plan test-tuned defaults

Determine values optimized for integration test speed and reliability (e.g., short polling intervals, generous timeouts). These will be written into the test profile YAML files.

---

## Section 4: Message Flow & Payloads

What happens to messages as they cross the binder boundary — payload format constraints and whether interceptors need to transform messages.

**Reference:** `AbcConsumerBindingMessageInterceptorFactory.java`, `AbcProducerBindingInterceptorFactory.java` in `abc-micro-integration/src/main/java/`.

### Payload requirements

From `{BINDER_CLAUDE_MD}`, determine for each direction whether the binder imposes payload format constraints. Store as one of: `none`, `string`, `json-string`, `typed:{class}`, `content-type:{type}`.

- `PRODUCER_PAYLOAD_REQUIREMENT`: how the outbound message handler processes the incoming payload
- `CONSUMER_PAYLOAD_FORMAT`: what payload type the inbound message producer places into the outbound Spring Message

### Message interceptors

The interceptor factories (`ConsumerBindingMessageInterceptorFactory`, `ProducerBindingMessageInterceptorFactory`) are extension points for modifying messages before/after the binder processes them. In the ABC template they return `null` (no-op).

Based on `{BINDER_CLAUDE_MD}`, determine whether the target technology requires message interception — for example, payload format conversion, header enrichment, or content-type adaptation. If the binder handles these internally, interceptors remain no-op scaffolding. Document the decision and rationale.

### Consumer ack callback bridging interceptor

**Only applicable when `CONSUMER_ACK_BRIDGING_REQUIRED` is `true` (from Section 1).**

The structural template is `.claude/skills/add-microintegration/pseudocode_consumer_ack.txt` — it contains the `AcknowledgmentCallback` wrapper class and `@GlobalChannelInterceptor` bean with all the wiring logic. Document the resolved proprietary values (header name, ack class, ack method, nack method from Section 1) so `generate-mi.md` can substitute the pseudocode placeholders with concrete values.

---

## Section 5: Application Wiring & Configuration

How `MicroIntegrationApplication.java` registers beans and how `application.yml` configures the Spring Cloud Stream runtime.

**Reference:** `MicroIntegrationApplication.java` and `src/main/resources/application.yml` in `abc-micro-integration/`.

### Naming conventions

Document the naming scheme `generate-mi.md` needs for class names, bean names, packages, and string identifiers:

- Target Java package (replaces `com.solace.samples.microintegration`)
- Technology class prefix (replaces `Abc` — e.g., `MongoDb`)
- Binder identifier registered in `spring.binders` (replaces `"abc"` in `getBinderType()`, `equals("abc")`, YAML `binder: abc`, health JSON path `components.binders.components.abc`)
- Technology label for comments/Javadoc (replaces `ABC`)

### Bean registration

Based on verified capabilities from Section 1, document which bean methods to include in `MicroIntegrationApplication.java`:
- Consumer capabilities factory + consumer interceptor factory (only if consumer supported)
- Producer capabilities factory + producer interceptor factory (only if producer supported)
- `@GlobalChannelInterceptor` ack bridging bean (only if `CONSUMER_ACK_BRIDGING_REQUIRED` is `true`)

### Spring Boot auto-configuration exclusion

Check whether the target technology has Spring Boot auto-configuration classes (e.g., `org.springframework.boot.autoconfigure.{tech}.*`). When present, they detect the SDK client on the classpath and create competing client beans, health indicators, and connection pools under a different property namespace (`spring.{tech}.*`). These must NOT be used — the binder manages its own client via its connection properties, and the test-support module provides containers/proxies with connected clients via JUnit extensions. Add all such classes to `@SpringBootApplication(exclude = {...})` on `MicroIntegrationApplication`. Document the exclusion list in `{MI_CLAUDE_MD}`.

### application.yml

This file is entirely framework boilerplate — 20 input/output bindings, consumer groups, workflow routing, Solace defaults, management/actuator configuration, and `logging.level.root: warn`. It contains no technology-specific content. No binder or MI logging entries exist here; those appear only in the test profile YAMLs (Section 7).

### application-operator.yml

This file is the **operator-facing sample configuration** — it shows how to configure the connector at deployment time. Unlike `application.yml`, its content is **capability-conditional** and contains technology-specific values. It must be planned here so `generate-mi.md` has all the information needed to produce it.

**Reference:** `abc-micro-integration/src/main/resources/application-operator.yml`

#### Binding layout

Based on `VERIFIED_CONSUMER_SUPPORTED` and `VERIFIED_PRODUCER_SUPPORTED` (from Section 1), document the binding layout:

- **Producer only**: flow 0 (Solace → Tech) — `input-0: solace`, `output-0: {RESOLVED_BINDER_NAME}` with `destination: target-destination`. No flow 1. One workflow.
- **Consumer only**: flow 0 (Tech → Solace) — `input-0: {RESOLVED_BINDER_NAME}` with `destination: source-destination`, `output-0: solace`. No flow 1. One workflow.
- **Both**: flow 0 (Solace → Tech) + flow 1 (Tech → Solace) — four bindings, two workflows.

Document which bindings appear and their binder/destination assignments.

#### Workflow count

- Single direction (producer only or consumer only): **1 workflow** (workflow 0)
- Both directions: **2 workflows** (workflow 0 + workflow 1)

#### Extended binding properties to display

From Section 3 (Extended Binding Properties), list each property to be shown in the operator YAML as a **commented-out YAML entry with a description comment**. Separate into consumer and producer groups:

- **Consumer properties**: include only when `VERIFIED_CONSUMER_SUPPORTED` is `true`. For each property, document the YAML key, default value, and a short description of its purpose.
- **Producer properties**: include only when `VERIFIED_PRODUCER_SUPPORTED` is `true`. Same format.

These appear under `spring.cloud.stream.{RESOLVED_BINDER_NAME}.default`.

#### Content-type decision for transforms

The operator YAML includes a **commented-out transform block** on workflow 0 as an example. Determine the content types to use:

- Check `PRODUCER_PAYLOAD_REQUIREMENT` and `CONSUMER_PAYLOAD_FORMAT` from Section 4 (Message Payload Requirements).
- If the binder's payload requirements confirm XML support (e.g., `content-type:application/xml` or `typed:` with XML-capable types), use `"application/xml"` for `source-payload` and `"application/json"` for `target-payload`.
- If XML is **not** supported, use `"application/json"` for both `source-payload` and `target-payload`.

Document the chosen content types and the rationale.

#### Logging packages

Document the resolved logging package names for the operator YAML:

- MI package (replaces `com.solace.connector.samples`)
- Binder package (replaces `com.solace.samples.binder.abc`)

Static framework entries (`com.solace.connector.core`, `com.solace.spring.cloud.stream.binder`) carry over unchanged.

---

## Section 6: Test Infrastructure

The plumbing that makes integration tests work: containers or proxy services, JUnit extensions, SDK client access, and how connection properties are bridged into the Spring application context.

**Reference:** `BaseTest.java`, `BasicConsumerMessagingIT.java`, and `BasicProducerMessagingIT.java` in `abc-micro-integration/src/test/java/`.

### JUnit extensions & wrapper classes

From `{TEST_SUPPORT_CLAUDE_MD}`, extract:
- **Architecture pattern**: `container-with-client` or `proxy-service-with-client`
- **JUnit 5 extension class**: fully qualified name
- **Container wrapper / proxy class**: fully qualified name
- **Test-support package**: for import statements

### SDK client methods for test lifecycle

Read the wrapper/proxy class source code under `{TARGET_PROJECT_FOLDER}/{TEST_SUPPORT_ARTIFACT_ID}/src/main/java/`. Identify:
- **Client accessor** — getter that returns the SDK client instance (e.g., `getClient()`)
- **SDK message types** — inbound/outbound types used in tests

Catalog every SDK client method needed for test lifecycle. For each, record the full method signature:

| Lifecycle point | SDK method | Notes |
|---|---|---|
| Create destination | e.g., `client.createQueue(String name)` | @BeforeAll |
| Delete destination | e.g., `client.deleteQueue(String name)` | @AfterAll |
| Purge messages | e.g., `client.deleteAllMessages(String name)` | @AfterEach |
| Publish message | e.g., `client.publishMessage(String dest, AbcOutboundMessage msg)` | Consumer test: send to tech |
| Poll message | e.g., `Optional<AbcInboundMessage> client.pollMessage(String dest)` | Producer test: read from tech |

If the SDK has no equivalent for a lifecycle point, note as no-op with rationale.

### BaseTest — shared test base class

Connection property constants and `setupBinderConnectionProperties()` live in `BaseTest.java`, which both test classes extend. The complete content of this file is planned in Section 8.

---

## Section 7: Test Profiles

The YAML files that configure which bindings point to which binder and set extended properties for each test scenario.

**Reference (structural templates):**
- `abc-micro-integration/src/test/resources/application-messaging-consumer.yml`
- `abc-micro-integration/src/test/resources/application-messaging-producer.yml`
- `abc-micro-integration/src/test/resources/application-messaging-consumer-with-transforms.yml`
- `abc-micro-integration/src/test/resources/application-messaging-producer-with-transforms.yml`

### Binding direction assignments

For each profile, document which bindings use the target binder vs Solace. Only include bindings for supported capabilities:

- **Consumer profile** (`messaging-consumer`): `input-0: binder: {target}` (read from tech), `output-0: binder: solace` (write to Solace). Workflow 0 only.
- **Producer profile** (`messaging-producer`): `input-0: binder: solace`, `output-0: binder: {target}`, plus `input-1: binder: {target}` and `output-1: binder: solace` for workflow-1 loopback verification. Workflows 0 and 1.
- **Batch producer profile** (`messaging-batch-producer`): derived from the producer profile above. Identical bindings, workflows, extended properties, connection properties comment block, and logging. Only addition: `input-0` (Solace consumer) binding includes `consumer.batch-mode: true` and `consumer.batch-timeout: 1000` to enable Solace batch consumption, so multiple messages are collected into a batch before the producer binder publishes them to the target technology.
- **Consumer transform variant** (`messaging-consumer-with-transforms`): same bindings as consumer profile (input-0 target, output-0 solace). Workflow 0 with transform configuration added.
- **Producer transform variant** (`messaging-producer-with-transforms`): only workflow-0 bindings (`input-0: binder: solace`, `output-0: binder: {target}`). No workflow-1 loopback bindings. Workflow 0 with transform configuration added.

### Connection properties — runtime injection, not YAML

Connection properties are NOT set in test YAML. They are injected at runtime via `argsBuilder` in the test class (Section 6). Document the commented-out block for each profile showing which properties exist and how they're injected.

### Extended binding properties

Under `spring.cloud.stream.{RESOLVED_BINDER_NAME}.default`. Document consumer and producer extended properties with defaults tuned for test speed, as planned in Section 3.

### Logging

Each test profile includes logging entries at two levels:

- **Static framework entries** (carry over unchanged from the template):
  - `com.solace.connector.core: DEBUG`
  - `com.solace.spring.cloud.stream.binder: DEBUG`
- **Technology-specific entries** (must be replaced with resolved values):
  - MI package (replaces `com.solace.connector.samples: TRACE`)
  - Binder package (replaces `com.solace.samples.binder.abc: DEBUG`)
  - Binder acknowledge sub-package (replaces `com.solace.samples.binder.abc.inbound.acknowledge: DEBUG`)

Document all five entries — static and replaceable — so `generate-mi.md` produces complete logging blocks.

---

## Section 8: Test Code

The integration test classes that verify end-to-end message flow through the connector. Each test starts a real Spring application context, then exercises a specific flow.

Only plan consumer tests if `VERIFIED_CONSUMER_SUPPORTED`; producer tests if `VERIFIED_PRODUCER_SUPPORTED`.

**Reference:** `BaseTest.java`, `BasicConsumerMessagingIT.java`, and `BasicProducerMessagingIT.java` in `abc-micro-integration/src/test/java/`.

### BaseTest.java — standalone generated file

`BaseTest.java` is a **separate output file**, not inlined into the test classes. Both `BasicConsumerMessagingIT` and `BasicProducerMessagingIT` extend it. It contains:

- Connection property constant strings (one per binder connection property from Section 2)
- The `setupBinderConnectionProperties(ConnectorArgsBuilder, WrapperClass)` method with one `argsBuilder.put()` call per property (from the alignment table in Section 2)

The property keys come from the binder's connection properties; the values come from the test-support wrapper/proxy getters, which expose the simplest authentication the test infrastructure supports (e.g., basic auth or API key for containers, simplest available option for proxy services).

Document the complete planned content of this file — constants, method signature, and every `put()` call — so `generate-mi.md` can generate it as a standalone file at `{MI_TARGET_TEST_SRC_DIR}/BaseTest.java`.

### Destination lifecycle & Solace queue provisioning

The ABC template manages test destinations through two mechanisms: `@SolaceQueue` annotations that provision Solace queues via the test extension, and SDK client calls that create technology-side destinations.

#### Solace queue provisioning via `@SolaceQueue`

The `@BeforeAll` method signature includes `@SolaceQueue` parameters that trigger Solace queue creation before tests run. Document which Solace queues each test class requires:

- **Consumer test** (`@BeforeAll`): `@SolaceQueue(name = "output-0") Queue output0` — provisions the Solace output queue for workflow-0.
- **Producer test** (`@BeforeAll`): `@SolaceQueue(name = "input-0") Queue input0` and `@SolaceQueue(name = "output-1") Queue output1` — provisions the Solace input queue for workflow-0 and the Solace output queue for workflow-1 loopback.

These are framework-level parameters — the queue names match the binding names in the test profile YAML. They stay the same across technologies.

#### Technology-side destination lifecycle

Based on the target SDK's destination model (from Section 6), document per test class:

**Consumer test:**
- **@BeforeAll** — create `source-destination` in the target technology. If the technology auto-creates destinations, document as no-op with rationale.
- **@AfterAll** — delete `source-destination`. If not needed, document why.
- **@AfterEach** — purge messages from `source-destination` to prevent cross-test interference.

**Producer test:**
- **@BeforeAll** — create both `source-destination` and `target-destination` in the target technology.
- **@AfterAll** — delete both destinations.
- **@AfterEach** — purge messages from both `source-destination` and `target-destination`.

If the technology doesn't support message deletion (e.g., append-only logs), document the alternative strategy.

### Consumer test flow — Tech to Solace

Tests workflow-0: a message originates in the target technology, the binder reads it, the connector framework routes it, and it arrives at a Solace queue.

- **Publish to tech**: the ABC template uses `testContainer.getClient().publishMessage(destination, new AbcOutboundMessage(payload, headers))`. Document the target SDK's equivalent — how to construct an outbound message, what payload format to use (plain text, JSON, byte[]), and how to publish it to the source destination.
- **Verify on Solace**: `solaceMessaging.consumeAndAssert(...)` reads from the Solace output queue. This is framework code and stays unchanged.

### Producer test flow — Solace to Tech

Tests workflow-0: a message is sent to Solace, the connector framework reads it, and the binder writes it to the target technology.

- **Publish to Solace**: uses `solaceMessaging.produceAsync(...)` to send a `TextMessage`. This is framework code and stays unchanged.
- **Verify on tech**: the ABC template uses `testContainer.getClient().pollMessage(destination)` returning `Optional<AbcInboundMessage>`, then asserts `inboundMessage.get().getPayload()`. Document the target SDK's poll/read method, the inbound message type, and how to extract and assert the payload.

### Batch producer test flow — Solace to Tech (batch)

Tests workflow-0 with Solace batch consumption enabled: multiple messages are sent to Solace, the Solace consumer collects them into a batch, the connector framework routes the batch, and the binder publishes each message to the target technology.

- **Publish to Solace**: `solaceMessaging.produceAsync(10, 0, ...)` sends 10 messages. The lambda creates each `TextMessage` with a unique payload and tracks payloads in an `ArrayList<String>`. This is framework code — stays unchanged.
- **Verify on tech**: poll all messages from `TARGET_DESTINATION` using the SDK's poll method. Collect received payloads into a list and assert `hasSize(10)` and `containsExactlyInAnyOrderElementsOf(testPayloads)` — order is not guaranteed because batch messages may arrive in any order.
- **Profile**: uses `BATCH_TEST_PROFILE` (`"messaging-batch-producer"`) which activates the batch producer YAML.

### Health check

Both test classes include `testHealthIndicatorsUp` which starts the connector and verifies the actuator health endpoint reports both binders as UP. The `HealthAssertions` utility checks `jsonPath("components.binders.components.{binder-name}")`. Document the resolved binder name for the JSON path.

### Test imports

Document the full set of technology-specific imports needed:
- Test extension class (FQ name)
- Wrapper/proxy class (FQ name)
- SDK inbound message type (for producer test verification)
- SDK outbound message type (for consumer test publishing)
- Any builder or factory classes needed for message construction
- `java.util.ArrayList` — for tracking test payloads in batch test
- `java.util.List` — for the payload list type

---

## Section 9: Output Assembly

### Source Transformation Guide

This section of `{MI_CLAUDE_MD}` is the sole authority `generate-mi.md` reads when adapting the ABC template code. It describes the technology-specific design decisions that drive code generation — not string replacements, but architectural adaptations.

Read every ABC template file listed in `generate-mi.md` Template Files table. Understand the role each class plays in the connector framework, then consolidate the decisions from Sections 1–8 into the guide.

Use fully resolved values in CLAUDE.md (e.g., write `MongoDB`, not `{TECH_NAME_CLASS_NAME_USE}`).

### MI CLAUDE.md section mapping

Create `{MI_CLAUDE_MD}` consolidating all findings. This file replaces the need for `generate-mi.md` to read the binder or test-support CLAUDE.md directly.

| CLAUDE.md section | Source |
|---|---|
| **What This Is** | Module name, binder mode (custom/third-party), verified capabilities |
| **Binder Identity** | `RESOLVED_BINDER_NAME`, `spring.binders` path, connection property prefix, extended binding namespace |
| **Binder Capability Modes** | Section 1 — which modes supported with evidence from `{BINDER_CLAUDE_MD}` |
| **Ack Modes** | Section 1 — consumer and producer ack modes with mapped enum values. Producer factory pattern: hardcoded `SYNC`, hardcoded `ASYNC_BY_CALLBACK_HEADER`, or dynamic (inspects extended producer properties per binding) depending on binder's async capability. `CONSUMER_ACK_BRIDGING_REQUIRED` flag. When bridging required: proprietary header name, ack class, ack/nack methods, and `pseudocode_consumer_ack.txt` as generation template reference |
| **Connection Properties Mapping** | Section 2 — full alignment table, one row per property |
| **Extended Binding Properties** | Section 3 — consumer and producer property tables (key, default, purpose) |
| **Message Payload Requirements** | Section 4 — `PRODUCER_PAYLOAD_REQUIREMENT` and `CONSUMER_PAYLOAD_FORMAT`, both directions, even if `none` |
| **Source Transformation Guide** | Section 5 — naming, bean registration, application.yml, application-operator.yml; Section 4 — interceptor decisions |
| **Test-Support Architecture** | Section 6 — architecture pattern, extension class (FQ), wrapper/proxy class (FQ), getter catalog by category, SDK client methods with full signatures |
| **Test Configuration Plan** | Section 7 — profiles, binding assignments, extended properties, logging. Includes batch producer profile (`messaging-batch-producer`) derived from producer profile with `batch-mode: true` and `batch-timeout: 1000` on `input-0` |
| **Test Java Plan** | Section 6 — `BaseTest` base class; Section 8 — `BaseTest.java` standalone file content, destination lifecycle, message flows (including batch producer test: `BATCH_TEST_PROFILE` constant, 10-message batch test method), health check, imports |
| **Generated Files** | Complete file list with target paths relative to `{MI_TARGET_MODULE_DIR}` — must include `BaseTest.java` as a standalone test source file, `application-operator.yml` as a main resource file, and `application-messaging-batch-producer.yml` as a test resource file (conditional on `VERIFIED_PRODUCER_SUPPORTED`) |

### Writing rules

- **Fully resolved values only** — no `{VARIABLE}` placeholders. Every binder name, property key, class name, and method signature must be the actual target technology value.
- **SDK method signatures** must include full parameter types and return types.
- **Test lifecycle methods** must include the complete planned Java statement, not just a description.
- **Cross-reference** all connection property keys against test-support getters. Flag any unmatched key.

### Validation checklist

Before finishing, verify:

- [ ] `RESOLVED_BINDER_NAME` comes from `spring.binders` — not derived from `TECH_NAME_LOWER`
- [ ] `VERIFIED_CONSUMER_SUPPORTED` and `VERIFIED_PRODUCER_SUPPORTED` determined from `{BINDER_CLAUDE_MD}` — not from configuration variables
- [ ] Every connection property maps to a getter or hardcoded value in the alignment table — no gaps
- [ ] `argsBuilder` injection has one `put()` per connection property
- [ ] Extended properties namespace is `spring.cloud.stream.{RESOLVED_BINDER_NAME}`
- [ ] SDK client methods documented with full signatures — or marked no-op with rationale
- [ ] Test YAML uses `{RESOLVED_BINDER_NAME}` everywhere — no `abc` remnants
- [ ] Test methods conditional on verified capabilities — consumer tests only if supported, producer tests only if supported
- [ ] Source transformation guide uses fully resolved values in all subsections
- [ ] No `abc`/`Abc`/`ABC` references remain in any planned content
- [ ] Payload requirements documented for both directions
- [ ] When consumer ack mode maps to `CLIENT_ACK_BY_CALLBACK_HEADER`: decision tree walked — custom binder short-circuits to `CONSUMER_ACK_BRIDGING_REQUIRED = false`; third-party binder reads `CONSUMER_ACK_NATIVE_SPRING_HEADER` from `{BINDER_CLAUDE_MD}` to determine bridging. When bridging required: proprietary header name, ack class, ack/nack methods documented, and `pseudocode_consumer_ack.txt` referenced as generation template
- [ ] Spring Boot auto-configuration exclusions identified and documented — or confirmed none exist for the target technology
- [ ] CLAUDE.md contains only resolved values — no placeholders
- [ ] `application-operator.yml` planned — bindings conditional on verified capabilities, extended properties listed for supported directions only, transform content types validated against payload requirements
- [ ] Batch producer test profile (`messaging-batch-producer`) planned as derivative of producer profile with `batch-mode: true` and `batch-timeout: 1000` on `input-0` — conditional on `VERIFIED_PRODUCER_SUPPORTED`
- [ ] Batch producer test method planned — 10 messages, `ArrayList` payload tracking, `containsExactlyInAnyOrderElementsOf` assertion
