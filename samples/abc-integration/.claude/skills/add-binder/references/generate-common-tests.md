# Generate Common Test Infrastructure

This file generates the shared test infrastructure that all capability-specific test classes (producer, consumer) depend on: profile-specific application YAML files, TestApplication, AbstractBase with container lifecycle, application-multibinder.yml, and MultiBinderIT. All shared binder infrastructure files (from `generate-shared.md`) are already generated before this file is invoked. Capability-specific handlers (producer, consumer) are generated **after** this file completes, in Steps 6 and 7 of SKILL.md.

---

## Step Execution Rules

**Step execution order is mandatory.** Each step must complete successfully before the next step begins. Do not skip ahead, parallelize steps, or begin a step while a previous step is incomplete. If a step fails, stop and report the failure — do not proceed to subsequent steps.

---

## Inputs

These are available from the calling context (SKILL.md):

- **Configuration variables and derived values** — all variables from `configuration.md` and the derived values table in SKILL.md
- **`{BINDER_TARGET_MODULE_DIR}/CLAUDE.md`** — primary reference for YAML configuration structure, SDK client details, connection properties, design decisions, and testing notes
- **`configuration.md`** — first fallback when CLAUDE.md is incomplete or ambiguous; contains concrete values such as `CLIENT_SDK_DEPENDENCY`, naming variables, and `TECH_OVERVIEW_PATH`
- **Technology overview report** at `{TECH_OVERVIEW_PATH}` (referenced in `configuration.md`) — second fallback for full SDK details (class names, method signatures, exceptions, data types)
- **ABC template project** (`spring-cloud-stream-binder-abc/src/test/`) — working reference implementation showing how binder integration tests are structured: container lifecycle, Spring context bootstrap, StreamBridge publishing, SDK-based verification

---

## Template Reference

Template path prefixes used in this file:

| Prefix | Path |
|---|---|
| `ABC_TEST` | `spring-cloud-stream-binder-abc/src/test/java/com/solace/samples/binder/abc` |
| `ABC_TEST_RES` | `spring-cloud-stream-binder-abc/src/test/resources` |

### ABC test templates used as structural references

```
{ABC_TEST}/
├── AbstractBaseWithOnTheFlyContainerIT.java   # Abstract base — container lifecycle, connection injection, destination management, profile activation
├── MultiBinderIT.java                          # Multi-binder test — two binder instances, both healthy
└── app/
    └── TestApplication.java                    # Test Spring Boot app — consumer/supplier beans, error channel, helper methods
{ABC_TEST_RES}/
├── application-consumer.yml                    # Consumer profile config — consumer bindings and extended consumer properties only
├── application-async-producer.yml              # Async producer profile config — producer bindings and extended producer properties only
├── application-sync-producer.yml              # Sync producer profile config — producer bindings, sync mode, extended producer properties only
└── application-multibinder.yml                 # Multi-binder profile config — two binder instances with separate bindings
```

These are **structural references only** — each step reads the corresponding ABC template to understand the pattern, then generates the target file using CLAUDE.md for all technology-specific values.

---

## Transformation Rules

These rules apply to **every** file generated from an ABC template in this file. Verify each rule before writing any file.

1. **Package**: Replace `com.solace.samples.binder.abc` with `{BINDER_PACKAGE}` in all package declarations and imports.
2. **Class names**: Replace `Abc` prefix with `{TECH_NAME_CLASS_NAME_USE}` in class names, constructors, and cross-references.
3. **Binder name**: Replace `abc` with `{BINDER_NAME}` in per-binding `binder`, multi-binder `type`, health endpoint JSON paths, and log messages.
4. **SDK alignment**: SDK client class, read/poll methods, message types, and accessor methods must match CLAUDE.md SDK Client section. If CLAUDE.md is incomplete or ambiguous, consult `configuration.md` first, then the technology overview report at `{TECH_OVERVIEW_PATH}` (referenced in `configuration.md`). For test structure and patterns (lifecycle, assertions, StreamBridge usage), refer to the ABC template project as the working example. Do not carry over ABC-specific API calls — adapt the pattern to the target SDK.
5. **No leftover template references**: Generated files must not contain `com.solace.samples`, `abc`, `Abc`, or `ABC` (except in comments that explicitly reference the template origin).
6. **All imports explicit**: No wildcard imports.

---

