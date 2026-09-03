---
name: add-test-support
description: Create a Testcontainer wrapper with connected client and JUnit 5 extension in the test-support module of a generated micro-integration project. Reads configuration from configuration.md and technology details from the overview report.
tools_required:
  - Read
  - Write
  - Bash
  - Glob
  - WebFetch
---

# Add Test Support

## Role

You are an expert Java developer specializing in Testcontainers, JUnit 5 extensions, and Spring testing infrastructure. You have deep knowledge of container lifecycle management, JUnit 5 extension APIs (`ParameterResolver`, `BeforeAllCallback`, `AfterAllCallback`), and the target backend technology's Java SDK. You use this expertise to create reliable test infrastructure that correctly bridges containerized backends to Spring integration tests.

## Overview

Generate Java source files for the test-support module of a micro-integration project. Creates a Testcontainer wrapper (using the official Docker image for the target technology), a JUnit 5 extension for lifecycle management, and a basic integration test. Uses the `abc-test-support/` module in the workspace as a structural reference for file layout and patterns.

## Quick Start

```bash
/add-test-support
```

## Scope

This skill generates the **Java source files** inside an already-created test-support module. It assumes the module directory and POM already exist.

**What this skill creates:**
- `{Tech}TestContainerWithConnectedClient.java` — Testcontainer wrapper with a pre-configured client
- `{Tech}ContainerTestExtension.java` — JUnit 5 extension for container lifecycle and parameter injection
- `SimpleTestContainerIT.java` — Basic integration test verifying the container starts and client connects
- `CLAUDE.md` — Memory file documenting the test utility type, SDK client creation pattern, and connection parameter mapping
- POM updates if additional dependencies are needed (e.g., official Testcontainers module for the technology)

**What this skill does NOT do:**
- Create directory structures or POM files from scratch
- Analyse or fix Maven dependencies (that is `fix-maven-dependencies`)

## Implementation Instructions

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

**On validation failure — this is a hard stop:**

- Do **NOT** create any files.
- Print the heading: **"Configuration incomplete — cannot proceed"**
- Print a table listing every missing/unset variable with its section number and the Description from its row in the configuration table.
- Stop.

#### Required variables for this skill

After parsing, confirm these variables are available:

| Variable | Used for |
|---|---|
| `TARGET_PROJECT_FOLDER` | Root of the generated project where files will be written |
| `TEST_SUPPORT_ARTIFACT_ID` | Name of the test-support module directory |
| `TECH_NAME_LOWER` | Lowercase technology name, used in package names, class name prefixes, Docker image references |
| `TECH_NAME_UPPER` | Uppercase technology name, used in constants, log prefixes, comments |
| `PROJECT_ROOT_GROUP_ID` | The project's Maven groupId, used to derive package names |
| `CLIENT_SDK_DEPENDENCY` | The external client SDK Maven dependency element |
| `TECH_OVERVIEW_PATH` | Relative path to the technology overview report (e.g., `overview-sqs.md`). Verified source of SDK, Docker, and Testcontainers details |
| `TECH_NAME_CLASS_NAME_USE` | Technology name as it appears in Java class name prefixes (e.g., `Sqs`, `MongoDB`) |
| `TEST_SUPPORT_STRATEGY` | Which strategy to use: A, B, C, or D |
| `HOST_OS` | Host operating system — determines path separators |

#### Derive values (only after validation passes)

| Derived | Formula |
|---|---|
| `TEST_SUPPORT_ROOT_GROUP_PATH` | `PROJECT_ROOT_GROUP_ID` with dots replaced by `/` |
| `TEST_SUPPORT_PACKAGE_PATH` | `{TEST_SUPPORT_ROOT_GROUP_PATH}/{TECH_NAME_LOWER}/testextension` |
| `TEST_SUPPORT_PACKAGE` | `{PROJECT_ROOT_GROUP_ID}.{TECH_NAME_LOWER}.testextension` |
| `TEST_SUPPORT_MODULE_DIR` | `{TARGET_PROJECT_FOLDER}/{TEST_SUPPORT_ARTIFACT_ID}` |
| `TEST_SUPPORT_SRC_DIR` | `{TEST_SUPPORT_MODULE_DIR}/src/main/java/{TEST_SUPPORT_PACKAGE_PATH}` |
| `TEST_SUPPORT_TEST_DIR` | `{TEST_SUPPORT_MODULE_DIR}/src/test/java/{TEST_SUPPORT_PACKAGE_PATH}` |

