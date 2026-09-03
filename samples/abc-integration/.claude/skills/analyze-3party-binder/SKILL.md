---
name: analyze-3party-binder
description: Deep-analyze a third-party Spring Cloud Stream binder from its GitHub source. Discovers Maven dependency, connection properties, extended binding properties, authentication variants, capability modes, message payload requirements, special headers, and the underlying client SDK. Produces the binder's CLAUDE.md so downstream skills have the same reference document as for a custom-generated binder.
tools_required:
  - Read
  - Write
  - Glob
  - Grep
  - Bash
  - WebFetch
  - AskUserQuestion
---

# Analyze Third-Party Binder

## Role

You are an expert Java developer specializing in Spring Cloud Stream binder internals. You can read and understand Spring Cloud Stream binder source code — auto-configuration classes, provisioning providers, message handlers, message producers, binding properties, and `spring.binders` registrations. You have deep knowledge of Spring Boot `@ConfigurationProperties`, `@EnableConfigurationProperties`, the binder SPI (`org.springframework.cloud.stream.binder.AbstractMessageChannelBinder`, `org.springframework.cloud.stream.provisioning.ProvisioningProvider`, `org.springframework.cloud.stream.binder.ExtendedBindingProperties`), and how connection/authentication properties flow from YAML through property classes into SDK client construction.

## Overview

When a project uses a third-party Spring Cloud Stream binder instead of a custom-generated one (`BINDER_SKIP_GENERATION=true`), the downstream `add-microintegration` skill needs the same detailed binder reference document that a custom binder's `CLAUDE.md` provides. This skill fills that gap by performing a deep analysis of the third-party binder's source code and producing a `CLAUDE.md` file with all findings.

The skill downloads the binder source locally (using git sparse-checkout for monorepo subtrees to avoid downloading unnecessary code), then uses Glob, Grep, and Read to locate key classes (auto-configuration, properties, message handler, message producer, `spring.binders`), extracts configuration metadata, and documents everything in a structured format that `plan-mi-impl.md` can consume directly.

## Quick Start

```bash
/analyze-3party-binder
```

## Scope

**What this skill does:**
- Reads `BINDER_SKIP_GENERATION` and `BINDER_3PARTY_SOURCE_URL` from `configuration.md`
- Downloads the binder source locally into `.TEMP/` using the download script
- For monorepo URLs, downloads **only** the binder subtree — not the entire repository
- Locates key source files using Glob and Grep on the local copy
- Discovers the binder name from `spring.binders`
- Discovers the Maven dependency (groupId, artifactId, latest stable version)
- Catalogs all connection properties with types, defaults, and structure
- Catalogs all extended consumer and producer binding properties
- Identifies all supported authentication variants with YAML examples
- Determines whether the binder supports producer mode, consumer mode, or both
- Analyzes message payload requirements for both directions
- Discovers special Spring message headers with meaning for the binder
- Identifies the underlying client SDK used by the binder
- Produces `{TARGET_PROJECT_FOLDER}/{BINDER_ARTIFACT_ID}/CLAUDE.md`
- Preserves `.TEMP/` after analysis for later inspection (cleaned up at the start of the next run)

**What this skill does NOT do:**
- Generate any Java source code
- Create POM files or directory structures (beyond the output directory for CLAUDE.md)
- Modify the binder source
- Generate test-support infrastructure

---

## Step Execution Rules

**Step execution order is mandatory.** Each step must complete successfully before the next step begins. Do not skip ahead, parallelize steps, or begin a step while a previous step is incomplete. If a step fails, stop and report the failure — do not proceed to subsequent steps.

---

## Execution Instructions

### Step 1: Parse and validate configuration.md

Read `configuration.md` from workspace root. Scan **every** markdown table across **all** sections (including subsections like 2a, 2b, 3b, etc.). Each table row whose first column contains a back-ticked `Variable` name defines one configuration variable. Extract every such variable by reading its value from the **Configuration Value** column.

`configuration.md` is the single source of truth for which variables exist. Do not maintain a separate list of expected variable names in this skill.

#### Configuration validation (mandatory — run before anything else)

Each configuration table row has an **Example Value** column and a **Configuration Value** column. A variable's value is taken **exclusively** from the **Configuration Value** column. The Example Value column is documentation only — it must **never** be used as a fallback or default.

**Validation rules — ALL must pass or the skill stops immediately:**