## Step 1: Create `.env` file (proxy-service-with-client only)

**Blocked by:** Nothing — this is the first step.

**Skip this step entirely if `TEST_SUPPORT_ARCHITECTURE` is `container-with-client`.** Container-based test-support modules manage their own lifecycle and connection values — no `.env` file is needed.

**When `TEST_SUPPORT_ARCHITECTURE` is `proxy-service-with-client`:** The proxy reads connection and authentication values from environment variables via dotenv-java. The `.env` file must exist in the working directory when tests run. Since Maven Failsafe executes integration tests with the module root as the working directory, the `.env` file must be placed in `{BINDER_TARGET_MODULE_DIR}/`.

#### Source

Copy the `.env` file from the test-support module: `{TEST_SUPPORT_MODULE_DIR}/.env` → `{BINDER_TARGET_MODULE_DIR}/.env`

If `{TEST_SUPPORT_MODULE_DIR}/.env` does not exist, read the test-support CLAUDE.md "Remote backend details" section to determine the required environment variables, and generate a `.env` file following the same format as the ABC template (`abc-test-support/.env`):

- Header block with technology name, security note, and purpose
- One variable per connection/auth parameter using `{TECH_NAME_UPPER}_SERVICE_*` naming
- Placeholder values (not real credentials)

#### Validation

Verify `{BINDER_TARGET_MODULE_DIR}/.env` exists and contains at least one non-comment, non-empty line.

---

## Step 2: Generate profile-specific test application YAML files

**Blocked by:** Step 1 must complete successfully (or be skipped for container-based architecture). All binder implementation files must already exist.

Instead of a single `application.yml`, generate **separate profile-specific YAML files** — one for consumer tests and one for producer tests. Each messaging integration test class activates its own profile via `getActiveProfiles()`, so each YAML file contains only the bindings and extended properties relevant to that capability. This avoids loading unused function definitions and bindings when only one capability is being tested.

Read `ABC_TEST_RES/application-consumer.yml` and `ABC_TEST_RES/application-async-producer.yml` for **structural reference only** — to understand what sections a binder test configuration needs for each capability.

**Do NOT copy-paste the ABC templates.** Generate the target files by assembling from CLAUDE.md.

### Step 2a: Generate `application-consumer.yml` (conditional)

**Skip this sub-step if `BINDER_TYPE` does not contain `consumer`.**

Generate `{BINDER_TARGET_TEST_RESOURCES_DIR}/application-consumer.yml`:

| Output | Source in CLAUDE.md |
|---|---|
| `{BINDER_TARGET_TEST_RESOURCES_DIR}/application-consumer.yml` | Configuration section (Sections 1, 2, 4) + Testing Notes |

#### Assembly rules

1. **Connection properties block (commented out):**
   Read CLAUDE.md Configuration **Section 1** (Connection). Write each connection property as a YAML comment with an explanation that values are injected via command-line arguments by the abstract base class in `@BeforeAll`. Include the full connection structure (nested auth block if present) — all commented out.

2. **Spring Cloud Function definition:**
   ```yaml
   spring:
     cloud:
       function:
         definition: "testConsumer"
   ```
   Only the consumer function — no `testSupplier`.

3. **Spring Cloud Stream bindings:**
   Read CLAUDE.md Configuration **Section 2** (Bindings and extended properties) for the binding structure. Create **only the consumer binding**:

   - `testConsumer-in-0` — destination: `source-destination`, binder: `{BINDER_NAME}`, group: `test-group`

4. **Extended binding properties (consumer only):**
   Read CLAUDE.md Configuration **Section 2** for the `{BINDER_NAME}:` → `default:` → `consumer:` structure. Write only the **consumer** extended binding properties with test-appropriate values (e.g., shorter polling intervals for faster test execution). Do not include the `producer:` block.

5. **Management/health actuator:**
   Read CLAUDE.md Configuration **Section 4** (Health actuator). Write the management block verbatim.

6. **Logging:**
   ```yaml
   logging:
     level:
       root: INFO
       {BINDER_PACKAGE}: DEBUG
   ```
   Replace `{BINDER_PACKAGE}` with the actual package value (dots, not slashes).

**Validation:** Every YAML key must trace to a CLAUDE.md Configuration section. No hardcoded technology-specific values that don't come from CLAUDE.md.

### Step 2b: Generate `application-async-producer.yml` (conditional)

