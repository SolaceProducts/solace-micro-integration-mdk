# Generate Shared Infrastructure

This file generates the infrastructure that both producer and consumer capabilities depend on: property classes, client factory, provisioner, binder class, auto-configuration, and META-INF registration files. No capability-specific files (message handler, channel adapter, ack callback) are generated here — those are handled by `generate-producer.md` and `generate-consumer.md`.

---

## Step Execution Rules

**Step execution order is mandatory.** Each step must complete successfully before the next step begins. Do not skip ahead, parallelize steps, or begin a step while a previous step is incomplete. If a step fails, stop and report the failure — do not proceed to subsequent steps.

---

## Inputs

These are available from the calling context (SKILL.md):

- **Configuration variables and derived values** — all variables from `configuration.md` and the derived values table in SKILL.md
- **`{BINDER_TARGET_MODULE_DIR}/CLAUDE.md`** — primary reference for field names, types, defaults, validation annotations, SDK client details, and YAML configuration structure
- **`configuration.md`** — first fallback when CLAUDE.md is incomplete or ambiguous; contains concrete values such as `CLIENT_SDK_DEPENDENCY`, naming variables, and `TECH_OVERVIEW_PATH`
- **Technology overview report** at `{TECH_OVERVIEW_PATH}` (referenced in `configuration.md`) — second fallback for full SDK details (class names, method signatures, exceptions, data types)
- **ABC template project** (`spring-cloud-stream-binder-abc/`) — working reference implementation showing how the binder SPI pattern is wired: `AbstractMessageChannelBinder` integration, provisioner, auto-configuration, META-INF registration, property binding, and test structure

---

## Template Reference

All source files are generated from the ABC binder template. Template path prefixes used in this file:

| Prefix | Path |
|---|---|
| `ABC_MAIN` | `spring-cloud-stream-binder-abc/src/main/java/com/solace/samples/binder/abc` |
| `ABC_RES` | `spring-cloud-stream-binder-abc/src/main/resources` |

---

## Transformation Rules

These rules apply to **every** file generated from an ABC template in this file. Verify each rule before writing any file.

1. **Package**: Replace `com.solace.samples.binder.abc` with `{BINDER_PACKAGE}` in all package declarations and imports.
2. **Class names**: Replace `Abc` prefix with `{TECH_NAME_CLASS_NAME_USE}` in class names, constructors, and cross-references.
3. **Connection config prefix**: Replace `"abc"` with `{BINDER_CONNECTION_PREFIX}` in `@ConfigurationProperties` annotations for connection properties.
4. **Extended binding prefix**: Replace `"spring.cloud.stream.abc"` with `{EXTENDED_BINDING_PREFIX}` in extended binding properties and `DEFAULTS_PREFIX` constants.
5. **Binder name**: Replace `abc` with `{BINDER_NAME}` in `spring.binders`, per-binding `binder`, multi-binder `type`, and log messages.
6. **SDK alignment**: SDK client class, connection pattern, publish/read methods, and exception handling must match CLAUDE.md SDK Client section. If CLAUDE.md is incomplete or ambiguous, consult `configuration.md` first, then the technology overview report at `{TECH_OVERVIEW_PATH}` (referenced in `configuration.md`). For binder SPI integration patterns (how to wire the SDK into `AbstractMessageChannelBinder`, provisioner, auto-configuration, etc.), refer to the ABC template project as the working example. Do not carry over ABC-specific API calls — adapt the pattern to the target SDK.
7. **META-INF alignment**: All fully-qualified class names in META-INF registration files must use `{BINDER_PACKAGE}` and `{TECH_NAME_CLASS_NAME_USE}`, not template values.
8. **No leftover template references**: Generated files must not contain `com.solace.samples`, `abc`, `Abc`, or `ABC` (except in comments that explicitly reference the template origin).
9. **All imports explicit**: No wildcard imports.
10. **No useless comments**: Do not carry over template boilerplate comments. Only retain comments that provide genuine value.

---

## Step 1: Generate property classes

**Blocked by:** Nothing — this is the first step. CLAUDE.md must already exist (produced by `plan-binder-impl.md`).

Properties are shared infrastructure — both producer and consumer capabilities depend on them. This step uses CLAUDE.md as its primary input for field names, types, defaults, and validation annotations.

Read each ABC template, apply the transformation rules, and write to `{BINDER_TARGET_SRC_DIR}/properties/`.

### 1a. Connection properties

| Blueprint | Output |
|---|---|
| `{ABC_MAIN}/properties/AbcBinderConnectionProperties.java` | `{TECH_NAME_CLASS_NAME_USE}BinderConnectionProperties.java` |
| `{ABC_MAIN}/properties/AuthenticationConfig.java` | `AuthenticationConfig.java` |