1. **Every variable** found in the configuration tables must have a **non-empty Configuration Value**. A cell that is empty, contains only whitespace, or is absent means the variable is **unset**.
2. If a configuration section is commented out (e.g. wrapped in `[//]: #` markdown comments), all variables from that section are considered **unset**.
3. `TARGET_PROJECT_FOLDER` must not equal the placeholder `c:/your/path/new-project-name`.
4. **No derivation, guessing, or inference is allowed.** Do not compute a value from the Example Value, the technology name, or any other variable. Every value must come verbatim from the Configuration Value cell.

**Exception for optional variables:** The following variables may be empty — they are not required for all skills to proceed:

- `BINDER_3PARTY_DEPENDENCY`

**On validation failure — this is a hard stop:**

- Do **NOT** create any files.
- Print the heading: **"Configuration incomplete — cannot proceed"**
- Print a table listing every missing/unset variable with its section number and the Description from its row in the configuration table.
- Stop.

#### Required variables for this skill

After parsing, confirm these variables are available:

| Variable | Used for |
|---|---|
| `TARGET_PROJECT_FOLDER` | Root of the generated project — output directory base |
| `BINDER_ARTIFACT_ID` | Directory name for the output CLAUDE.md |
| `BINDER_SKIP_GENERATION` | Must be `true` — this skill only runs for third-party binders |
| `BINDER_3PARTY_SOURCE_URL` | GitHub URL of the third-party binder source repository or subtree |
| `TECH_NAME_LOWER` | Lowercase technology name — used in output headings and cross-references |
| `TECH_NAME_UPPER` | Uppercase technology name — used in output headings |
| `HOST_OS` | Host operating system (`windows` or `linux`) — determines which download script to use |

#### Pre-condition: verify BINDER_SKIP_GENERATION is true

If `BINDER_SKIP_GENERATION` is not `true` (case-insensitive), this skill has no purpose — the custom binder generation (`add-binder`) produces its own CLAUDE.md. Print: **"BINDER_SKIP_GENERATION is not `true` — this skill is only needed for third-party binders. Run `/add-binder` instead."** and stop.

#### Pre-condition: verify BINDER_3PARTY_SOURCE_URL is non-empty

If `BINDER_3PARTY_SOURCE_URL` is empty, there is no source to analyze. Print: **"BINDER_3PARTY_SOURCE_URL is empty — cannot analyze a third-party binder without a source URL. Set the URL in configuration.md."** and stop.

#### Derive paths (only after validation passes)

| Derived | Formula | Purpose |
|---|---|---|
| `BINDER_OUTPUT_DIR` | `{TARGET_PROJECT_FOLDER}/{BINDER_ARTIFACT_ID}` | Directory for the output CLAUDE.md |
| `BINDER_CLAUDE_MD` | `{BINDER_OUTPUT_DIR}/CLAUDE.md` | Output file path |
| `WORKSPACE_ROOT` | Directory containing `configuration.md` | Base for `.TEMP` and download scripts |
| `SKILL_DIR` | `{WORKSPACE_ROOT}/.claude/skills/analyze-3party-binder` | Location of download scripts |

---

### Step 2: Download binder source locally

Download the third-party binder source into `.TEMP/` using the download script. This replaces all web-based source navigation — after this step, every file read uses local tools (Read, Glob, Grep).

#### 2a. Clean up any previous `.TEMP/` directory

If `{WORKSPACE_ROOT}/.TEMP` exists from a previous run, delete it first to ensure a clean download:

- **`HOST_OS=windows`**:
  ```
  rmdir /s /q "{WORKSPACE_ROOT}/.TEMP"
  ```
- **`HOST_OS=linux`**:
```
rm -rf "{WORKSPACE_ROOT}/.TEMP"
```

The downloaded source is **not** deleted at the end of the skill — it remains in `.TEMP/` so it can be inspected later if needed.

#### 2b. Run the download script

Select the script based on `HOST_OS`:

- **`HOST_OS=windows`**:
  ```
  cmd /c "{SKILL_DIR}/scripts/download-binder-source.cmd" "{BINDER_3PARTY_SOURCE_URL}" "{WORKSPACE_ROOT}/.TEMP"
  ```
- **`HOST_OS=linux`**:
```
  bash "{SKILL_DIR}/scripts/download-binder-source.sh" "{BINDER_3PARTY_SOURCE_URL}" "{WORKSPACE_ROOT}/.TEMP"
```

Use a **2-minute timeout** (120 000 ms) — the download involves a network clone.

The script prints `BINDER_SOURCE_ROOT=<path>` on its second-to-last output line (the last line is a separator). Capture this value — it is the absolute local directory containing the binder's top-level `pom.xml`.

