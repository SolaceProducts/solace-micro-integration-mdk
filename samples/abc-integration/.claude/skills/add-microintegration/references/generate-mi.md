# Generate Micro-Integration Code

Instructions for generating Java source files, Spring Cloud Stream YAML configuration, and integration tests by transforming the ABC micro-integration template files.

The input to this step is `{MI_CLAUDE_MD}` (produced by the planning step) and configuration variables from `configuration.md` (parsed in SKILL.md Step 1). The output is generated source code in `{MI_TARGET_MODULE_DIR}`.

## Path constraint

All file operations must stay within exactly two directory trees:

1. **Workspace root** — the directory containing `configuration.md` (template source).
2. **`TARGET_PROJECT_FOLDER`** — as read from `configuration.md` (generated output).

Do **NOT** access, list, or search any parent directory of either location. When using `Glob` or `Grep`, always set the `path` parameter to one of these two roots or a subdirectory within them.

---

## Data Sources

Two data sources drive all code generation:

- **`{MI_CLAUDE_MD}`** — the MI design document produced by the planning step. Contains all resolved values, transformation rules, connection property mappings, test patterns, and SDK method signatures. This is the sole authority for what transformations to apply and what technology-specific values to use.
- **`configuration.md`** — user-provided configuration variables (paths, package names, artifact IDs, technology names). Already parsed and validated in SKILL.md Step 1. Provides structural values like target directory paths and build tool configuration.

---

## Template Files

All template source files are read from the ABC micro-integration module in the workspace:

| Template file (read from workspace root) | Purpose |
|---|---|
| `abc-micro-integration/src/main/java/com/solace/samples/microintegration/MicroIntegrationApplication.java` | Spring Boot main class with bean definitions |
| `abc-micro-integration/src/main/java/com/solace/samples/microintegration/AbcConsumerBindingCapabilitiesFactory.java` | Consumer binding capabilities factory |
| `abc-micro-integration/src/main/java/com/solace/samples/microintegration/AbcProducerBindingCapabilitiesFactory.java` | Producer binding capabilities factory |
| `abc-micro-integration/src/main/java/com/solace/samples/microintegration/AbcConsumerBindingMessageInterceptorFactory.java` | Consumer message interceptor factory |
| `abc-micro-integration/src/main/java/com/solace/samples/microintegration/AbcProducerBindingInterceptorFactory.java` | Producer message interceptor factory |
| `abc-micro-integration/src/main/java/com/solace/samples/microintegration/package-info.java` | Non-null annotations |
| `abc-micro-integration/src/main/resources/application.yml` | Main Spring Cloud Stream configuration |
| `abc-micro-integration/src/main/resources/application-operator.yml` | Sample operator configuration — bindings, workflows, extended binder properties, transforms, logging |
| `abc-micro-integration/src/test/java/com/solace/samples/microintegration/BasicConsumerMessagingIT.java` | Consumer integration tests (Tech → Solace) |
| `abc-micro-integration/src/test/java/com/solace/samples/microintegration/BasicProducerMessagingIT.java` | Producer integration tests (Solace → Tech) |
| `abc-micro-integration/src/test/java/com/solace/samples/microintegration/HealthAssertions.java` | Health endpoint assertion utility |
| `abc-micro-integration/src/test/resources/application-messaging-consumer.yml` | Test profile — consumer messaging |
| `abc-micro-integration/src/test/resources/application-messaging-producer.yml` | Test profile — producer messaging |
| `abc-micro-integration/src/test/resources/application-messaging-consumer-with-transforms.yml` | Test profile — consumer with transformations |
| `abc-micro-integration/src/test/resources/application-messaging-producer-with-transforms.yml` | Test profile — producer with transformations |
| `abc-micro-integration/src/test/java/com/solace/samples/microintegration/BaseTest.java` | Shared test base class — connection property constants and `setupBinderConnectionProperties()` |
| `.claude/skills/add-microintegration/pseudocode_consumer_ack.txt` | Consumer ack callback bridging template — `AcknowledgmentCallback` wrapper class and `@GlobalChannelInterceptor` bean. Only used when `CONSUMER_ACK_BRIDGING_REQUIRED` is `true` per `{MI_CLAUDE_MD}` (Ack Modes section). |