**Skip this sub-step if `BINDER_TYPE` does not contain `producer` or if `PRODUCER_IS_ASYNC=false`.**

Generate `{BINDER_TARGET_TEST_RESOURCES_DIR}/application-async-producer.yml`:

| Output | Source in CLAUDE.md |
|---|---|
| `{BINDER_TARGET_TEST_RESOURCES_DIR}/application-async-producer.yml` | Configuration section (Sections 1, 2, 4) + Testing Notes |

#### Assembly rules

1. **Connection properties block (commented out):**
   Same as Step 2a — read CLAUDE.md Configuration **Section 1** (Connection). Write each connection property as a YAML comment.

2. **Spring Cloud Function definition:**
   ```yaml
   spring:
     cloud:
       function:
         definition: "testSupplier"
   ```
   Only the supplier function — no `testConsumer`.

3. **Spring Cloud Stream bindings:**
   Read CLAUDE.md Configuration **Section 2** for the binding structure. Create **only the producer binding**:

   - `testSupplier-out-0` — destination: `target-destination`, binder: `{BINDER_NAME}`, producer: `error-channel-enabled: true`, `use-native-encoding: true`

4. **Extended binding properties (producer only):**
   Read CLAUDE.md Configuration **Section 2** for the `{BINDER_NAME}:` → `default:` → `producer:` structure. Write only the **producer** extended binding properties with test-appropriate values (e.g., shorter write timeouts for faster test execution). Do not include the `consumer:` block.

5. **Management/health actuator:**
   Read CLAUDE.md Configuration **Section 4** (Health actuator). Write the management block verbatim.

6. **Logging:**
   ```yaml
   logging:
     level:
       root: INFO
       {BINDER_PACKAGE}: DEBUG
   ```
   Replace `{BINDER_PACKAGE}` with the actual package value (dots, not slashes).

**Validation:** Every YAML key must trace to a CLAUDE.md Configuration section. No hardcoded technology-specific values that don't come from CLAUDE.md.

### Step 2c: Generate `application-sync-producer.yml` (conditional)

**Skip this sub-step if `BINDER_TYPE` does not contain `producer`.**

Generate `{BINDER_TARGET_TEST_RESOURCES_DIR}/application-sync-producer.yml`:

| Output | Source in CLAUDE.md |
|---|---|
| `{BINDER_TARGET_TEST_RESOURCES_DIR}/application-sync-producer.yml` | Configuration section (Sections 1, 2, 4) + Testing Notes |

#### Assembly rules

1. **Connection properties block (commented out):**
   Same as Step 2a — read CLAUDE.md Configuration **Section 1** (Connection). Write each connection property as a YAML comment.

2. **Spring Cloud Function definition:**
   ```yaml
   spring:
     cloud:
       function:
         definition: "testSupplier"
   ```
   Only the supplier function — no `testConsumer`.

3. **Spring Cloud Stream bindings:**
   Read CLAUDE.md Configuration **Section 2** for the binding structure. Create **only the producer binding**:

   - `testSupplier-out-0` — destination: `target-destination`, binder: `{BINDER_NAME}`, producer: `error-channel-enabled: true`, `use-native-encoding: true`

4. **Extended binding properties (producer only):**
   Read CLAUDE.md Configuration **Section 2** for the `{BINDER_NAME}:` → `default:` → `producer:` structure. Write only the **producer** extended binding properties with test-appropriate values (e.g., shorter write timeouts for faster test execution).

   **`async` property handling:** When `PRODUCER_IS_ASYNC=true`, include `async: false` to explicitly force synchronous mode for this test profile. When `PRODUCER_IS_ASYNC=false`, omit the `async` property entirely — the binder has no async capability and is always synchronous.

5. **Management/health actuator:**
   Read CLAUDE.md Configuration **Section 4** (Health actuator). Write the management block verbatim.

6. **Logging:**
   ```yaml
   logging:
     level:
       root: INFO
       {BINDER_PACKAGE}: DEBUG
   ```
   Replace `{BINDER_PACKAGE}` with the actual package value (dots, not slashes).

**Validation:** Every YAML key must trace to a CLAUDE.md Configuration section. No hardcoded technology-specific values that don't come from CLAUDE.md.

---

## Step 3: Generate TestApplication.java

**Blocked by:** Step 2 must complete successfully.