**Transformation rules (in addition to general rules):**

- `@ConfigurationProperties("abc")` → `@ConfigurationProperties("{BINDER_CONNECTION_PREFIX}")` — matches the root-level YAML namespace defined in CLAUDE.md
- **Connection fields**: The template uses `baseUrl` with `@NotNull @Pattern`. Replace with the fields listed in CLAUDE.md's Connection Properties → Java Mapping table. Adapt the URL pattern annotation to match the technology's connection URL format. If the SDK uses a different connection scheme (e.g., `https://sqs.us-east-1.amazonaws.com`), update the pattern accordingly.
- **AuthenticationConfig.java** — copy the template structure with package replacement. The `type`, `username`, `password` fields and `toString()` redaction pattern are universal. If CLAUDE.md specifies a different authentication pattern (API key, OAuth, etc.), adapt the fields accordingly.

### 1b. Binding property stubs

| Blueprint | Output |
|---|---|
| `{ABC_MAIN}/properties/AbcProducerProperties.java` | `{TECH_NAME_CLASS_NAME_USE}ProducerProperties.java` |
| `{ABC_MAIN}/properties/AbcConsumerProperties.java` | `{TECH_NAME_CLASS_NAME_USE}ConsumerProperties.java` |
| `{ABC_MAIN}/properties/AbcBindingProperties.java` | `{TECH_NAME_CLASS_NAME_USE}BindingProperties.java` |
| `{ABC_MAIN}/properties/AbcExtendedBindingProperties.java` | `{TECH_NAME_CLASS_NAME_USE}ExtendedBindingProperties.java` |

**Transformation rules:**

- **`{Tech}ProducerProperties.java`** — add producer fields from CLAUDE.md Extended Binding Properties table, or leave as empty class if `BINDER_TYPE` is consumer-only. **Class must exist regardless of `BINDER_TYPE`.**
- **`{Tech}ConsumerProperties.java`** — add consumer fields from CLAUDE.md Extended Binding Properties table, or leave as empty class if `BINDER_TYPE` is producer-only. **Class must exist regardless of `BINDER_TYPE`.**
- **`{Tech}BindingProperties.java`** — rename classes, copy structure as-is from ABC template.
- **`{Tech}ExtendedBindingProperties.java`** — rename classes, replace `"spring.cloud.stream.abc"` with `"{EXTENDED_BINDING_PREFIX}"` and update `DEFAULTS_PREFIX` accordingly.

---

## Step 2: Generate client factory

**Blocked by:** Step 1 must complete successfully.

Read the ABC template for the structural pattern: static factory method that takes `{Tech}BinderConnectionProperties` and returns a connected SDK client.

| Blueprint | Output |
|---|---|
| `{ABC_MAIN}/util/IOUtil.java` | `{BINDER_TARGET_SRC_DIR}/util/IOUtil.java` |

**Transformation rules:**

- Replace package and class references
- Replace `AbcClient` with the actual SDK client class from CLAUDE.md SDK Client section
- Replace the builder/factory pattern with the actual SDK connection pattern from CLAUDE.md
- Map `connectionProperties.getBaseUrl()` and `connectionProperties.getAuthentication()` to the SDK's connection and auth API
- Method signature: `public static {SdkClient} create{Tech}Client({Tech}BinderConnectionProperties connectionProperties)`

---

## Step 3: Generate provisioner and destination records

**Blocked by:** Step 2 must complete successfully.

The `destination` value from binding YAML flows through the provisioner into the message handler or channel adapter:

```yaml
spring:
  cloud:
    stream:
      bindings:
        myOutput-out-0:
          destination: target-destination  # -> provisionProducerDestination("target-destination", ...)
                                           #    -> {Tech}ProducerDestination record
                                           #      -> passed to createProducerMessageHandler()
```

**Auto-provisioning of destinations is out of scope** — the provisioner is pass-through only.

### 3a. Destination records

Generate destination records based on `BINDER_TYPE`:

| Condition | Blueprint | Output |
|---|---|---|
| `BINDER_TYPE` contains `producer` | `{ABC_MAIN}/outbound/AbcProducerDestination.java` | `{BINDER_TARGET_SRC_DIR}/outbound/{Tech}ProducerDestination.java` |
| `BINDER_TYPE` contains `consumer` | `{ABC_MAIN}/inbound/AbcConsumerDestination.java` | `{BINDER_TARGET_SRC_DIR}/inbound/{Tech}ConsumerDestination.java` |

Each is a record implementing `ProducerDestination` or `ConsumerDestination`. Wraps the destination name. Pure rename from ABC template.

### 3b. Provisioner