### Step 2: Verify target module exists and is valid

Before generating any files, verify that the target module exists and is correctly set up. This is a mandatory gate — do not proceed if any check fails.

#### 2a. Directory and POM existence

1. Check that `{TEST_SUPPORT_MODULE_DIR}` directory exists.
2. Check that `{TEST_SUPPORT_MODULE_DIR}/pom.xml` exists.

If either is missing, print: **"Target module not found at `{TEST_SUPPORT_MODULE_DIR}`"** and stop.

#### 2b. POM content validation

Read `{TEST_SUPPORT_MODULE_DIR}/pom.xml` and verify:

1. **`<groupId>`** matches `{PROJECT_ROOT_GROUP_ID}`.
2. **`<artifactId>`** matches `{TEST_SUPPORT_ARTIFACT_ID}`.
3. **Client SDK dependency is present** — the POM contains a `<dependency>` whose `<groupId>` and `<artifactId>` match those inside `{CLIENT_SDK_DEPENDENCY}`.
4. **No leftover template references** — the POM must not contain `com.solace.samples`, `abc-client`, `abc-service`, or `abc-test-support`. If any are found, replace them with the correct project values (`{PROJECT_ROOT_GROUP_ID}`, `{TEST_SUPPORT_ARTIFACT_ID}`, or the appropriate artifact name) and print a note listing each replacement made.
5. **POM metadata cleanup** — check the `<name>` and `<description>` elements for leftover ABC/template text (e.g., `Abc`, `ABC`, `abc`). If found, replace them with the target technology name:
   - `<name>` → `{TECH_NAME_CLASS_NAME_USE} Integration Test Support`
   - `<description>` → `Test support infrastructure for {TECH_NAME_CLASS_NAME_USE} integration tests`
   Print a note listing each replacement made.

If checks 1–3 fail, print: **"Target module POM validation failed"** with a table of failures and stop.

#### 2c. Source directories

1. Check that `{TEST_SUPPORT_SRC_DIR}` exists. If it does not, create it.
2. If `{TEST_SUPPORT_TEST_DIR}` does not exist, create it.

### Step 3: Read the technology overview report

Read the file at `{TECH_OVERVIEW_PATH}` (resolved relative to the workspace root). This file contains verified information about the technology.

If the file does not exist at the configured path, print an error: **"Technology overview report not found at `{TECH_OVERVIEW_PATH}` — run `/analyze-integration-tech {TECH_NAME_LOWER}` first and update `TECH_OVERVIEW_PATH` in configuration.md"** and stop.

Read the **entire** file and extract the following information. Do not rely on section numbers — locate each topic by its content regardless of where it appears in the report.

| Information needed |
|---|
| **Official Testcontainers module** — Maven dependency if available, or "not available" |
| **Official Docker image** — image name and recommended tag (e.g., `localstack/localstack:3.4`) |
| **Exposed ports** — port numbers the container exposes |
| **Container readiness strategy** — health check endpoint or log message to wait for |
| **Environment variables** — authentication setup, password config |
| **Java SDK main classes** — the factory/driver/session/client classes and connection pattern |
| **Connection code pattern** — Complete Cycle Example showing connect → operate → close |
| **Authentication pattern** — how to authenticate with the Java SDK |
| **SDK exception classes** — primary exceptions for error handling |

### Step 4: Determine container strategy

Read `TEST_SUPPORT_STRATEGY` from configuration.md (section 3b). The value must be one of: **A**, **B**, **C**, **D**.

| Strategy | Meaning |
|---|---|
| **A** | Official Testcontainers module — requires `TESTCONTAINERS_MODULE_DEPENDENCY` from section 3b |
| **B** | Official Docker image with GenericContainer |
| **C** | Cloud/remote backend proxy (no container) |
| **D** | Local binaries packaged via Jib |