Read `ABC_TEST/app/TestApplication.java` for the structural pattern.

| Blueprint | Output |
|---|---|
| `{ABC_TEST}/app/TestApplication.java` | `{BINDER_TARGET_TEST_SRC_DIR}/app/TestApplication.java` |

**Transformation rules (in addition to general rules):**

- Package declaration → `{BINDER_PACKAGE}.app`
- Class name stays `TestApplication` — this is a universal test harness
- Bean names stay: `testConsumer`, `otherTestConsumer`, `testSupplier` — they match the binding names in the generated profile YAML files
- `@SpringBootApplication` annotation stays
- All helper methods are universal (no SDK-specific logic):
  - `receiveMessage(long timeout, TimeUnit unit)` — poll from `receivedMessages` queue
  - `clearReceivedMessages()` — clear queue
  - `readMessageReceivedOnErrorChannel(long timeout, TimeUnit unit)` — poll from error queue
  - `clearErrorChannelMessages()` — clear error queue
- Error channel subscription (`DirectChannel` bean with `ErrorMessage` capture) — copy pattern as-is with package replacement
- The `testConsumer` bean's `reject_me` and `cause_error` header handling — copy as-is (tests consumer ack/error behaviour universally)
- **Auto-configuration exclusion**: If CLAUDE.md **Key Design Decisions** lists `SPRING_BOOT_AUTOCONFIG_EXCLUDES`, add `@SpringBootApplication(exclude = {…})` listing each class. The binder manages its own client via `IOUtil` and the test-support extension provides the container/proxy with a connected client — Spring Boot's technology-specific auto-configuration must not create competing client beans or health indicators.

**Note on unused beans:** When `BINDER_TYPE` is `producer` only, the `testConsumer` and `otherTestConsumer` beans have no matching binding and remain idle. When `BINDER_TYPE` is `consumer` only, no `testSupplier` is needed. Spring does not fail on unused function beans — they simply never activate. Generate all beans unconditionally to keep the TestApplication universal.

---

## Step 4: Generate AbstractBaseWithOnTheFlyContainerIT.java

**Blocked by:** Step 3 must complete successfully.

Read `ABC_TEST/AbstractBaseWithOnTheFlyContainerIT.java` for the structural pattern.

| Blueprint | Output |
|---|---|
| `{ABC_TEST}/AbstractBaseWithOnTheFlyContainerIT.java` | `{BINDER_TARGET_TEST_SRC_DIR}/AbstractBaseWithOnTheFlyContainerIT.java` |

**Transformation rules (in addition to general rules):**

- Package declaration → `{BINDER_PACKAGE}`
- **Extension class**: `@ExtendWith(AbcSimpleContainerTestExtension.class)` → `@ExtendWith({extension class})` where the extension class is read from CLAUDE.md **Testing Notes** section (field: "Test-support extension class"). The extension class depends on the test-support architecture:
  - `container-with-client` → e.g., `{Tech}SimpleContainerTestExtension`
  - `proxy-service-with-client` → e.g., `{Tech}ProxyTestExtension`
- **Wrapper/proxy class**: `AbcTestContainerWithConnectedClient` → wrapper or proxy class from CLAUDE.md **Testing Notes** section (field: "Test-support wrapper class"). This appears as the parameter type in `@BeforeAll`, `@AfterAll`, and `buildConnectionArgs()` methods. The class depends on the test-support architecture:
  - `container-with-client` → e.g., `{Tech}TestContainerWithConnectedClient`
  - `proxy-service-with-client` → e.g., `{Tech}ProxyServiceWithClient`
- **SDK client class**: `AbcClient` → SDK client class from CLAUDE.md **SDK Client** section. This is the type of the `simpleClient` field.
- **System property constants**: Replace the ABC constants:
  ```java
  static final String BINDER_BASE_URL_PROPERTY = "abc.base-url";
  static final String BINDER_AUTHENTICATION_TYPE_PROPERTY = "abc.authentication.type";
  // etc.
  ```
  with constants derived from CLAUDE.md **Testing Notes** "System property key → getter mapping table". Each row in that table becomes one `static final String` constant. The property key uses the `{BINDER_CONNECTION_PREFIX}` prefix (e.g., `{BINDER_CONNECTION_PREFIX}.base-url`).