---

## Step 1: Generate main source files

For each template file below, read it from the workspace root, apply the general transformation rules from `{MI_CLAUDE_MD}` (Source Transformation Guide — General Rules section), then apply the file-specific transformations, and write the result to the target path.

### 1a. Capabilities factories

Generate only for verified capabilities documented in `{MI_CLAUDE_MD}` (Binder Capability Modes section).

**If consumer is supported**: Read `AbcConsumerBindingCapabilitiesFactory.java` and `AbcConsumerBindingMessageInterceptorFactory.java`. Apply general transformations. Set the consumer ack mode from `{MI_CLAUDE_MD}` (Ack Modes section). Apply file-specific transformations from `{MI_CLAUDE_MD}` (Source Transformation Guide — Capabilities Factories section). Write to `{MI_TARGET_SRC_DIR}/{TECH_NAME_CLASS_NAME_USE}ConsumerBindingCapabilitiesFactory.java` and `{MI_TARGET_SRC_DIR}/{TECH_NAME_CLASS_NAME_USE}ConsumerBindingMessageInterceptorFactory.java`.

**If producer is supported**: Read `AbcProducerBindingCapabilitiesFactory.java` and `AbcProducerBindingInterceptorFactory.java`. Apply general transformations. Implement the producer ack mode pattern from `{MI_CLAUDE_MD}` (Ack Modes section) — hardcode `SYNC` if no async capability, hardcode `ASYNC_BY_CALLBACK_HEADER` if only async, or dynamically determine from extended producer properties if the binder supports both modes. Apply file-specific transformations from `{MI_CLAUDE_MD}` (Source Transformation Guide — Capabilities Factories section). Write to `{MI_TARGET_SRC_DIR}/{TECH_NAME_CLASS_NAME_USE}ProducerBindingCapabilitiesFactory.java` and `{MI_TARGET_SRC_DIR}/{TECH_NAME_CLASS_NAME_USE}ProducerBindingInterceptorFactory.java`.

### 1b. Main application class

Read `MicroIntegrationApplication.java`. Apply general transformations. Apply file-specific transformations from `{MI_CLAUDE_MD}` (Source Transformation Guide — MicroIntegrationApplication section), including conditional bean definitions based on verified capabilities and the `@GlobalChannelInterceptor` ack bridging bean if `CONSUMER_ACK_BRIDGING_REQUIRED` is `true` per `{MI_CLAUDE_MD}` (Ack Modes section).

Write to `{MI_TARGET_SRC_DIR}/MicroIntegrationApplication.java`.

### 1c. package-info.java

Read `package-info.java`. Replace the package declaration using the general transformation rules. Write to `{MI_TARGET_SRC_DIR}/package-info.java`.

### 1d. Consumer ack callback bridging class

**Only generate if `CONSUMER_ACK_BRIDGING_REQUIRED` is `true` in `{MI_CLAUDE_MD}` (Ack Modes section).**

Read `.claude/skills/add-microintegration/pseudocode_consumer_ack.txt` as the structural template. Replace the `Abc` pseudocode placeholders with resolved values from `{MI_CLAUDE_MD}` (Ack Modes section):

| Pseudocode placeholder | Replace with |
|---|---|
| `AbcAcknowledgmentCallback` | `{TECH_NAME_CLASS_NAME_USE}AcknowledgmentCallback` |
| `AbcAcknowledgmentCallbackOrAcknowledgeableMessage` | Proprietary ack class from `{MI_CLAUDE_MD}` |
| `this.acknowledgeable.ack()` | Proprietary ack method call from `{MI_CLAUDE_MD}` |
| `this.acknowledgeable.nack()` | Proprietary nack method call from `{MI_CLAUDE_MD}` |
| `AbcHeaders.ORIGINAL_MESSAGE_WITH_ACKNOWLEDGMENT_CALLBACK` | Proprietary ack header constant from `{MI_CLAUDE_MD}` |
| Package declaration | `{MI_PACKAGE}` |