**On failure:** If the script exits with a non-zero code, print the error output and stop. Do not proceed with partial or missing source.

#### 2b. Locate key source files locally

Use Glob and Grep against the downloaded source under `BINDER_SOURCE_ROOT` to find all key files. Run these searches **in parallel** where possible:

| File pattern | Tool | What it finds |
|---|---|---|
| `**/META-INF/spring.binders` | Glob | Binder name and auto-configuration class |
| `**/*Configuration.java` | Glob | Auto-configuration and binder configuration classes |
| `**/*Properties.java` | Glob | All property classes (connection, consumer, producer, binding, extended) |
| `**/pom.xml` | Glob | Maven coordinates and SDK dependencies |
| Grep for `@ConfigurationProperties` | Grep across `**/*.java` | Connection property prefix and property class identification |
| Grep for `AbstractMessageChannelBinder` | Grep across `**/*.java` | Main binder class (producer/consumer handler creation) |
| Grep for `ExtendedBindingProperties` | Grep across `**/*.java` | Extended binding properties class |
| Grep for `ProvisioningProvider` | Grep across `**/*.java` | Destination provisioning class |

Store the **absolute local path** for each located file. If a file cannot be found, document it as **"not found"** and note the impact on downstream analysis.

#### 2c. Identify the main binder class

The main binder class extends `org.springframework.cloud.stream.binder.AbstractMessageChannelBinder`. Use the Grep result from 2b to find it. This class contains the `createProducerMessageHandler()` and `createConsumerEndpoint()` methods that are critical for Steps 7 and 8. Read its full content and keep it available for later steps.

---

### Step 3: Extract binder identity

#### 3a. Resolve binder name from `spring.binders`

Read the local `spring.binders` file found in Step 2b. The file uses the format:

```
binder-name=fully.qualified.AutoConfigurationClass
```

Extract the **left side** of the `=` sign. This is the canonical binder name used in YAML (`binder: {name}`) and health check paths.

Store as `RESOLVED_BINDER_NAME`.

If the file contains multiple binder registrations (multiple lines), document all of them and identify the primary one.

#### 3b. Resolve Maven dependency

Read the binder's local `pom.xml` files (both the binder subtree root `pom.xml` and the module that produces the binder JAR). Extract:
- `groupId`
- `artifactId`
- `version`

If the version shows a `SNAPSHOT`, resolve the latest stable release by fetching the binder's published POM from Maven Central:
```
https://repo1.maven.org/maven2/{groupId-as-path}/{artifactId}/maven-metadata.xml
```
Use WebFetch on this URL to find the `<latest>` or `<release>` version.

If `BINDER_3PARTY_DEPENDENCY` is already set in configuration.md, use it as the authoritative value. Otherwise, construct the `<dependency>` element from the extracted coordinates.

Store as `BINDER_MAVEN_DEPENDENCY`.

#### 3c. Identify the underlying client SDK

Inspect the binder's local `pom.xml` / `build.gradle` dependencies to find the client SDK used to communicate with the backend technology. Look for:
- Direct dependencies on a technology-specific client library (e.g., `kafka-clients`, `aws-java-sdk-sqs`, `pulsar-client`)
- Transitive dependencies pulled in via a Spring Boot starter

Also inspect the binder source code locally — use Grep to search for import statements of SDK client classes across all Java files. Document:
- SDK library Maven coordinates (groupId, artifactId, version). To resolve the version when it is managed by a BOM or parent POM: fetch the published POM from Maven Central using WebFetch at `https://repo1.maven.org/maven2/{groupId-as-path}/{artifactId}/{version}/{artifactId}-{version}.pom` and inspect its `<dependencies>` section.
- Primary client class (fully qualified name)
- Client creation pattern (builder, factory, constructor)
- Close/shutdown method

Store as `SDK_CLIENT_INFO`.

---

### Step 4: Analyze connection properties

#### 4a. Find the connection properties class

Use the Grep results from Step 2b to locate the `@ConfigurationProperties`-annotated class that defines the binder's connection settings. This is typically **not** under the `spring.cloud.stream.*` namespace — it has its own prefix (e.g., `spring.kafka`, `spring.pulsar`, `spring.cloud.aws.sqs`).

**Important:** The connection properties class may live **outside** the binder subtree — for example, in Spring Boot's auto-configuration module (e.g., `org.springframework.boot.autoconfigure.pulsar.PulsarProperties`). If Grep within `BINDER_SOURCE_ROOT` does not find a `@ConfigurationProperties` class with the expected connection prefix:

1. Check the binder's auto-configuration class imports to identify which external properties class is used.
2. Use your knowledge of the Spring Boot `@ConfigurationProperties` class for that technology. The standard Spring Boot connection properties classes and their prefixes are well-documented and stable.
3. If needed, fetch the specific properties class source from the Spring Boot GitHub repository using WebFetch at `https://raw.githubusercontent.com/spring-projects/spring-boot/{branch}/spring-boot-project/spring-boot-autoconfigure/src/main/java/{package-path}/{ClassName}.java`.

Read the full source of the connection properties class. Extract:
- The `@ConfigurationProperties` prefix value — this is the **connection property prefix**
- Every field with its Java type, default value (if any), and any validation annotations (`@NotNull`, `@NotEmpty`, etc.)
- Nested configuration classes (e.g., `Authentication`, `Ssl`, `Security` inner classes) — flatten all properties into their full YAML key paths

#### 4b. Catalog every connection property

For each property, document:

| YAML key path | Java type | Default value | Required? | Description |
|---|---|---|---|---|
| `{prefix}.{key}` | `String` / `int` / `boolean` / `enum` / `Duration` | value or "none" | Yes/No | Purpose |

Pay special attention to:
- **Nested objects** — document the full dot-separated YAML path (e.g., `spring.kafka.ssl.key-store-location`)
- **Enum fields** — list all valid enum values
- **Duration fields** — note the unit (millis, seconds) and format (ISO-8601 or plain number)
- **Map/List fields** — document the expected key/value structure

#### 4c. Classify property structure

For each connection property, classify its structural role (same classification used by `plan-mi-impl.md`):

| Classification | Description |
|---|---|
| **Root connection** | Top-level key directly under the prefix (e.g., `spring.kafka.bootstrap-servers`) |
| **Nested sub-object** | Key under a nested config class (e.g., `spring.kafka.ssl.protocol`) |
| **Nested credential** | Authentication credential under a nested auth/security class |

---

### Step 5: Analyze authentication variants

Inspect the connection properties class and any related authentication/security configuration classes to discover all supported authentication mechanisms.

#### 5a. Identify authentication patterns

Look for:
- An `authentication.type` or similar enum/string property that switches between auth modes
- Separate property groups for different auth mechanisms (e.g., `basic-auth.*`, `oauth2.*`, `sasl.*`)
- SSL/TLS configuration (truststore, keystore, protocols)
- API key / token authentication
- AWS-style credential providers (IAM roles, access keys, STS)
- No-auth / anonymous access option

Use Grep on the local source to search for classes containing `Ssl`, `Security`, `Auth`, `Credential`, or `Sasl` in their name or fields.

#### 5b. Document each authentication variant

For each supported authentication mechanism, document:
- **Name** (e.g., `PLAIN`, `SCRAM-SHA-256`, `OAuth2`, `API_KEY`, `BASIC`)
- **Required properties** — which connection properties must be set
- **Optional properties** — additional tuning properties. **When listing optional connection properties (hosts, ports, connection URLs, addresses, virtual-hosts, and similar connectivity parameters), always include their default values in parentheses** (e.g., `spring.pulsar.client.service-url` (default: `pulsar://localhost:6650`), `spring.kafka.bootstrap-servers` (default: `localhost:9092`)). This ensures downstream consumers know the effective defaults without cross-referencing the properties table.

Do **not** produce YAML examples in this step — YAML construction happens in Step 9b using the property data cataloged here.

---

### Step 6: Analyze extended binding properties

#### 6a. Find the extended binding properties classes

Use the Grep result from Step 2b to locate the class that implements `org.springframework.cloud.stream.binder.ExtendedBindingProperties` (or equivalent). Read it locally. From it, find:
- The `DEFAULTS_PREFIX` constant — this is the extended binding properties namespace (e.g., `spring.cloud.stream.kafka`)
- The consumer properties class
- The producer properties class

#### 6b. Catalog extended consumer properties

Read the consumer properties class locally. For each field, document:

| Property key | Java type | Default value | Description |
|---|---|---|---|
| `{key}` | type | value | purpose |

If the consumer properties class extends a common base class, also read and include all inherited fields.

#### 6c. Catalog extended producer properties

Read the producer properties class locally. Same table format as 6b. Include inherited fields from any base class.

#### 6d. Identify other properties

Some binders have additional properties that don't fall into connection or extended binding categories — e.g., admin properties, metrics properties, or global binder-level settings. Use the Grep result for `@ConfigurationProperties` from Step 2b to identify any additional property classes. Read and document any such properties found.