- **`getActiveProfiles()` method**: The default implementation returns an empty array. Subclasses override this to activate a specific profile:
  - `BinderSyncProducerMessagingIT` returns `{"sync-producer"}` → loads `application-sync-producer.yml`
  - `BinderAsyncProducerMessagingIT` returns `{"async-producer"}` → loads `application-async-producer.yml`
  - `BinderConsumerMessagingIT` returns `{"consumer"}` → loads `application-consumer.yml`
  - `MultiBinderIT` returns `{"multibinder"}` → loads `application-multibinder.yml`

  This method is called in `@BeforeAll` via `SpringApplicationBuilder.profiles(getActiveProfiles())`. Copy this pattern as-is from the ABC template.

- **`@BeforeAll` body**: Get the client reference and create the 3 test destinations, then start the Spring application context with profiles and connection args:
  ```java
  simpleClient = containerWrapper.getClient();
  createDestination(SOURCE_DESTINATION);
  createDestination(TARGET_DESTINATION);
  createDestination(DYNAMIC_DESTINATION);

  applicationContext = new SpringApplicationBuilder(getApplicationClass())
      .profiles(getActiveProfiles())
      .run(buildConnectionArgs(containerWrapper));
  ```
  The `createDestination` call uses the SDK's destination creation method from CLAUDE.md **SDK Client** section (field: "SDK destination management methods"). If the SDK has no `createDestination` equivalent, the `createDestination` helper becomes a no-op.

- **`@AfterEach`**: Clear messages from all 3 destinations using the SDK's equivalent of `deleteAllMessages(destination)` from CLAUDE.md **SDK Client** section. If the SDK has no message clearing method, this becomes a no-op.

- **`@AfterAll`**: Close the application context, then delete all 3 destinations using the SDK's equivalent of `deleteDestination(destination)` from CLAUDE.md **SDK Client** section, then clear all system properties:
  ```java
  System.clearProperty(CONSTANT_NAME);
  ```
  for each constant defined above. If the SDK has no `deleteDestination` equivalent, the delete calls become no-ops.

- **Destination constants** stay the same: `SOURCE_DESTINATION = "source-destination"`, `TARGET_DESTINATION = "target-destination"`, `DYNAMIC_DESTINATION = "dynamic-destination"`

---

## Step 5: Generate application-multibinder.yml

**Blocked by:** Step 4 must complete successfully.

Read `ABC_TEST_RES/application-multibinder.yml` for **structural reference only** — to understand how a multi-binder test profile is configured.

| Blueprint | Output |
|---|---|
| `{ABC_TEST_RES}/application-multibinder.yml` | `{BINDER_TARGET_TEST_RESOURCES_DIR}/application-multibinder.yml` |

**Do NOT copy-paste the ABC template.** Generate by assembling from CLAUDE.md:

| Output | Source in CLAUDE.md |
|---|---|
| `{BINDER_TARGET_TEST_RESOURCES_DIR}/application-multibinder.yml` | Configuration section (Section 3 — Multi-binder + Section 4 — Health actuator) + Testing Notes |

#### Assembly rules

1. **Spring Cloud Function definition:**
   ```yaml
   spring:
     cloud:
       function:
         definition: "testConsumer;otherTestConsumer"
   ```

2. **Multi-binder instances:**
   Read CLAUDE.md Configuration **Section 3** (Multi-binder). Define two binder instances:
   ```yaml
   spring:
     cloud:
       stream:
         binders:
           {BINDER_NAME}-1:
             type: {BINDER_NAME}
           {BINDER_NAME}-2:
             type: {BINDER_NAME}
   ```
   Connection environment properties for each instance are **commented out** — they are injected at runtime by `MultiBinderIT.buildConnectionArgs()`.

3. **Bindings:**
   Two consumer bindings, each routed to a different binder instance:
   - `testConsumer-in-0` — destination: `source-destination`, binder: `{BINDER_NAME}-1`, group: `test-group-1`
   - `otherTestConsumer-in-0` — destination: `source-destination`, binder: `{BINDER_NAME}-2`, group: `test-group-2`

   Default binder:
   ```yaml
         default-binder: {BINDER_NAME}-1
   ```

4. **Management/health actuator:**
   Read CLAUDE.md Configuration **Section 4** (Health actuator). Write the management block verbatim.

