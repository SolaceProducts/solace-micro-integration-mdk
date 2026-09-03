# Strategy C: Use Cloud/Remote Backend

Use this strategy when the target technology is a cloud-managed service, a SaaS platform, or any backend where no official Docker image or Testcontainers module is available. Tests connect to a pre-running remote instance — no container is started or stopped.

This strategy produces two source files, an integration test, and a `.env` template that work together:

- **`{TECH_NAME_CLASS_NAME_USE}ServiceProxy`** — plain Java class (`AutoCloseable`, no container). Reads connection URL and credentials from environment variables via dotenv-java. Creates an SDK client on `start()` and closes it on `close()`. Exposes getters for the client and connection details.
- **`{TECH_NAME_CLASS_NAME_USE}ProxyTestExtension`** — JUnit 5 extension. Manages the proxy's lifecycle per test class and injects it into test methods via parameter resolution.
- **`SimpleProxyIT`** — integration test using `@ExtendWith`. Verifies the proxy connects, connection URL is valid, and client is initialized.
- **`.env`** — template file with connection variables. Loaded by dotenv-java; falls back to system environment variables if absent.

Step 1 updates the POM. Step 2 creates the `.env` file. Steps 3–5 generate source files and test. Step 6 verifies the build compiles. Step 7 prints a summary.

---

## Step 1: Update POM

Read `{TEST_SUPPORT_MODULE_DIR}/pom.xml` and apply these changes:

1. **Add** the dotenv-java dependency **without** `<scope>` — the proxy class in `src/main` depends on it directly:
   ```xml
   <dependency>
       <groupId>io.github.cdimascio</groupId>
       <artifactId>dotenv-java</artifactId>
       <version>3.2.0</version>
   </dependency>
   ```

2. **Remove** the `jib-core` dependency entirely (no container image building needed).

3. **Remove** the `commons-compress` dependency if it was only present for Jib support.

4. **Remove** `org.testcontainers:testcontainers` dependency if present — no container is used.

5. **Remove** `org.testcontainers:junit-jupiter` dependency if present — no Testcontainers lifecycle.

---

## Step 2: Create the `.env` template file

Create a `.env` file in `{TEST_SUPPORT_MODULE_DIR}/` (the test-support module root, next to `pom.xml`).

This file defines the environment variables that `{TECH_NAME_CLASS_NAME_USE}ServiceProxy` reads via dotenv-java. The proxy loads `.env` with `ignoreIfMissing()`, so tests also work when variables are set as system environment variables instead.

### Determine required variables

Identify the connection parameters the SDK needs from the overview report (connection URL, authentication credentials, tokens, API keys, etc.). Define one environment variable per parameter, using the naming pattern `{TECH_NAME_UPPER}_SERVICE_*`.

### File format

The header block is fixed. The variables after the header are **derived from the overview report** — add one variable per connection/auth parameter the SDK requires. Use the naming pattern `{TECH_NAME_UPPER}_SERVICE_<PARAMETER>`.

```
# ------------------------------------------------------------------
# {TECH_NAME} Service connection configuration
# Used by {TECH_NAME_CLASS_NAME_USE}ServiceProxy (via dotenv) to connect
# to the {TECH_NAME} backend
# ------------------------------------------------------------------
# SECURITY NOTE - Do NOT commit this file to version control if it
# contains sensitive information (e.g., passwords, tokens, API keys).
# Consider using environment variables when running tests.
# THIS FILE IS FOR DEMONSTRATION PURPOSES ONLY TO SHOW WHICH
# ENVIRONMENT VARIABLES ARE NEEDED AND SHOULD BE PROTECTED IN ALL
# ENVIRONMENTS.
# ------------------------------------------------------------------

# <one commented line per variable explaining its purpose>
{TECH_NAME_UPPER}_SERVICE_<PARAMETER>=<placeholder>
```

Examples of variable sets depending on the authentication pattern found in the overview report:

| Auth pattern | Variables |
|---|---|
| Basic auth | `_BASE_URL`, `_USERNAME`, `_PASSWORD` |
| API key / token | `_BASE_URL`, `_API_KEY` |
| OAuth / client credentials | `_BASE_URL`, `_CLIENT_ID`, `_CLIENT_SECRET` |
| Connection string | `_CONNECTION_STRING` |
| SDK secret file | `_BASE_URL`, `_CREDENTIALS_FILE` (path to JSON/YAML key file) |

