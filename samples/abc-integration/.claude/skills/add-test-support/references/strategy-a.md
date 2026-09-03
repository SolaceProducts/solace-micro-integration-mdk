# Strategy A: Use Official Testcontainers Module

Use this strategy when an official Java Testcontainers module is available for the target technology (e.g., `org.testcontainers:influxdb`, `org.testcontainers:postgresql`).

This strategy produces two source files and an integration test that work together:

- **`{TECH_NAME_CLASS_NAME_USE}TestContainerWithConnectedClient`** — extends the official module's container, embeds a connected SDK client. Manages full lifecycle: start container, create client, close client, stop container.
- **`{TECH_NAME_CLASS_NAME_USE}ContainerTestExtension`** — JUnit 5 extension. Manages the wrapper's lifecycle per test class and injects it into test methods via parameter resolution.
- **`SimpleTestContainerIT`** — integration test using `@Testcontainers`/`@Container`. Verifies the container starts, connection URL is valid, and client is initialized.

Step 1 updates the POM so dependencies are in place. Steps 2–4 generate source files and test. Step 5 verifies the build compiles. Step 6 prints a summary.

---

## Step 1: Update POM

Read `{TEST_SUPPORT_MODULE_DIR}/pom.xml` and apply these changes:

1. **Add** the official Testcontainers module dependency from `TESTCONTAINERS_MODULE_DEPENDENCY` (configuration.md section 3b) **without** `<scope>` — the wrapper class in `src/main` depends on it directly. Insert the dependency element verbatim from the configured value.

2. **Add** `org.testcontainers:junit-jupiter` with `<scope>test</scope>` if not already present:
   ```xml
   <dependency>
       <groupId>org.testcontainers</groupId>
       <artifactId>junit-jupiter</artifactId>
       <scope>test</scope>
   </dependency>
   ```

3. **Remove** the `jib-core` dependency entirely (official image used, no Jib needed).

4. **Remove** the `commons-compress` dependency if it was only present for Jib support.

---

## Step 2: Generate the Testcontainer wrapper with embedded connected client

Create `{TECH_NAME_CLASS_NAME_USE}TestContainerWithConnectedClient.java` in `{TEST_SUPPORT_SRC_DIR}/`.

This class extends a Testcontainers-managed container and embeds an SDK client that connects automatically when the container starts and disconnects when it stops. Tests receive a ready-to-use client via `getClient()` without managing any lifecycle themselves.

### Base class

Extend the technology-specific container class provided by the Testcontainers module (e.g., `InfluxDBContainer<>`). Do **not** extend `GenericContainer` directly — the module's container class already handles image selection, port exposure, and wait strategy.

### SDK connection class

Identify the central class of the client SDK used to interact with the technology backend (e.g., `com.influxdb.client.InfluxDBClient` for InfluxDB, `com.mongodb.client.MongoClient` for MongoDB). This is the type that `getClient()` returns and that tests use to issue operations against the backend. Find this in the overview report under the Java SDK / connection pattern information.

### Constructor

- Check the container class constructor signatures from the fetched source (Step 2, substep 1). If a no-arg constructor exists, call it — the module provides a default Docker image, exposed ports, and wait strategy. If no no-arg constructor exists (e.g., `InfluxDBContainer` requires a `DockerImageName` argument), define a `private static final String DEFAULT_IMAGE_NAME` constant with the image and tag from the overview report, and pass it to the parent constructor
- To customize credentials or other settings, use fluent configuration methods from the container class (e.g., `withUsername()`, `withPassword()`, `withBucket()`). Verify method names exist in the fetched source — do not assume.

### Client field and thread safety

Declare the client field as `private volatile` to ensure visibility across threads — the container may be started and accessed from different threads during parallel test execution:

```java
private volatile SomeTechnologySdkClient client;   // replace with the respective sdk client class
```

### `start()` — create and connect the client

Override `public void start()` from `org.testcontainers.containers.GenericContainer`. Call `super.start()` first so the container is fully running, then create the SDK client. Delegate the creation logic to a private `createClient()` method to keep `start()` clean. The client must be ready to use after `start()` returns.