5. **Logging:**
   ```yaml
   logging:
     level:
       root: INFO
       {BINDER_PACKAGE}: DEBUG
   ```
   Replace `{BINDER_PACKAGE}` with the actual package value (dots, not slashes).

---

## Step 6: Generate MultiBinderIT.java

**Blocked by:** Step 5 must complete successfully.

Read `ABC_TEST/MultiBinderIT.java` for the structural pattern.

| Blueprint | Output |
|---|---|
| `{ABC_TEST}/MultiBinderIT.java` | `{BINDER_TARGET_TEST_SRC_DIR}/MultiBinderIT.java` |

**Transformation rules (in addition to general rules):**

- Package declaration → `{BINDER_PACKAGE}`
- `extends AbstractBaseWithOnTheFlyContainerIT` — stays as-is (same class name, same package)
- **Wrapper/proxy class**: `AbcTestContainerWithConnectedClient` → wrapper or proxy class from CLAUDE.md **Testing Notes** section (field: "Test-support wrapper class"). This is the parameter type for the `buildConnectionArgs()` override.

- **`getActiveProfiles()` override**: Returns `new String[]{"multibinder"}` — activates `application-multibinder.yml`. Copy as-is.

- **`buildConnectionArgs()` override**: Builds command-line arguments that inject connection properties for **each binder instance** under its multi-binder environment prefix. The ABC template uses:
  ```java
  String abc1Prefix = "spring.cloud.stream.binders.abc-1.environment.";
  String abc2Prefix = "spring.cloud.stream.binders.abc-2.environment.";
  ```
  Replace with:
  ```java
  String {BINDER_NAME}1Prefix = "spring.cloud.stream.binders.{BINDER_NAME}-1.environment.";
  String {BINDER_NAME}2Prefix = "spring.cloud.stream.binders.{BINDER_NAME}-2.environment.";
  ```
  For each prefix, append the connection property constants (from `AbstractBaseWithOnTheFlyContainerIT`) with values read from the wrapper's getter methods. Use the same constant names and getter methods as the base class's default `buildConnectionArgs()`, but prepend each binder instance's environment prefix.

  The `--server.port=0` argument stays as-is.

- **`checkHealthWithMultipleBinders()` test**: One test method that verifies both binder instances are healthy via the actuator:
  - `jsonPath("components.binders.components.abc-1")` → `jsonPath("components.binders.components.{BINDER_NAME}-1")`
  - `jsonPath("components.binders.components.abc-1.status")` → `jsonPath("components.binders.components.{BINDER_NAME}-1.status")`
  - `jsonPath("components.binders.components.abc-2")` → `jsonPath("components.binders.components.{BINDER_NAME}-2")`
  - `jsonPath("components.binders.components.abc-2.status")` → `jsonPath("components.binders.components.{BINDER_NAME}-2.status")`
  - Everything else (MockMvc, GET `/actuator/health`, status `isOk()`, value `"UP"`) — universal, copy as-is

---

## Step 7: Validate generated files

**Blocked by:** Step 6 must complete successfully.

Verify that every file this module is responsible for exists and is non-empty. All paths are relative to `{BINDER_TARGET_MODULE_DIR}`. `{pkg}` = `{BINDER_PACKAGE_PATH}`.

| # | File | Condition |
|---|---|---|
| 1 | `.env` | **proxy-service-with-client only** — skip for container-with-client |
| 2 | `src/test/resources/application-consumer.yml` | `BINDER_TYPE` contains `consumer` |
| 3 | `src/test/resources/application-sync-producer.yml` | `BINDER_TYPE` contains `producer` |
| 4 | `src/test/resources/application-async-producer.yml` | `BINDER_TYPE` contains `producer` AND `PRODUCER_IS_ASYNC=true` |
| 5 | `src/test/java/{pkg}/app/TestApplication.java` | Always |
| 6 | `src/test/java/{pkg}/AbstractBaseWithOnTheFlyContainerIT.java` | Always |
| 7 | `src/test/resources/application-multibinder.yml` | Always |
| 8 | `src/test/java/{pkg}/MultiBinderIT.java` | Always |

**Validation procedure:**

1. For each applicable row (check the Condition column), verify the file exists and has non-zero size.
2. **On success:** Print **"generate-common-tests validation passed — all files present."**
3. **On failure:** Print **"generate-common-tests validation FAILED"** with a table of missing or empty files (row number, expected path) and **stop**.