The template contains two artifacts: the `AcknowledgmentCallback` wrapper class (write to `{MI_TARGET_SRC_DIR}/{TECH_NAME_CLASS_NAME_USE}AcknowledgmentCallback.java`) and the `@GlobalChannelInterceptor` bean (goes into `MicroIntegrationApplication.java`, handled in Step 1b).

---

## Step 2: Generate application.yml

**Blocked by:** Step 1 must complete successfully.

Read `abc-micro-integration/src/main/resources/application.yml`. This file is largely boilerplate — the 20 input/output bindings, consumer groups, workflow definitions, and Solace settings are identical across all connectors. Apply transformations from `{MI_CLAUDE_MD}` (Source Transformation Guide — application.yml section).

Write to `{MI_TARGET_RESOURCES_DIR}/application.yml`.

### Step 2b: Generate application-operator.yml

Read `abc-micro-integration/src/main/resources/application-operator.yml`. This is the operator-facing sample configuration — unlike `application.yml` (pure framework boilerplate), it is **capability-conditional** and contains technology-specific content. Apply transformations from `{MI_CLAUDE_MD}` (Source Transformation Guide — application-operator.yml section).

#### Bindings & workflows — conditional on verified capabilities

Adapt the binding layout and workflow count based on `VERIFIED_CONSUMER_SUPPORTED` and `VERIFIED_PRODUCER_SUPPORTED` from `{MI_CLAUDE_MD}` (Binder Capability Modes section):

**Producer only** (Solace → Tech):
- `input-0: binder: solace` — Solace destination is generated by PubSubPlusExtension
- `output-0: binder: {RESOLVED_BINDER_NAME}`, `destination: target-destination`
- Remove `input-1` and `output-1` bindings entirely
- Single workflow: `0: enabled: false`
- Remove workflow `1` entirely

**Consumer only** (Tech → Solace):
- `input-0: binder: {RESOLVED_BINDER_NAME}`, `destination: source-destination`
- `output-0: binder: solace` — Solace destination is generated by PubSubPlusExtension
- Remove `input-1` and `output-1` bindings entirely
- Single workflow: `0: enabled: false`
- Remove workflow `1` entirely

**Both directions**:
- Flow 0 — Solace → Tech (producer direction): `input-0: binder: solace`, `output-0: binder: {RESOLVED_BINDER_NAME}`, `destination: target-destination`
- Flow 1 — Tech → Solace (consumer direction): `input-1: binder: {RESOLVED_BINDER_NAME}`, `destination: source-destination`, `output-1: binder: solace`
- Two workflows: `0: enabled: false` and `1: enabled: true`

#### Extended binder properties

Under `spring.cloud.stream.{RESOLVED_BINDER_NAME}.default`, include extended binding properties from `{MI_CLAUDE_MD}` (Extended Binding Properties section). Show properties as **commented-out YAML with descriptions**:

- **Consumer properties**: include only when `VERIFIED_CONSUMER_SUPPORTED` is `true`. List each property under `consumer:` with its default value and a comment describing its purpose.
- **Producer properties**: include only when `VERIFIED_PRODUCER_SUPPORTED` is `true`. List each property under `producer:` with its default value and a comment describing its purpose.

#### Binder connection properties

Include the target technology's connection properties from `{MI_CLAUDE_MD}` (Connection Properties Mapping section, sourced from the Configuration section of `{BINDER_CLAUDE_MD}`). Show all connection properties as **commented-out YAML with descriptions** so the operator knows how to configure connectivity to the backend technology.

Use the **real property keys** from the Connection Properties Mapping alignment table — these are the same keys injected via `argsBuilder.put()` in the test `BaseTest.java`. Place them under the binder's environment namespace. For each connection property, include:

- The actual YAML key path as documented in `{BINDER_CLAUDE_MD}` (Configuration section)
- A comment describing its purpose
- A placeholder or example value appropriate to the property type

The test profiles (`application-messaging-*.yml`) inject these same properties at runtime via `argsBuilder`; the operator YAML shows operators the equivalent static configuration they would use in production.

#### Transforms — commented-out example on workflow 0

Include a commented-out transform block on workflow `0`, following the template's structure:

```yaml
#        transform:
#          enabled: true
#          source-payload:
#            content-type: "{SOURCE_CONTENT_TYPE}"
#          target-payload:
#            content-type: "{TARGET_CONTENT_TYPE}"
#          expressions:
#            - transform: "..."
```

**Content-type rules**: check `{MI_CLAUDE_MD}` (Message Payload Requirements section). If the binder's payload requirements confirm XML support, use `"application/xml"` for `source-payload` content-type. Otherwise, use `"application/json"` for both `source-payload` and `target-payload`. It is acceptable to show `"application/json"` for both source and target.

Include sample SpEL expressions from the template (variable intermediates, payload mapping, header mapping).

#### Logging

Replace template logging packages with resolved values from `{MI_CLAUDE_MD}` (Source Transformation Guide):

- `com.solace.connector.samples` → MI package (from `{MI_CLAUDE_MD}`)
- `com.solace.samples.binder.abc` → binder package (from `{MI_CLAUDE_MD}`)

Keep `com.solace.connector.core` and `com.solace.spring.cloud.stream.binder` unchanged.

#### Management & remaining sections

Carry over the `solace.java`, `management`, `solace.default.producer`, and `server` sections from the template. The `solace.java` section provides Solace event broker connection details (`connect-retries`, `reconnect-retries`, and commented-out `host`, `msg-vpn`, `client-username`, `client-password` placeholders) — include it exactly as it appears in the template, as a sibling of `solace.connector` under the top-level `solace:` key. No `abc`/`Abc`/`ABC` references should remain.

Write to `{MI_TARGET_RESOURCES_DIR}/application-operator.yml`.

---

## Step 3: Generate test source files

**Blocked by:** Step 2 must complete successfully.

### 3a. HealthAssertions.java

Read `HealthAssertions.java`. Apply general transformations. Apply file-specific transformations from `{MI_CLAUDE_MD}` (Source Transformation Guide — HealthAssertions section).

Write to `{MI_TARGET_TEST_SRC_DIR}/HealthAssertions.java`.

### 3b. BaseTest.java — shared test base class

Read `BaseTest.java`. Apply general transformations (package declaration, imports). Use `{MI_CLAUDE_MD}` (Test Java Plan — BaseTest section) for all adaptations:

- **Connection property constants**: replace the ABC constant strings with one `private static final String` per binder connection property from the Connection Properties Mapping in `{MI_CLAUDE_MD}`. Each constant holds the flat property key used in `argsBuilder.put()`.
- **`setupBinderConnectionProperties()` method**: replace the ABC parameter type (`AbcTestContainerWithConnectedClient`) with the target technology's wrapper/proxy class from `{MI_CLAUDE_MD}` (Test-Support Architecture section). Replace the method body with one `argsBuilder.put()` call per connection property, using the alignment table from `{MI_CLAUDE_MD}` (Connection Properties Mapping section) — getter-backed values call the wrapper method, static values use hardcoded strings.

Write to `{MI_TARGET_TEST_SRC_DIR}/BaseTest.java`.

### 3c. Consumer integration test

**Only generate if `VERIFIED_CONSUMER_SUPPORTED` in `{MI_CLAUDE_MD}`.**