---

### Step 7: Determine capability modes

#### 7a. Producer mode

Read the main binder class (from Step 2c). Confirm the binder supports producer mode by verifying:
- The `createProducerMessageHandler()` method exists and returns a message handler (not just throwing `UnsupportedOperationException`)
- Producer-specific properties exist (even if empty)
- The provisioning provider handles producer destinations

If found, determine the **producer acknowledgment pattern** by reading the `createProducerMessageHandler()` method body:

| Evidence of **async** publishing | Evidence of **sync** publishing |
|---|---|
| SDK publish method returns `Future`, `CompletableFuture`, `Mono`, `ListenableFuture` | SDK publish method returns `void` or a direct result |
| Message handler uses a callback/listener for completion notification | Message handler blocks until the backend confirms receipt |
| Producer properties include async-related settings (e.g., `async: true`, `acks`, callback timeouts) | No async settings in producer properties |

Store as `VERIFIED_PRODUCER_SUPPORTED` (boolean) and `PRODUCER_IS_ASYNC` (boolean). Map the boolean to the output label: `PRODUCER_IS_ASYNC=false` → `SYNC`, `PRODUCER_IS_ASYNC=true` → `ASYNC_BY_CALLBACK_HEADER`.

#### 7b. Consumer mode

Read the main binder class (from Step 2c). Confirm the binder supports consumer mode by verifying:
- The `createConsumerEndpoint()` method exists and returns a consumer endpoint (not just throwing `UnsupportedOperationException`)
- Consumer-specific properties exist (even if empty)
- The provisioning provider handles consumer destinations

If found, determine the **consumer acknowledgment pattern** by reading the `createConsumerEndpoint()` method body:

| Evidence of **client-ack** consumption | Evidence of **auto-ack** consumption |
|---|---|
| Binder's inbound handler populates `org.springframework.integration.IntegrationMessageHeaderAccessor.ACKNOWLEDGMENT_CALLBACK` | No ack callback header is set |
| Consumer uses `org.springframework.integration.acks.AcknowledgmentCallback` or per-message ack mechanism | Messages auto-acknowledged on delivery |
| Consumer properties include `ackMode` or similar configuration | No explicit ack configuration |

**Ack mode preference rule:** When the binder supports **both** auto-ack and client-ack (i.e., the ack mode is configurable via a property like `acknowledgeMode`), always set `CONSUMER_IS_CLIENT_ACK` to **`false`** and recommend auto-acknowledgment. Auto-ack is simpler, avoids manual ack/nack plumbing in the generated micro-integration code, and is sufficient for the connector use case. Only set `CONSUMER_IS_CLIENT_ACK` to `true` when the binder **exclusively** uses client-ack with no auto-ack option.

In the YAML configuration examples (Step 9), explicitly set the ack mode property to the auto-ack value (e.g., `acknowledgeMode: AUTO`) so downstream consumers generate code that does not perform manual acknowledgment.

Store as `VERIFIED_CONSUMER_SUPPORTED` (boolean) and `CONSUMER_IS_CLIENT_ACK` (boolean). Map the boolean to the output label: `CONSUMER_IS_CLIENT_ACK=false` → `AUTO_ACK`, `CONSUMER_IS_CLIENT_ACK=true` → `CLIENT_ACK_BY_CALLBACK_HEADER`.

#### 7c. Consumer ack — standard Spring header detection

**This sub-step runs only when `CONSUMER_IS_CLIENT_ACK` is `true`.**

Determine whether the binder natively populates the standard Spring `IntegrationMessageHeaderAccessor.ACKNOWLEDGMENT_CALLBACK` header with an `org.springframework.integration.acks.AcknowledgmentCallback` implementation. Use Grep on the consumer endpoint class and inbound message producer/adapter class for:

- `IntegrationMessageHeaderAccessor.ACKNOWLEDGMENT_CALLBACK`
- Import of `org.springframework.integration.acks.AcknowledgmentCallback`
- Any class implementing `AcknowledgmentCallback`

| Finding | Value |
|---|---|
| Binder sets `ACKNOWLEDGMENT_CALLBACK` header with an `AcknowledgmentCallback` implementation | `CONSUMER_ACK_NATIVE_SPRING_HEADER` = `true` |
| Binder uses a proprietary ack mechanism (own header name, own callback interface, per-message ack object) without setting the standard header | `CONSUMER_ACK_NATIVE_SPRING_HEADER` = `false` |