The overview report at `{TECH_OVERVIEW_PATH}` determines which pattern applies. Do not default to basic auth — use what the SDK actually requires.

### Template reference

Read `abc-test-support/.env` for the structural pattern (header comments, variable naming, security note).

---

## Step 3: Generate the service proxy class

Create `{TECH_NAME_CLASS_NAME_USE}ServiceProxy.java` in `{TEST_SUPPORT_SRC_DIR}/`.

This class reads connection details from environment variables, creates an SDK client on `start()`, and closes it on `close()`. Tests receive a ready-to-use client via `getClient()` without managing any lifecycle themselves. The remote backend must be running before tests start.

### Base class

Implement `AutoCloseable`. Do **not** extend any container or Testcontainers class.

### SDK connection class

Identify the central class of the client SDK used to interact with the technology backend (e.g., `software.amazon.awssdk.services.sqs.SqsClient` for SQS, `com.influxdb.client.InfluxDBClient` for InfluxDB). This is the type that `getClient()` returns and that tests use to issue operations against the backend. Find this in the overview report under the Java SDK / connection pattern information.

### Dotenv configuration

Declare a `Dotenv` instance that loads variables from `.env` with `ignoreIfMissing()`:

```java
private final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
```

This allows the `.env` file to be optional — values fall back to system environment variables when the file is absent.

### Client field and thread safety

Declare the client field as `volatile` to ensure visibility across threads:

```java
volatile SomeTechnologySdkClient client;   // replace with the respective SDK client class
```

### `start()` — create and connect the client

Create the SDK client by delegating to `createClient()`. The client must be ready to use after `start()` returns:

```java
public void start() {
    this.client = createClient();
}
```

### `close()` — dispose the client

