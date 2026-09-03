---
name: clean-claude-files
description: Transform generated CLAUDE.md files in a micro-integration project from code-generation blueprints into clean, developer-facing reference documentation. Strips generation instructions (source transformation guides, test Java plans, template mappings, argsBuilder injection details) and restructures what remains into a comprehensive guide covering architecture, configuration with YAML examples, authentication options, binder features, ack behavior, payload formats, and project structure. Run this after the micro-integration has been generated and verified successfully. Use this skill whenever the user mentions cleaning CLAUDE.md files, converting generation docs to reference docs, or asks for documentation cleanup after micro-integration generation.
tools_required:
  - Read
  - Write
  - Edit
  - Glob
  - Bash
---

# Clean CLAUDE Files

## Role

You are a technical documentation specialist with deep knowledge of Spring Cloud Stream, Solace micro-integrations, and the Solace Micro-Integration Framework. You transform internal code-generation blueprints into clear, developer-facing reference documentation.

## Overview

After a micro-integration project is generated and verified, the project contains CLAUDE.md files that served as blueprints for code generation. These files are packed with valuable technical details (connection properties, ack modes, extended binding properties, authentication options, SDK patterns) but they also contain generation-specific instructions that are no longer relevant (source transformation guides, template replacement tables, test Java plans with code snippets, argsBuilder injection patterns).

This skill reads each CLAUDE.md, identifies what is useful reference material vs. what is generation scaffolding, and rewrites each file as a clean, comprehensive reference document.

## Quick Start

```bash
/clean-claude-files
```

## Integration with generate-mi

This skill is designed to be invoked as a final step after the micro-integration project has been generated and verified successfully. It can be called:

- **Standalone**: Run `/clean-claude-files` manually after generation is complete
- **From generate-mi**: The `add-microintegration` skill can invoke `/clean-claude-files` as a post-verification step using the Skill tool

Either way, this skill reads `configuration.md` independently and requires no parameters to be passed in.

## File Safety Constraint

**This skill ONLY modifies CLAUDE.md files.** No other files in the project may be created, edited, or deleted — no Java source files, no YAML configuration files, no POM files, no test files, no scripts, nothing. The three CLAUDE.md files listed in the Scope section below are the complete and exhaustive set of files this skill is permitted to write. If a step seems to require changing any other file, stop and report the issue instead of making the change.

## Scope

This skill processes exactly three CLAUDE.md files with aggressive, full-restructure cleanup:

1. **MI module** (`{INTEGRATION_APP_ARTIFACT_ID}/CLAUDE.md`) - The main micro-integration application
2. **Binder module** (`{BINDER_ARTIFACT_ID}/CLAUDE.md`) - The Spring Cloud Stream binder (custom or third-party)
3. **Test-support module** (`{TEST_SUPPORT_ARTIFACT_ID}/CLAUDE.md`) - The Testcontainers wrapper and JUnit extension

No other files are read for modification or created. All other project files (Java sources, YAML configs, POMs, test resources) are read-only inputs used for context.

Every module's CLAUDE.md gets a full restructure into clean reference documentation. No module gets a "light" pass.

---

## Step Execution Rules

**Step execution order is mandatory.** Each step must complete successfully before the next step begins. Do not skip ahead or parallelize steps.

---

## Naming Conventions

In all cleaned CLAUDE.md content:

- Use **"Solace event broker"** — never "Solace PubSub+" or "PubSub+".
- Use **"Solace Micro-Integration Framework"** — never "connector framework" or "Connector Framework".

If any source text uses the old names, replace them when writing the cleaned file.

---

## Step 1: Read configuration.md and locate CLAUDE.md files

Read `configuration.md` from the workspace root. Extract these variables:

| Variable | Purpose |
|---|---|
| `TARGET_PROJECT_FOLDER` | Project root directory |
| `INTEGRATION_APP_ARTIFACT_ID` | MI module directory name |
| `BINDER_ARTIFACT_ID` | Binder module directory name |
| `TEST_SUPPORT_ARTIFACT_ID` | Test-support module directory name |
| `TECH_NAME_UPPER` | Technology display name (e.g., "Neo4j", "RabbitMQ") |
| `TECH_NAME_LOWER` | Technology lowercase name (e.g., "neo4j", "rabbitmq") |
| `BINDER_SKIP_GENERATION` | Whether binder is third-party (`true`) or custom (`false`) |