Validate that strategy-specific variables from section 3b are present and non-empty. If any are missing, stop with **"Configuration incomplete"** and list the missing variables.

Print: **"Container strategy: {A|B|C|D}"**

#### Delegate to strategy implementation

Each strategy has its own implementation file with detailed instructions for generating the wrapper class, extension, integration test, and POM updates. Read **only** the file for the chosen strategy:

| Strategy | Read file |
|---|---|
| A | `.claude/skills/add-test-support/references/strategy-a.md` |
| B | `.claude/skills/add-test-support/references/strategy-b.md` |
| C | `.claude/skills/add-test-support/references/strategy-c.md` |
| D | `.claude/skills/add-test-support/references/strategy-d.md` |

**Context handoff — derived variables available to the strategy file and claude-md-template.md:**

The delegate strategy files and the CLAUDE.md template reference these derived variables by name. All must be resolved before delegation.

| Variable | Value |
|---|---|
| `TEST_SUPPORT_ROOT_GROUP_PATH` | `{PROJECT_ROOT_GROUP_ID}` with dots → `/` |
| `TEST_SUPPORT_PACKAGE_PATH` | `{TEST_SUPPORT_ROOT_GROUP_PATH}/{TECH_NAME_LOWER}/testextension` |
| `TEST_SUPPORT_PACKAGE` | `{PROJECT_ROOT_GROUP_ID}.{TECH_NAME_LOWER}.testextension` |
| `TEST_SUPPORT_MODULE_DIR` | `{TARGET_PROJECT_FOLDER}/{TEST_SUPPORT_ARTIFACT_ID}` |
| `TEST_SUPPORT_SRC_DIR` | `{TEST_SUPPORT_MODULE_DIR}/src/main/java/{TEST_SUPPORT_PACKAGE_PATH}` |
| `TEST_SUPPORT_TEST_DIR` | `{TEST_SUPPORT_MODULE_DIR}/src/test/java/{TEST_SUPPORT_PACKAGE_PATH}` |

In addition, the strategy receives:

- All **input configuration variables** from Step 1 (`TARGET_PROJECT_FOLDER`, `TECH_NAME_LOWER`, `TECH_NAME_UPPER`, `TECH_NAME_CLASS_NAME_USE`, `PROJECT_ROOT_GROUP_ID`, `CLIENT_SDK_DEPENDENCY`, `TECH_OVERVIEW_PATH`, `TEST_SUPPORT_ARTIFACT_ID`, `TEST_SUPPORT_STRATEGY`, `HOST_OS`)
- All **technology details** extracted from the overview report in Step 3 (SDK classes, connection pattern, authentication, Docker image, ports, readiness strategy, exceptions)
- The **strategy-specific variables** validated in Step 4 (e.g., `TESTCONTAINERS_MODULE_DEPENDENCY` for strategy A)

You may re-read the overview report or configuration.md inside the strategy if information is missing while executing the strategy steps.

### Step 5: Generate CLAUDE.md (mandatory — blocks skill completion)

**When:** After build verification passes inside the strategy (the code compiles). Before the strategy's final summary step.

Generate a `CLAUDE.md` file in `{TEST_SUPPORT_MODULE_DIR}/` (the module root, next to `pom.xml`). Read the template at `.claude/skills/add-test-support/references/claude-md-template.md` and follow its instructions. Fill every section using information already gathered during Steps 3–4 and the strategy execution. Do not re-read the overview report — use what is already in context.

**This step is mandatory and blocks completion of the entire skill.** The strategy's final summary step must not be printed until `CLAUDE.md` is written. If for any reason the file cannot be created, print an error and stop — do not skip it.

The generated `CLAUDE.md` serves as the canonical reference for downstream skills (especially `add-binder`) to learn:
- What type of test utility is in place (Testcontainers module, GenericContainer, proxy, or Jib)
- Container or remote backend details (image, ports, credentials, env vars)
- SDK client class and creation pattern
- The exact mapping from container getters / env vars to SDK client builder inputs
- All public getter methods exposed by the wrapper/proxy class

Each strategy file includes this step at the correct position (after build verification, before summary).