| Blueprint | Output |
|---|---|
| `{ABC_MAIN}/provisioning/AbcBinderProvisioner.java` | `{BINDER_TARGET_SRC_DIR}/provisioning/{Tech}BinderProvisioner.java` |

Implements `ProvisioningProvider`. Pass-through only — returns destination records by name.

**Transformation rules:**

- `provisionProducerDestination()`:
  - If `BINDER_TYPE` contains `producer` → return `new {Tech}ProducerDestination(name)` (pass-through)
  - Else → `throw new UnsupportedOperationException("Producer not supported")`
- `provisionConsumerDestination()`:
  - If `BINDER_TYPE` contains `consumer` → return `new {Tech}ConsumerDestination(name)` (pass-through)
  - Else → `throw new UnsupportedOperationException("Consumer not supported")`

---

## Step 4: Generate binder class

**Blocked by:** Step 3 must complete successfully.

| Blueprint | Output |
|---|---|
| `{ABC_MAIN}/AbcBinder.java` | `{BINDER_TARGET_SRC_DIR}/{Tech}Binder.java` |

**Transformation rules:**

- Replace package, class names, and imports
- `createProducerMessageHandler()`:
  - If `BINDER_TYPE` contains `producer` → return `new {Tech}OutboundMessageHandler(...)` with connection properties, producer destination, and error channel
  - Else → `throw new UnsupportedOperationException("Producer not supported")`
- `createConsumerEndpoint()`:
  - If `BINDER_TYPE` contains `consumer` **and** `.claude/skills/add-binder/generate-consumer.md` contains actual generation steps (not just a placeholder): return `new {Tech}InboundChannelAdapter(...)` with connection properties and consumer destination. Import `{Tech}InboundChannelAdapter` from the `inbound` subpackage.
  - If `BINDER_TYPE` contains `consumer` but `generate-consumer.md` is a placeholder or has no generation steps: treat the same as "consumer not supported" below. Print a warning: **"Consumer requested in BINDER_TYPE but consumer generation is not yet implemented — `createConsumerEndpoint` will throw UnsupportedOperationException."**
  - Else → `throw new UnsupportedOperationException("Consumer not supported")`
- Delegate `getExtendedProducerProperties`, `getDefaultsPrefix`, `getExtendedPropertiesEntryClass` to `extendedBindingProperties`
- Keep the class signature compatible with `AbstractMessageChannelBinder`

---

## Step 5: Generate auto-configuration classes

**Blocked by:** Step 4 must complete successfully.

| Blueprint | Output (write to `{BINDER_TARGET_SRC_DIR}/config/`) |
|---|---|
| `{ABC_MAIN}/config/AbcBinderConfiguration.java` | `{Tech}BinderConfiguration.java` |
| `{ABC_MAIN}/config/ExtendedBindingHandlerMappingsProviderConfiguration.java` | `ExtendedBindingHandlerMappingsProviderConfiguration.java` |
| `{ABC_MAIN}/config/AbcBinderHealthConfiguration.java` | `{Tech}BinderHealthConfiguration.java` |

**Transformation rules:**

- **`{Tech}BinderConfiguration.java`** — `@Configuration`, `@ConditionalOnMissingBean(Binder.class)`, `@EnableConfigurationProperties` listing all property classes. Creates binder bean. Replace all `Abc`/`abc` references.
- **`ExtendedBindingHandlerMappingsProviderConfiguration.java`** — maps `{EXTENDED_BINDING_PREFIX}.bindings` to `{EXTENDED_BINDING_PREFIX}.default`. Replace namespace strings only.
- **`{Tech}BinderHealthConfiguration.java`** — health indicator bean checking client connectivity. Conditional on actuator. Adapt health check to the SDK's health/ping method from CLAUDE.md SDK Client section.

**Do NOT use or import Spring Boot auto-configuration classes for the target technology** (e.g., classes under `org.springframework.boot.autoconfigure.{tech}`). The binder's own configuration classes (`{Tech}BinderConfiguration`, `{Tech}BinderHealthConfiguration`) fully manage client creation via `IOUtil` and health via a custom `HealthIndicator`. Spring Boot's technology-specific auto-configurations create competing beans under a different property namespace and must be excluded — not referenced, extended, or delegated to.

---

## Step 6: Generate META-INF registration files

**Blocked by:** Step 5 must complete successfully.