Derive paths:
- `MI_CLAUDE_MD` = `{TARGET_PROJECT_FOLDER}/{INTEGRATION_APP_ARTIFACT_ID}/CLAUDE.md`
- `BINDER_CLAUDE_MD` = `{TARGET_PROJECT_FOLDER}/{BINDER_ARTIFACT_ID}/CLAUDE.md`
- `TEST_SUPPORT_CLAUDE_MD` = `{TARGET_PROJECT_FOLDER}/{TEST_SUPPORT_ARTIFACT_ID}/CLAUDE.md`

Read all three CLAUDE.md files. Record the word count of each original file (for the summary). If a file does not exist, skip it and note the skip.


## Step 2: Clean the MI module CLAUDE.md

This is the most important file. It becomes the primary reference document for the micro-integration.

### What to keep and enhance

**"What This Is"** - Keep as-is. This is the introduction.

**"Binder Identity"** - Keep. Tells developers what binder name to use in YAML config, what the connection property prefix is, and what the extended binding namespace is. Present as a clean reference table.

**"Binder Capability Modes"** - Keep but humanize. Remove internal flag names (`VERIFIED_CONSUMER_SUPPORTED`, `VERIFIED_PRODUCER_SUPPORTED`). State clearly what is and isn't supported and what that means for the developer: which message directions work, what happens if you try the unsupported direction.

**"Ack Modes"** - Keep the substance but reframe entirely. Developers need to understand how message acknowledgment works. Remove all framework enum references (`ProducerAckMode.ASYNC_BY_CALLBACK_HEADER`, `ConsumerAckMode.AUTO_ACK`, `ConsumerAckMode.CLIENT_ACK_BY_CALLBACK_HEADER`) and internal flag names (`CONSUMER_ACK_BRIDGING_REQUIRED`, `PRODUCER_IS_ASYNC`, `CONSUMER_IS_CLIENT_ACK`). Write in plain language: "Producer writes are asynchronous — the binder uses async callbacks to confirm delivery" or "Consumer acknowledgment is automatic — messages are acknowledged after processing completes."

**"Connection Properties Mapping"** - Keep the property keys and their purposes. Remove these columns entirely: `Flat key for argsBuilder`, `Test-support getter`, `Dynamic/Static`, `Hardcoded value`. Instead, present as a complete YAML configuration example with inline comments. Include all authentication variants the binder supports with a separate YAML example for each (basic auth, no auth, bearer token, etc.).

**"Extended Binding Properties"** - Keep and enhance. Present each property with its default value, purpose, and a YAML example. Group by direction (consumer properties, producer properties).

**"Message Payload Requirements"** - Keep. Remove internal flag names (`PRODUCER_PAYLOAD_REQUIREMENT`, `CONSUMER_PAYLOAD_FORMAT`). Describe what formats work and what constraints exist.

### What to remove entirely

**"Source Transformation Guide"** - Remove completely. Contains template-to-target replacement tables (`Abc` -> `Neo4j`, package mappings), per-file transformation instructions, bean registration details. None of this has reference value after generation.

**"Test Java Plan"** - Remove completely. Contains Java code blueprints for test classes (BaseTest.java, producer/consumer test flows, import lists, `@ExtendWith` annotations). The actual code exists in the generated files.

**"Test Configuration Plan"** - Transform into a concise "Testing" section. Keep only: what test profiles exist, how to run tests, what each profile tests. Remove: binding direction YAML assignments, `argsBuilder` injection references, commented-out connection property blocks, per-profile YAML dumps.

**"Generated Files"** - Transform into "Project Structure". Keep the file list, describe what each file does, remove the "Generated Files" framing.

### Target structure for cleaned MI CLAUDE.md

```markdown
# {Project Title from original heading}

## Overview
{Rewritten from "What This Is" — what this micro-integration does, what technologies it bridges, which directions are active, what framework it's built on}

## Capabilities
{Which directions are supported (producer/consumer/both), practical implications, what happens in each direction}

## Configuration

### Connection Properties
{Full YAML example with inline comments explaining each property}

### Authentication
{YAML example for each auth method the binder supports — e.g., basic auth, no auth, bearer token}
{Label each example clearly}

### Binding Configuration
{Complete YAML example showing Spring Cloud Stream bindings — input/output channels, binder assignments, destinations}

### Extended Binding Properties
{Per-direction tables: property name, default, purpose}
{Complete YAML example showing how to set extended properties}

### Workflow Configuration
{How to enable/disable workflows with YAML example}

### Logging
{Logging configuration YAML with all relevant package names}

## Binder Behavior

### Acknowledgment
{Plain-language description of how message delivery confirmation works in each direction}

### Message Payload Format
{What formats are accepted/produced, any constraints or unsupported types}

## Testing
{Available test profiles and what each one tests}
{How to run the tests (Maven command)}

## Project Structure
{File listing with brief description of what each file does}
```