From `AutoCloseable`. If the client is not `null`, close or dispose it (call the SDK's close/shutdown method).

### `createClient()` — build the SDK client from environment variables

Use connection details from getter methods (which read from dotenv) to create an SDK client instance. Every connection and authentication value must come from a getter — no string literals for URLs or credentials in `createClient()`.

Consult the overview report for the SDK's client factory/builder pattern and required connection parameters. Use the real SDK API — do not fabricate method names.

### Getter methods

Expose one public getter per connection parameter defined in the `.env` file. Each getter reads from dotenv and returns a `String`. The getter names must match the SDK's connection requirements as documented in the overview report at `{TECH_OVERVIEW_PATH}`.

Examples depending on the authentication pattern:

| Auth pattern | Getter methods |
|---|---|
| Basic auth | `getConnectionUrl()`, `getUsername()`, `getPassword()` |
| API key / token | `getConnectionUrl()`, `getApiKey()` |
| OAuth / client credentials | `getConnectionUrl()`, `getClientId()`, `getClientSecret()` |
| Connection string | `getConnectionString()` |

Do not assume basic auth. The overview report determines which getters are needed.

### Code requirements

- No Testcontainers imports (no `GenericContainer`, no `@Container`)
- No Jib imports
- All imports must be explicit (no wildcards)
- Include Javadoc explaining that this connects to a remote backend, which environment variables are required, and how to obtain the client

### Template reference

Read `abc-test-support/src/main/java/com/solace/samples/abc/testextension/container/proxy/CLAUDE.md` first for a summary of the template classes and their roles.

Then read `abc-test-support/src/main/java/com/solace/samples/abc/testextension/container/proxy/AbcServiceProxy.java` for the structural pattern (method signatures, lifecycle flow, field layout, dotenv usage). Adapt — do not copy literally.

---

## Step 4: Generate the JUnit 5 extension class

Create `{TECH_NAME_CLASS_NAME_USE}ProxyTestExtension.java` in `{TEST_SUPPORT_SRC_DIR}/`.

Read `abc-test-support/src/main/java/com/solace/samples/abc/testextension/container/proxy/AbcProxyTestExtension.java` for the structural pattern.

### Implementation

1. Implement `ParameterResolver`, `BeforeAllCallback`, `AfterAllCallback`
2. Use `{TECH_NAME_CLASS_NAME_USE}ServiceProxy` as the proxy type throughout
3. Set `PROXY_KEY` to `"{TECH_NAME_UPPER}_PROXY_KEY"`
4. Set `EXTENSION_NAMESPACE` using `ExtensionContext.Namespace.create({TECH_NAME_CLASS_NAME_USE}ProxyTestExtension.class)`
5. In `beforeAll()`: create proxy, call `start()`, store in ExtensionContext
6. In `afterAll()`: retrieve from ExtensionContext, call `close()`
7. In `resolveParameter()`: return the proxy instance from ExtensionContext
8. Log the connection URL on start/stop using the proxy's URL getter
9. Include complete imports and Javadoc — replace all `Abc`/`abc` references with technology name

---

## Step 5: Generate the integration test class

Create `SimpleProxyIT.java` in `{TEST_SUPPORT_TEST_DIR}/`.

Read `abc-test-support/src/test/java/com/solace/samples/abc/testextension/SimpleAbcProxyIT.java` for the structural pattern.

### Implementation

1. **Do not** use `@Testcontainers` or `@Container` annotations — there is no container
2. Use `@ExtendWith({TECH_NAME_CLASS_NAME_USE}ProxyTestExtension.class)` for lifecycle management
3. Receive the proxy via method parameter injection — each test method takes a `{TECH_NAME_CLASS_NAME_USE}ServiceProxy` parameter
4. Include these tests:
   - **URL not null**: connection URL getter returns a non-null, non-empty string
   - **URL protocol**: connection URL starts with the expected prefix (e.g., `http://`, `https://`)
   - **Client initialized**: `getClient()` returns a non-null instance
5. Use Hamcrest matchers: `assertThat`, `is(not(emptyOrNullString()))`, `startsWith()`, `is(notNullValue())`
6. Include complete imports and Javadoc noting which environment variables must be set before running

---

## File naming conventions

| File | Name pattern | Example (Snowflake) |
|---|---|---|
| Service proxy | `{TECH_NAME_CLASS_NAME_USE}ServiceProxy.java` | `SnowflakeServiceProxy.java` |
| JUnit 5 extension | `{TECH_NAME_CLASS_NAME_USE}ProxyTestExtension.java` | `SnowflakeProxyTestExtension.java` |
| Integration test | `SimpleProxyIT.java` | `SimpleProxyIT.java` |
| Environment config | `.env` | `.env` |

---

## Step 6: Build verification

Read `.claude/skills/add-test-support/build-verify.md` and follow its instructions to compile the test-support module and fix dependency issues if the build fails.

---

## Step 7: Generate CLAUDE.md

Read `.claude/skills/add-test-support/claude-md-template.md` and generate a `CLAUDE.md` file in `{TEST_SUPPORT_MODULE_DIR}/`. Fill every section using information gathered during Steps 2–6. This step is mandatory — do not proceed to the summary until the file is written.

---

## Step 8: Print summary

Print a summary listing:
- Container strategy used: **C — Cloud/Remote Backend** (no container, connects to pre-running service)
- Files created (with full paths relative to `TARGET_PROJECT_FOLDER`)
- POM changes made (dependencies added/removed)
- How to run the integration test:
  - The Maven command to execute the test using Maven Failsafe plugin
  - Note that the remote backend must be running and `.env` (or equivalent environment variables) must be configured before running

---

## Validation checklist

Before writing any file, verify:

- [ ] Package declaration matches `{TEST_SUPPORT_PACKAGE}`
- [ ] All imports are explicit (no wildcards)
- [ ] Class names use `{TECH_NAME_CLASS_NAME_USE}` prefix (not `Abc`)
- [ ] No references to `com.solace.samples` remain (use `{PROJECT_ROOT_GROUP_ID}`)
- [ ] No references to `abc`, `Abc`, or `ABC` remain in generated code
- [ ] No Testcontainers imports or annotations in any generated file
- [ ] SDK client class and connection pattern match the overview report
- [ ] Environment variable names in `.env`, proxy class, and Javadoc are consistent
- [ ] The proxy class compiles against the dependencies in the POM