### `createClient()` — bridge container API to SDK client

Use connection details from the container's getter methods to create an SDK client instance. Both APIs (container getters and SDK factory) must come from inspecting real source code — do not fabricate method names.

1. **Fetch the container class source from GitHub.** Mandatory — do not skip. Identify:
   - **All public getter methods** on the technology-specific container class (e.g., `KafkaContainer`) — not just connection-related getters. Catalog every public getter: name, return type, and purpose. These are inherited by the wrapper and must be documented in CLAUDE.md so downstream skills know the full API surface. **Do not catalog getters inherited from `GenericContainer`** — only getters defined on the technology-specific container class itself.
   - Fluent configuration methods for pre-start customization
   - Constructor signatures and class package/generics
   - **Version awareness:** the `main` branch may have a different package or API than the version resolved by Maven. If in doubt, verify the actual package by inspecting the resolved jar.

2. **Map container getters to SDK client inputs.** Every connection and authentication value in `createClient()` must come from a getter call on the container instance. Do not duplicate values from the constructor as constants. For example, `InfluxDBContainer` exposes `getUrl()`, `getUsername()`, `getPassword()`, `getAdminToken()`, `getBucket()`, `getOrganization()` — use these directly to build the SDK client instead of repeating values from the constructor.

3. **Fallback when a getter is missing.** If the container class has no getter for a value the SDK needs (confirm by reading the source), define a `private static final` constant on the wrapper class and expose it via a public getter. Use that getter in `createClient()`. No string literals for connection or auth values in `createClient()`.

   Example — a container exposes `getPassword()` but not the username:
   ```java
   private static final String DEFAULT_USERNAME = "admin";
   public String getUsername() { return DEFAULT_USERNAME; }
   ```

### `stop()` — dispose the client and stop the container