When `CONSUMER_ACK_NATIVE_SPRING_HEADER` is `false`, additionally document:
- **Proprietary ack header name** — the header key the binder uses to pass the ack-capable object (e.g., `GcpPubSubHeaders.ORIGINAL_MESSAGE`)
- **Proprietary ack class** — the fully qualified class name of the ack-capable object
- **Ack method signature** — the method to call for positive acknowledgment
- **Nack method signature** — the method to call for negative acknowledgment (if available)

These details are required by `plan-mi-impl.md` to generate a `@GlobalChannelInterceptor` that bridges the proprietary mechanism to the standard `ACKNOWLEDGMENT_CALLBACK` header.

The binder must support at least one mode. If neither is found, print: **"Binder does not appear to implement producer or consumer mode — verify the source URL points to a valid Spring Cloud Stream binder."** and use AskUserQuestion to confirm whether to continue with partial findings or stop.

---

### Step 8: Analyze message payload and headers

#### 8a. Producer direction — message payload requirements

Read the `createProducerMessageHandler()` method in the main binder class and the outbound message handler class it creates. Determine how it processes the incoming Spring `Message` payload before writing to the backend:

| Evidence | Payload requirement |
|---|---|
| Handler passes `byte[]` directly to the SDK write method | `none` — raw bytes forwarded |
| Handler casts or converts payload to `String` | `string` |
| Handler deserializes from JSON (`objectMapper.readValue(...)`) | `json-string` |
| Handler expects a specific Java type (domain object, `Map`, `Record`) | `typed:{fully.qualified.ClassName}` |
| Handler sets or requires a specific `contentType` header | `content-type:{type}` |

Store as `PRODUCER_PAYLOAD_REQUIREMENT`.

#### 8b. Consumer direction — message payload format

Read the `createConsumerEndpoint()` method in the main binder class and the inbound message producer class it creates. Determine what payload type it places into outbound Spring `Message` instances:

| Evidence | Payload format |
|---|---|
| Producer sets raw `byte[]` from SDK read | `none` |
| Producer converts SDK response to `String` | `string` |
| Producer serializes SDK response to JSON | `json-string` |
| Producer sets a specific Java type as payload | `typed:{fully.qualified.ClassName}` |
| Producer sets a `contentType` header | `content-type:{type}` |

Store as `CONSUMER_PAYLOAD_FORMAT`.

#### 8c. Special Spring message headers

Use Grep to search the local source for header-related patterns:
- Grep for `IntegrationMessageHeaderAccessor` across all Java files
- Grep for `ACKNOWLEDGMENT_CALLBACK` across all Java files
- Grep for `setHeader\(` or `getHeaders\(\)` in the main binder class and its handler/producer classes
- Grep for `HeaderMapper` or `headerPatterns` to understand header mapping behavior

For each header found, document:

| Header name | Direction | Type | Purpose | Required? |
|---|---|---|---|---|
| `{header-name}` | Producer / Consumer / Both | `String` / `Integer` / etc. | What it controls | Yes / No |

---

### Step 9: Build YAML configuration examples

Using all properties discovered in Steps 4-8, construct complete YAML configuration examples.

**Minimal-YAML principle (applies to all sub-steps):** Include **only required or essential properties** as active YAML lines. List all optional properties as **commented-out lines** with their default values so developers can see what is available without cluttering the working configuration.

#### 9a. Connection properties example

A minimal YAML snippet showing only the required connection properties needed for a basic local/testing setup (e.g., host, port, credentials). List optional properties in a trailing comment with defaults:

```yaml
{connection-prefix}:
  {required-key}: {value}
  # Optional (not shown): {property} (default: {value}), {property} (default: {value})
```

#### 9b. Authentication examples — one per variant

For each authentication variant cataloged in Step 5b, provide a self-contained YAML example showing connection properties configured for that auth type. Each example must contain **only the required properties** for that variant. List optional properties in a trailing comment with defaults.

#### 9c. Extended consumer properties example

A YAML snippet under the binding defaults namespace (`spring.cloud.stream.{binder-name}.default.consumer`). If Step 7b determined auto-ack, include the ack mode property set to the auto-ack value (e.g., `acknowledgeMode: AUTO`):

```yaml
spring:
  cloud:
    stream:
      {binder-name}:
        default:
          consumer:
            {required-key}: {value}
            # {optional-key}: {default-value}
            # {optional-key}: {default-value}
```

#### 9d. Extended producer properties example

Same format as 9c but under `spring.cloud.stream.{binder-name}.default.producer`.

#### 9e. Full working example