### Enrichment from the binder CLAUDE.md

Cross-reference the binder CLAUDE.md to enrich the MI document:

- Pull in complete YAML examples for multi-binder configuration if documented
- Add authentication variant examples (basic, none, bearer, kerberos, etc.) if the binder supports more than what the MI doc mentions
- Include health actuator configuration if documented
- Add data type constraints or format notes that help developers understand payload requirements

Do not duplicate the entire binder CLAUDE.md — only pull in what adds clear value.

---

## Step 3: Clean the binder module CLAUDE.md

Apply the same aggressive restructure as the MI module. The binder CLAUDE.md becomes a comprehensive reference for the Spring Cloud Stream binder itself.

### What to remove entirely

- **"Boolean flags summary"** sections and internal flag definitions (`PRODUCER_IS_ASYNC`, `CONSUMER_IS_CLIENT_ACK`, etc.)
- **Any `argsBuilder` injection tables** or test-support getter mapping tables
- **"System property key → getter mapping table"** sections (generation artifact for mapping connection props to test-support getters)
- **"Connection Properties → Java Mapping"** table if it duplicates the Configuration section — consolidate into one clean Configuration section. If it contains unique information (Java field names, validation annotations), keep only what's useful for developers extending the binder
- **Template replacement instructions** or any "Source Transformation Guide" equivalent
- **Generation-specific cross-references** like "overview: `SessionConfig.builder().withDatabase()`" source attributions in tables
- **Environment file requirement** sections that just say "Not applicable"

### What to keep and restructure

- **What This Is** → **Overview** — rewrite as a clear introduction
- **Binder Identity** → fold into **Overview** — binder name, package, auto-config class
- **Capability Modes** → **Capabilities** — remove internal flag names, describe in plain language
- **Ack Modes** → **Acknowledgment** under Binder Behavior — remove enum references, write plainly
- **Build & Test** → **Build & Test** — keep the Maven commands and Java version requirement
- **Project Layout** → **Project Structure** — keep directory tree and file descriptions
- **Key Design Decisions** → **Design Decisions** — keep as-is, this is valuable architectural context
- **Configuration** → **Configuration** — keep all YAML namespace examples, ensure they're complete. Merge any connection property tables into this section as YAML examples with comments rather than separate mapping tables
- **SDK Client** → **SDK Reference** — keep client creation, write/read operations, data types, exceptions. This is valuable for developers extending the binder or writing custom tests
- **Message Payload Requirements** → **Message Format** — remove internal flag names, describe formats clearly
- **Extended Binding Properties** → fold into **Configuration** section
- **Dependencies to Know About** → **Dependencies** — keep
- **Testing Notes** → **Testing** — keep test-support architecture, extension class, container wrapper, getter methods, SDK lifecycle methods. Remove generation-specific cross-references and source attribution comments. Remove the "System property key → getter mapping table" entirely

### Target structure for cleaned binder CLAUDE.md

```markdown
# {Binder Title}

## Overview
{What this binder is, what technology it connects to, which directions it supports}

## Capabilities
{Producer/consumer support, with practical description of what each does}

## Design Decisions
{Key architectural choices and their rationale}

## Configuration

### Connection Properties
{Complete YAML example with inline comments}

### Authentication
{YAML example for each supported auth method}

### Extended Binding Properties
{Per-direction property tables with YAML examples}

### Multi-Binder Setup
{YAML example for running multiple binder instances — if applicable}

### Health Actuator
{Health endpoint configuration YAML}

## Message Format

### Supported Payload Types
{What payload formats the binder accepts, with code examples}

### Data Types
{Type mapping table between Java types and target technology types}

## Binder Behavior

### Acknowledgment
{How acks work in each direction, in plain language}

### Destination Mapping
{How Spring Cloud Stream destinations map to technology concepts}

### Header Handling
{How message headers are processed}

## SDK Reference

### Client Creation
{Code example for creating the SDK client}

### Write Operations
{Sync and async write patterns with code examples}

### Read Operations
{Read patterns with code examples — if applicable}

### Exceptions
{Exception hierarchy and when each is thrown}

## Testing

### Test Architecture
{Test-support pattern, extension class, container wrapper}

### Available Test Methods
{Getter methods and SDK operations useful for writing tests}

### Test Profiles
{What test profiles exist and what they verify}

## Build & Test
{Maven commands, Java version, prerequisites}

## Project Structure
{Directory tree with file descriptions}

## Dependencies
{Key dependencies and their purposes}
```