Read `BasicConsumerMessagingIT.java`. Apply general transformations. Use `{MI_CLAUDE_MD}` (Test Java Plan section) for all test-specific adaptations: imports, `@ExtendWith` extension classes, lifecycle methods (@BeforeAll/@AfterAll/@AfterEach with SDK calls or no-op), and test verification (publish to tech via SDK, read from Solace). Connection property constants and `setupBinderConnectionProperties()` are inherited from `BaseTest` (generated in Step 3b).

Write to `{MI_TARGET_TEST_SRC_DIR}/BasicConsumerMessagingIT.java`.

### 3d. Producer integration test

**Only generate if `VERIFIED_PRODUCER_SUPPORTED` in `{MI_CLAUDE_MD}`.**

Read `BasicProducerMessagingIT.java`. Apply general transformations. Use `{MI_CLAUDE_MD}` (Test Java Plan section) for all test-specific adaptations: imports, `@ExtendWith` extension classes, lifecycle methods, and test verification (send to Solace, poll from tech via SDK). Connection property constants and `setupBinderConnectionProperties()` are inherited from `BaseTest` (generated in Step 3b).

The producer test class must also include:

- **`BATCH_TEST_PROFILE` constant**: `"messaging-batch-producer"` — references the batch producer YAML profile.
- **Batch producer test method** (template's `solaceToAbcMessagingBatchTest` — apply general transformation rules to rename `Abc` → target technology): follows the same structure as the basic producer test but uses `BATCH_TEST_PROFILE`, sends 10 messages via `solaceMessaging.produceAsync(10, 0, ...)`, tracks payloads in an `ArrayList<String>`, and verifies all 10 arrive at `TARGET_DESTINATION` by polling with the SDK's read/poll method. Assert `hasSize(10)` and `containsExactlyInAnyOrderElementsOf(testPayloads)`.
- **Additional imports**: `java.util.ArrayList` and `java.util.List` (needed for batch test payload tracking).

Write to `{MI_TARGET_TEST_SRC_DIR}/BasicProducerMessagingIT.java`.

---

## Step 4: Generate test resource files

**Blocked by:** Step 3 must complete successfully.

### 4a. Consumer test profiles

**Only generate if `VERIFIED_CONSUMER_SUPPORTED` in `{MI_CLAUDE_MD}`.**

Read `abc-micro-integration/src/test/resources/application-messaging-consumer.yml`. Apply binder name substitutions, connection properties comment block, binding direction assignments, extended consumer properties, and logging packages from `{MI_CLAUDE_MD}` (Test Configuration Plan section). Write to `{MI_TARGET_TEST_RESOURCES_DIR}/application-messaging-consumer.yml`.

Read `abc-micro-integration/src/test/resources/application-messaging-consumer-with-transforms.yml`. Apply same substitutions plus transformation configuration. Write to `{MI_TARGET_TEST_RESOURCES_DIR}/application-messaging-consumer-with-transforms.yml`.

### 4b. Producer test profiles

**Only generate if `VERIFIED_PRODUCER_SUPPORTED` in `{MI_CLAUDE_MD}`.**

Read `abc-micro-integration/src/test/resources/application-messaging-producer.yml`. Apply binder name substitutions, connection properties comment block, binding direction assignments, extended producer properties, and logging packages from `{MI_CLAUDE_MD}` (Test Configuration Plan section). Write to `{MI_TARGET_TEST_RESOURCES_DIR}/application-messaging-producer.yml`.

Read `abc-micro-integration/src/test/resources/application-messaging-producer-with-transforms.yml`. Apply same substitutions plus transformation configuration. Write to `{MI_TARGET_TEST_RESOURCES_DIR}/application-messaging-producer-with-transforms.yml`.

Generate `application-messaging-batch-producer.yml` by copying the already-generated `application-messaging-producer.yml` and adding Solace batch consumer properties to the `input-0` binding: `consumer.batch-mode: true` and `consumer.batch-timeout: 1000`. All other content (bindings, workflows, extended properties, connection properties comment block, logging) remains identical. Write to `{MI_TARGET_TEST_RESOURCES_DIR}/application-messaging-batch-producer.yml`.