A multi-binder YAML configuration with Solace and the third-party binder. Must include:
- Connection properties for both binders (required only)
- Binding definitions with binder assignments
- Extended consumer and producer properties (required as active, optional as comments)

---

### Step 10: Write CLAUDE.md

#### 10a. Ensure output directory exists

If `{BINDER_OUTPUT_DIR}` does not exist, create it.

#### 10b. Validate output quality

Before writing, verify these output-quality checks:

- [ ] YAML examples use correct property keys from the actual properties classes
- [ ] No `abc`/`Abc`/`ABC` references in the output
- [ ] No `{VARIABLE}` placeholders remain in the output — all values are resolved
- [ ] Every Java class/interface name in the output uses its fully qualified package name — no unqualified short names
- [ ] SDK client information verified from the binder's dependency tree and source imports
- [ ] SDK Client section includes method signatures for publish/write, retrieve/poll, and destination management (or "not available")
- [ ] Testing Notes section includes all 9 required fields with resolved values from `{TEST_SUPPORT_CLAUDE_MD}`

#### 10c. Write the CLAUDE.md file

Write `{BINDER_CLAUDE_MD}` with the following sections. Use concrete, resolved values throughout — no `{VARIABLE}` placeholders. This file must be directly consumable by `plan-mi-impl.md` Step 2 without any additional web fetching.

**Required sections:**

````markdown
# {TECH_NAME_LOWER} Third-Party Binder Analysis

## What This Is

Third-party Spring Cloud Stream binder for {TECH_NAME_UPPER}.
- **Source**: {BINDER_3PARTY_SOURCE_URL}
- **Maven dependency**: {full <dependency> element}
- **Binder mode**: third-party (not custom-generated)

## Binder Identity

- **Binder name** (from `spring.binders`): `{RESOLVED_BINDER_NAME}`
- **Auto-configuration class**: `{fully.qualified.ConfigClass}`
- **Connection property prefix**: `{prefix}`
- **Extended binding properties namespace**: `spring.cloud.stream.{RESOLVED_BINDER_NAME}`

## Capability Modes

- **Producer supported**: {true/false} — {evidence summary}
- **Consumer supported**: {true/false} — {evidence summary}

### Producer Details
{If supported: message handler class, SDK write method, sync/async pattern}
{If not supported: "Producer mode is not implemented by this binder."}

### Consumer Details
{If supported: message producer class, SDK read/poll method, push/poll pattern}
{If not supported: "Consumer mode is not implemented by this binder."}

## Ack Modes

- **Producer ack mode**: `SYNC` or `ASYNC_BY_CALLBACK_HEADER` — {evidence}
- **Consumer ack mode**: `AUTO_ACK` or `CLIENT_ACK_BY_CALLBACK_HEADER` — {evidence}
- `PRODUCER_IS_ASYNC`: {true/false}
- `CONSUMER_IS_CLIENT_ACK`: {true/false}
- `CONSUMER_ACK_NATIVE_SPRING_HEADER`: {true/false} — whether the binder natively sets `IntegrationMessageHeaderAccessor.ACKNOWLEDGMENT_CALLBACK` with an `AcknowledgmentCallback` implementation. Only present when `CONSUMER_IS_CLIENT_ACK` is `true`.
{If CONSUMER_ACK_NATIVE_SPRING_HEADER is false:}
- **Proprietary ack header**: `{header key}` — the header the binder uses to pass the ack-capable object
- **Proprietary ack class**: `{fully.qualified.ClassName}`
- **Ack method**: `{signature}`
- **Nack method**: `{signature}`

## SDK Client

- **SDK library**: `{groupId}:{artifactId}:{version}`
- **Client class**: `{fully.qualified.ClientClass}`
- **Client creation pattern**: {one-line builder/factory description}
- **Close method**: `{method}`
- **Publish/write method**: `{signature with parameter types and return type}`
- **Retrieve/poll method**: `{signature with parameter types and return type}`
- **Ack/reject methods** (if applicable): `{signatures}`
- **Health/ping method** (if applicable): `{signature}`
- **Destination management methods** (if applicable): create, delete, purge — `{signatures or "not available"}`
- **Exception classes**: `{list of SDK exception types}`
- **Data types**: For each SDK class used as input to publish or returned by poll — list fields with Java types and payload format (String, byte[], Map, custom DTO)

## Connection Properties

### Properties table

| YAML key path | Java type | Default | Required | Classification | Description |
|---|---|---|---|---|---|
| ... | ... | ... | ... | ... | ... |

### Connection property structure

{For each property: full YAML key path, flat key for argsBuilder.put(), classification (root/nested/credential)}