---

## Step 4: Clean the test-support module CLAUDE.md

Apply the same aggressive restructure. The test-support CLAUDE.md becomes a clear guide for using the Testcontainers wrapper and JUnit extension.

### What to remove entirely

- **"System property key → getter mapping table"** — generation artifact for mapping binder connection properties to wrapper getters
- **`argsBuilder` references** — internal to the code generation process
- **"Getter catalog"** tables with `Dynamic/Static` or `Hardcoded value` columns — replace with a clean method reference
- **Generation-specific architecture pattern labels** like `container-with-client` — just describe what it does
- **Source attribution comments** like "test-support: `getBoltUrl()`" that appear in other module docs referencing this one

### What to keep and restructure

- **Overview** — what this module provides (Testcontainers wrapper + JUnit 5 extension)
- **Container image** — what Docker image is used, any configuration
- **JUnit 5 extension** — class name, how to use `@ExtendWith`, how to inject the container
- **Getter methods** — present as a clean API reference table: method name, return type, what it returns. Developers need this when writing custom tests
- **SDK client operations** — how to use the wrapped client for test setup/teardown/verification. Include code examples for common operations (create destination, delete messages, poll/verify)
- **Dependencies** — what this module depends on

### Target structure for cleaned test-support CLAUDE.md

```markdown
# {Test Support Title}

## Overview
{What this module provides — Testcontainers wrapper with connected SDK client and JUnit 5 extension}
{What Docker image it wraps}

## Usage

### JUnit 5 Extension
{Extension class name}
{How to use @ExtendWith}
{How to inject the container instance into your test}

### Container Lifecycle
{What happens on startup — container starts, client connects}
{What happens on shutdown — client closes, container stops}

## API Reference

### Connection Getters
{Table: method name | return type | description}
{These methods provide connection details for configuring the binder in tests}

### SDK Client Access
{How to get the underlying SDK client from the wrapper}
{Code example}

### Common Test Operations
{Code examples for each lifecycle operation:}
{- Creating a destination (or noting it's a no-op with explanation)}
{- Cleaning up / deleting test data}
{- Reading / polling for verification}

## Dependencies
{Key dependencies — Testcontainers module, SDK client library}
```

---

## Step 5: Validate and summarize

After rewriting all CLAUDE.md files:

### Validation pass

Re-read each cleaned file and verify these generation artifacts are completely absent:

**Terms that must not appear anywhere in cleaned files:**
- `argsBuilder`
- `VERIFIED_CONSUMER_SUPPORTED` or `VERIFIED_PRODUCER_SUPPORTED`
- `PRODUCER_PAYLOAD_REQUIREMENT` or `CONSUMER_PAYLOAD_FORMAT`
- `CONSUMER_ACK_BRIDGING_REQUIRED`
- `PRODUCER_IS_ASYNC` or `CONSUMER_IS_CLIENT_ACK`
- `MI_PACKAGE`, `MI_TARGET_MODULE_DIR`, `MI_TARGET_SRC_DIR`
- `BINDER_CLAUDE_MD` or `TEST_SUPPORT_CLAUDE_MD`
- `Source Transformation Guide` (as a heading)
- `Test Java Plan` (as a heading)
- `Test Configuration Plan` (as a heading)
- Template replacement tables (`Abc` -> `Something`, `com.solace.samples` -> `Something`)
- `Flat key for argsBuilder` (table column)
- `Test-support getter` (table column)
- `Dynamic/Static` (table column in connection property context)
- `Hardcoded value` (table column)
- `Boolean flags summary` (as a heading)
- References to other module CLAUDE.md files as sources (e.g., "from binder CLAUDE.md")
- `add-binder`, `add-test-support`, `add-microintegration`, `analyze-3party-binder` (skill invocation references)

**Quality checks:**
- All YAML examples are syntactically valid (proper indentation, no broken strings)
- Authentication section covers all variants the binder supports
- Configuration examples are complete and self-contained (a developer could copy-paste them)
- No orphaned section references ("see Section X" pointing to removed content)
- Every section heading in the target structure has content (no empty sections)

If any validation issue is found, fix it before proceeding.

### Summary

Print a summary table:

| File | Status | Words Before | Words After | Sections Removed | Sections Added/Enhanced |
|---|---|---|---|---|---|
| MI CLAUDE.md | Cleaned | N | N | list | list |
| Binder CLAUDE.md | Cleaned | N | N | list | list |
| Test-support CLAUDE.md | Cleaned | N | N | list | list |

For each file, also note any authentication variants that were added from cross-referencing, and any significant enrichment from other module docs.
