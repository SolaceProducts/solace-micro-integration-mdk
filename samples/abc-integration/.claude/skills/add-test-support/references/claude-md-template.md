# CLAUDE.md Template for Test-Support Module

Generate a `CLAUDE.md` file in `{TEST_SUPPORT_MODULE_DIR}/` (the module root, next to `pom.xml`) after all source files are created. This file documents the decisions made during generation so that downstream skills (especially `add-binder`) have a definitive reference for connection parameters, getter names, and SDK usage patterns.

**This step blocks skill completion.** The skill must not print its final summary until this file is written.

---

## Template

Fill in every section below. Replace `{...}` placeholders with actual values discovered during generation. Remove any section marked "Strategy X only" if it does not apply.

````markdown
# {TECH_NAME_LOWER}-test-support

## Test utility type

**Strategy {A|B|C|D}** — {one-line description from the table below}

| Strategy | Description |
|---|---|
| A | Official Testcontainers module (`{testcontainers module artifact}`) |
| B | GenericContainer with official Docker image |
| C | Remote backend proxy (no container, connects to pre-running service) |
| D | Local binaries packaged via Jib |

Pick one row. Delete the others.

## Container details

{Include this section for Strategy A, B, D. Delete for Strategy C.}

- **Docker image**: `{image}:{tag}`
- **Exposed ports**: {port list}
- **Readiness**: {wait strategy — health endpoint, log message, or port check}
- **Container class**: `{fully qualified class name}` {(from official module / GenericContainer / Jib-built)}
- **Credentials configured at start**: {e.g., "withUsername('test'), withPassword('test')" or "env var SOME_AUTH=user/pass"}
- **Web UI**: {If the technology provides a web-based admin console, data browser, or query editor — state the port, URL path, and how to access it from the running container. E.g., "Elasticsearch at `http://localhost:9200/_cat`". If none exists, write "None". For Testcontainers, note that the host port is mapped dynamically — include the getter or method to obtain the mapped URL at runtime, e.g., `container.getHttpUrl()`, `"http://localhost:" + container.getMappedPort(8080)`.}

## Remote backend details

{Include this section for Strategy C. Delete for Strategy A, B, D.}

- **Environment variables read by proxy** (via dotenv-java):
  {list each var with its purpose, e.g.:}
  - `{TECH_NAME_UPPER}_SERVICE_BASE_URL` — backend connection URL
  - `{TECH_NAME_UPPER}_SERVICE_USERNAME` — authentication username
  - `{TECH_NAME_UPPER}_SERVICE_PASSWORD` — authentication password

## Prerequisites for running tests

{Include this section for Strategy C only. Delete for Strategy A, B, D — container-based strategies manage their own lifecycle and require no external setup.}

This module connects to a pre-running backend service. Before running any test that depends on this module (including downstream binder and micro-integration integration tests), the following must be in place:

1. **The backend service must be running** and reachable from the test machine.
2. **Connection and authentication values must be configured** via one of:
   - A `.env` file in the test-support module root (`{TEST_SUPPORT_MODULE_DIR}/.env`) — loaded by dotenv-java
   - System environment variables with the same names — used as fallback when `.env` is absent
3. **Every variable listed in "Environment variables read by proxy" above must have a valid value.** Missing or empty variables will cause the proxy's `start()` to fail or produce an unusable SDK client.

## SDK client

- **Client class**: `{fully qualified SDK client class}` (e.g., `software.amazon.awssdk.services.sqs.SqsClient`)
- **Factory/builder pattern**: `{one-line showing how the client is created, e.g., "SqsClient.builder().endpointOverride(uri).region(region).credentialsProvider(creds).build()"}`
- **Close method**: `{method name}` (e.g., `client.close()`, `client.shutdown()`)

## Connection parameter mapping

How connection values flow from the container/proxy to the SDK client:

| Source (container getter or env var) | SDK client builder input | Value at test time |
|---|---|---|
| `{getter or env var name}` | `{SDK method or constructor arg}` | `{example value or description}` |
| ... | ... | ... |

{Fill one row per connection/auth parameter. This table is the primary reference for `add-binder` when writing `IOUtil.java` and `{Tech}BinderConnectionProperties`.}

## Getter methods exposed

Public getters on the wrapper/proxy class that tests and extensions use:

| Method | Returns | Source | Used by |
|---|---|---|---|
| `getClient()` | `{SDK client class}` | Wrapper | Tests, binder ITs |
| `{getConnectionUrl() or getUrl() etc.}` | `String` | Wrapper | Extension log messages, binder IT system properties |
| ... | ... | ... | ... |

{List every public getter. The binder integration tests call these to inject system properties for `@ConfigurationProperties` binding.}

**Strategy A — inherited parent container getters:** For Strategy A, the wrapper extends a technology-specific Testcontainers module class (e.g., `KafkaContainer`). The table above must include **all public getter methods inherited from the parent container class** — not just getters defined on the wrapper itself. These inherited getters expose connection details (URLs, ports, credentials, configuration values) that downstream skills need for connection property mapping. Mark inherited getters with `Parent` in the Source column to distinguish them from getters defined on the wrapper. **Do not include getters inherited from `GenericContainer`** — only getters from the technology-specific parent class.
````

---

## Instructions for the agent

1. After all strategy steps complete (source files, POM updates, build verification), generate this file at `{TEST_SUPPORT_MODULE_DIR}/CLAUDE.md`.
2. Fill every section using information already gathered during the strategy steps — do not re-read the overview report.
3. The **connection parameter mapping table** is the most important section. Every row must trace a value from its source (container getter / env var) through to the SDK builder call. If a value was hardcoded as a `private static final` constant (because no getter existed on the container), note that in the "Source" column.
4. Do not include the template instructions or the strategy selection table — only the filled-in content for the chosen strategy.
5. Keep it concise. This is a memory file, not documentation.