Template references:
- `{ABC_RES}/META-INF/spring.binders`
- `{ABC_RES}/META-INF/shared.beans`
- `{ABC_RES}/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

### 6a. Binder type registration

**`{BINDER_TARGET_RESOURCES_DIR}/META-INF/spring.binders`:**
```
{BINDER_NAME}=\
{BINDER_PACKAGE}.config.{TECH_NAME_CLASS_NAME_USE}BinderConfiguration
```

### 6b. Shared bean definitions for multi-binder isolation

**`{BINDER_TARGET_RESOURCES_DIR}/META-INF/shared.beans`:**

Spring Cloud Stream creates an **isolated child `ApplicationContext`** for each binder instance. In a multi-binder deployment (e.g., `{BINDER_NAME}-1`, `{BINDER_NAME}-2`), each child context bootstraps its own copy of the binder's configuration and beans. By default, beans from the parent application context are **not visible** to child contexts.

The `shared.beans` file is an **allowlist of fully-qualified class names** from the parent context that child contexts are permitted to inherit. Only stateless, thread-safe utility classes should be listed — classes that every binder instance needs but that carry no per-instance connection state.

**How to determine the entries:**

1. Read CLAUDE.md SDK Client and Dependencies sections and identify the SDK's **transitive infrastructure dependencies** — builder/factory classes, HTTP client factories, serialisation utilities, or connection pool factories that the SDK uses internally.
2. From `IOUtil.java` (Step 2) and the SDK's client construction code, identify which framework-provided beans the SDK needs at construction time.
3. List each such class on its own line. These are classes that are:
   - Stateless or act as factories (safe to share across contexts)
   - Required by the SDK or its transitive dependencies during client creation
   - Provided by Spring Boot's parent context auto-configuration

**ABC template example** (the ABC client uses Spring's `RestTemplate` internally):
```
org.springframework.boot.web.client.RestTemplateBuilder
```

**For the target technology**, inspect how `IOUtil.create{Tech}Client(...)` constructs the SDK client and what framework beans it requires. Common entries include:
- `org.springframework.boot.web.client.RestTemplateBuilder` — if the SDK uses `RestTemplate` / `RestClient`
- `com.fasterxml.jackson.databind.ObjectMapper` — if the SDK requires Jackson for serialisation
- `io.netty.channel.EventLoopGroup` — if the SDK uses Netty for async I/O
- `org.apache.http.impl.client.HttpClientBuilder` — if the SDK uses Apache HttpClient

If the SDK client is fully self-contained (creates its own HTTP client, thread pools, etc. with no Spring-managed dependencies), the file may be **empty** but must still be created.

### 6c. Auto-configuration imports

**`{BINDER_TARGET_RESOURCES_DIR}/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:**
```
{BINDER_PACKAGE}.config.ExtendedBindingHandlerMappingsProviderConfiguration
```

---

## Step 7: Validate generated files

**Blocked by:** Step 6 must complete successfully.

Verify that every file this module is responsible for exists and is non-empty. All paths are relative to `{BINDER_TARGET_MODULE_DIR}`. `{pkg}` = `{BINDER_PACKAGE_PATH}`.

| # | Condition | File |
|---|---|---|
| 1 | always | `src/main/java/{pkg}/properties/{Tech}BinderConnectionProperties.java` |
| 2 | always | `src/main/java/{pkg}/properties/AuthenticationConfig.java` |
| 3 | always | `src/main/java/{pkg}/properties/{Tech}ProducerProperties.java` |
| 4 | always | `src/main/java/{pkg}/properties/{Tech}ConsumerProperties.java` |
| 5 | always | `src/main/java/{pkg}/properties/{Tech}BindingProperties.java` |
| 6 | always | `src/main/java/{pkg}/properties/{Tech}ExtendedBindingProperties.java` |
| 7 | always | `src/main/java/{pkg}/util/IOUtil.java` |
| 8 | `BINDER_TYPE` contains `producer` | `src/main/java/{pkg}/outbound/{Tech}ProducerDestination.java` |
| 9 | `BINDER_TYPE` contains `consumer` | `src/main/java/{pkg}/inbound/{Tech}ConsumerDestination.java` |
| 10 | always | `src/main/java/{pkg}/provisioning/{Tech}BinderProvisioner.java` |
| 11 | always | `src/main/java/{pkg}/{Tech}Binder.java` |
| 12 | always | `src/main/java/{pkg}/config/{Tech}BinderConfiguration.java` |
| 13 | always | `src/main/java/{pkg}/config/ExtendedBindingHandlerMappingsProviderConfiguration.java` |
| 14 | always | `src/main/java/{pkg}/config/{Tech}BinderHealthConfiguration.java` |
| 15 | always | `src/main/resources/META-INF/spring.binders` |
| 16 | always | `src/main/resources/META-INF/shared.beans` |
| 17 | always | `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |

**Validation procedure:**

1. For each row, check the condition against `BINDER_TYPE`. Skip rows whose condition is not met.
2. For each applicable row, verify the file exists and has non-zero size.
3. **On success:** Print **"generate-shared validation passed — all {count} files present."**
4. **On failure:** Print **"generate-shared validation FAILED"** with a table of missing or empty files (row number, expected path) and **stop**.