Override `public void stop()` from `org.testcontainers.containers.GenericContainer`. If the client is not `null`, close or dispose it (call the SDK's close/shutdown method). Then call `super.stop()`. Always close the client **before** stopping the container — closing after the container is gone may hang or throw.

### `getClient()` — return the connected client instance

This is the primary accessor that tests and extensions use. It must return the connected, ready-to-use client instance. Return type is the central client class identified above.

### Code requirements

- All imports must be explicit (no wildcards)
- Include Javadoc explaining the container image used, authentication setup, and how to obtain the client

### Template reference

Read `abc-test-support/src/main/java/com/solace/samples/abc/testextension/container/simple/CLAUDE.md` first for a summary of the template classes and their roles.

Then read `abc-test-support/src/main/java/com/solace/samples/abc/testextension/container/simple/AbcTestContainerWithConnectedClient.java` for the structural pattern (method signatures, lifecycle flow, field layout). Adapt — do not copy literally.

---

## Step 3: Generate the JUnit 5 extension class

Create `{TECH_NAME_CLASS_NAME_USE}ContainerTestExtension.java` in `{TEST_SUPPORT_SRC_DIR}/`.

Read `abc-test-support/src/main/java/com/solace/samples/abc/testextension/container/simple/AbcSimpleContainerTestExtension.java` for the structural pattern.

### Implementation

1. Implement `ParameterResolver`, `BeforeAllCallback` — do **not** implement `AfterAllCallback`
2. Use `{TECH_NAME_CLASS_NAME_USE}TestContainerWithConnectedClient` as the container type throughout
3. Set `CONTAINER_KEY` to `"{TECH_NAME_UPPER}_CONTAINER_KEY"`
4. Set `EXTENSION_NAMESPACE` using `ExtensionContext.Namespace.create({TECH_NAME_CLASS_NAME_USE}ContainerTestExtension.class)`
5. **Shared container with lazy creation:** implement a `private getContainer(ExtensionContext)` method that calls `extensionContext.getRoot().getStore(EXTENSION_NAMESPACE).getOrComputeIfAbsent(CONTAINER_KEY, key -> createContainerResource(), SharedContainerResource.class).getContainer()`. This ensures a single container is created on first access and reused across all test classes.
6. **`SharedContainerResource` inner class:** create a `private static class SharedContainerResource implements ExtensionContext.Store.CloseableResource` that wraps the container wrapper. It holds the `{TECH_NAME_CLASS_NAME_USE}TestContainerWithConnectedClient` instance, exposes it via `getContainer()`, and stops it in its `close()` method. Because it implements `CloseableResource`, the JUnit platform automatically calls `close()` when the root `ExtensionContext` store is closed (end of test suite) — no `afterAll()` needed.
7. **`createContainerResource()`:** a `private static` method that creates and starts the wrapper, wraps it in a `SharedContainerResource`, and returns it. Log the connection URL after start.
8. In `beforeAll()`: call `getContainer(context)` to trigger lazy creation on the first test class; subsequent classes reuse the same container. Log the connection URL.
9. In `resolveParameter()`: call `getContainer(extensionContext)` to return the wrapper instance
10. Use the correct URL getter name from Step 2 in log messages
11. Include complete imports and Javadoc — replace all `Abc`/`abc` references with technology name

---

## Step 4: Generate the integration test class

Create `SimpleTestContainerIT.java` in `{TEST_SUPPORT_TEST_DIR}/`.

Read `abc-test-support/src/test/java/com/solace/samples/abc/testextension/SimpleAbcTestContainerIT.java` for the structural pattern.

### Implementation

1. Use `@Testcontainers` and `@Container` annotations
2. Declare the wrapper as `private static {TECH_NAME_CLASS_NAME_USE}TestContainerWithConnectedClient`
3. Include these tests:
   - **URL not null**: connection URL getter returns a non-null, non-empty string
   - **URL protocol**: connection URL starts with the expected prefix (e.g., `http://`, `https://`)
   - **Client initialized**: `getClient()` returns a non-null instance
4. Use Hamcrest matchers: `assertThat`, `is(not(emptyOrNullString()))`, `startsWith()`, `is(notNullValue())`
5. Include complete imports and Javadoc

---

## File naming conventions

| File | Name pattern | Example (InfluxDB) |
|---|---|---|
| Container wrapper | `{TECH_NAME_CLASS_NAME_USE}TestContainerWithConnectedClient.java` | `InfluxDbTestContainerWithConnectedClient.java` |
| JUnit 5 extension | `{TECH_NAME_CLASS_NAME_USE}ContainerTestExtension.java` | `InfluxDbContainerTestExtension.java` |
| Integration test | `SimpleTestContainerIT.java` | `SimpleTestContainerIT.java` |

---

## Step 5: Build verification

Read `.claude/skills/add-test-support/build-verify.md` and follow its instructions to compile the test-support module and fix dependency issues if the build fails.

---

## Step 6: Generate CLAUDE.md

Read `.claude/skills/add-test-support/claude-md-template.md` and generate a `CLAUDE.md` file in `{TEST_SUPPORT_MODULE_DIR}/`. Fill every section using information gathered during Steps 2–5. This step is mandatory — do not proceed to the summary until the file is written.

---

## Step 7: Print summary

Print a summary listing:
- Container strategy used: **A — Official Testcontainers module** with the module artifact name
- Files created (with full paths relative to `TARGET_PROJECT_FOLDER`)
- POM changes made (dependencies added/removed)
- How to run the integration test:
  - The Maven command to execute the test using Maven Failsafe plugin
  - Note that Docker must be running for Testcontainers to work

---

## Validation checklist

Before writing any file, verify:

- [ ] Package declaration matches `{TEST_SUPPORT_PACKAGE}`
- [ ] All imports are explicit (no wildcards)
- [ ] Class names use `{TECH_NAME_CLASS_NAME_USE}` prefix (not `Abc`)
- [ ] No references to `com.solace.samples` remain (use `{PROJECT_ROOT_GROUP_ID}`)
- [ ] No references to `abc`, `Abc`, or `ABC` remain in generated code
- [ ] Docker image tag matches what the overview report documents
- [ ] SDK client class and connection pattern match the overview report
- [ ] The container wrapper compiles against the dependencies in the POM