## Authentication Variants

### {Variant 1 name}
- **Required properties**: ...
- **Optional properties**: ... (each with default value in parentheses)

```yaml
{Complete YAML example for this auth variant}
```

### {Variant 2 name}
...

## Extended Binding Properties

### Extended consumer properties

| Property key | Java type | Default | Description |
|---|---|---|---|
| ... | ... | ... | ... |

### Extended producer properties

| Property key | Java type | Default | Description |
|---|---|---|---|
| ... | ... | ... | ... |

### Other properties

{Any additional binder-level properties not covered above}

## Message Payload Requirements

### Producer direction (Solace -> Technology)
- **Payload requirement**: `{none|string|json-string|typed:...|content-type:...}`
- **Evidence**: {what was found in the message handler}

### Consumer direction (Technology -> Solace)
- **Payload format**: `{none|string|json-string|typed:...|content-type:...}`
- **Evidence**: {what was found in the message producer}

## Special Message Headers

| Header name | Direction | Type | Purpose | Required |
|---|---|---|---|---|
| ... | ... | ... | ... | ... |

{If no special headers: "No special message headers beyond standard Spring Integration headers."}

## YAML Configuration Examples

### Connection properties
```yaml
{from Step 9a}
```

### Authentication examples
{One subsection per variant from Step 9b}

### Extended consumer properties
```yaml
{from Step 9c}
```

### Extended producer properties
```yaml
{from Step 9d}
```

### Full multi-binder example
```yaml
{from Step 9e}
```

## Testing Notes

All information needed by `plan-mi-impl.md` and downstream MI test generation. This is the single source for test-related configuration in this CLAUDE.md.

**1. Test-support architecture**
- Value: `container-with-client` or `proxy-service-with-client`
- Source: `{TEST_SUPPORT_CLAUDE_MD}`

**2. Test-support extension class**
- Fully qualified name
- `container-with-client` → e.g., `com.example.tech.testextension.container.simple.TechSimpleContainerTestExtension`
- `proxy-service-with-client` → e.g., `com.example.tech.testextension.proxy.TechProxyTestExtension`
- Source: `{TEST_SUPPORT_CLAUDE_MD}`

**3. Test-support wrapper class**
- Fully qualified name (e.g., `com.example.tech.testextension.container.simple.TechTestContainerWithConnectedClient`)
- Source: `{TEST_SUPPORT_CLAUDE_MD}`

**4. Wrapper getter methods for connection injection**
- List all public getter method names exposed by the wrapper class
- Source: `{TEST_SUPPORT_CLAUDE_MD}` "Getter methods exposed"

**5. System property key → getter mapping table**
- One row per connection property:

| System property key | Wrapper getter method | Hardcoded value (if any) |
|---|---|---|
| `{connection-prefix}.host` | `getHost()` | — |
| `{connection-prefix}.port` | `getPort()` | — |

- Derived from: Connection Properties section cross-referenced with wrapper getters (field 4 above)

**6. SDK destination management methods for test lifecycle**
- For each lifecycle point below, reference the matching method from the SDK Client section of this CLAUDE.md. If the SDK has no equivalent, state "no-op" with rationale.
  - Create destination (@BeforeAll)
  - Delete destination (@AfterAll)
  - Delete all messages (@AfterEach)

**7. SDK poll/read method for test verification**
- Reference the retrieve/poll method from the SDK Client section of this CLAUDE.md. State which method to use for verifying a published message arrived at the backend.

**8. Environment file requirement (proxy-service-with-client only)**
- When `proxy-service-with-client`: list the `.env` variable names and their purposes
- When `container-with-client`: "Not applicable — container manages its own lifecycle and connection values"

**9. Test configuration approach**
- For third-party binders, there are no binder-level integration tests to generate. This field documents any binder-specific test configuration constraints that downstream MI test generation should be aware of (e.g., required Spring profiles, mandatory properties, initialization sequences).

## Key Design Decisions

{Notable architectural decisions observed in the binder source:
- Threading model
- Error handling approach
- Retry/backoff strategy
- Connection pooling
- Serialization/deserialization approach
- Any limitations or known issues documented in the source}
````

---

### Step 11: Print summary

Print:
- **Binder name**: `{RESOLVED_BINDER_NAME}`
- **Maven dependency**: groupId:artifactId:version
- **Capabilities**: producer, consumer, or both
- **Authentication variants**: list of supported types
- **SDK client**: library and primary class
- **Output file**: `{BINDER_CLAUDE_MD}`
- Any warnings about information that could not be determined from the source
