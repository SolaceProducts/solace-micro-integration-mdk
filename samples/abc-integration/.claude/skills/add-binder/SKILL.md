---
name: add-binder
description: Generate Spring Cloud Stream binder Java source files in the binder module of a generated micro-integration project. Supports producer, consumer, or both as configured by BINDER_TYPE in configuration.md. Reads configuration from configuration.md and technology details from the overview report. Uses the ABC binder as the structural template.
tools_required:
  - Read
  - Write
  - Bash
  - Glob
  - Edit
  - Task
  - WebFetch
  - AskUserQuestion
---

# Add Binder

## Role

You are an expert Java developer specializing in Spring Cloud Stream binder development. You have deep knowledge of the Spring Cloud Stream binder SPI (`AbstractMessageChannelBinder`, `ProvisioningProvider`, `ExtendedBindingProperties`), Spring Boot auto-configuration, and the target backend technology's Java SDK. You use this expertise to adapt the ABC template into a correct, idiomatic binder implementation that properly integrates the SDK's connection, publishing, and consumption patterns with the Spring Cloud Stream framework.

## Overview

Generate Java source files for a Spring Cloud Stream binder inside an already-created binder module. The `BINDER_TYPE` variable in `configuration.md` controls which capabilities are generated (producer, consumer, or both). Reads the ABC binder template files as structural blueprints, applies technology-specific transformations using configuration variables and the technology overview report, and writes the results to the target module.

## Quick Start

```bash
/add-binder
```

## Scope

This skill generates the **Java source files, META-INF registrations, and integration tests** inside an already-created binder module. It assumes the module directory and POM already exist (created by `init-mi-project`).

**What this skill does NOT do:**
- Create the client library (separate module, assumed to exist)
- Create directory structures or POM files (that is `init-mi-project`)
- Analyse or fix Maven dependencies (that is `fix-maven-dependencies`)
- Create test-support infrastructure (that is `add-test-support`)
- Auto-provision destinations on the backend (out of scope)

---

## Step Execution Rules

**Step execution order is mandatory.** Each step must complete successfully before the next step begins. Do not skip ahead, parallelize steps, or begin a step while a previous step is incomplete. If a step fails, stop and report the failure — do not proceed to subsequent steps.

These rules apply to:
- All steps in this file (Steps 1, 1a, 1b, 2, 3, 4, 5, 6, 7, 8)
- All steps in delegated files (`references/plan-binder-impl.md`, `references/generate-shared.md`, `references/generate-common-tests.md`, `references/generate-producer.md`, `references/generate-sync-producer-tests.md`, `references/generate-async-producer-tests.md`, `references/generate-consumer.md`, `references/generate-consumer-tests.md`)
- Cross-file transitions — a delegated file must complete all its steps before control returns and the next step in this file begins

---

## Implementation Instructions

### Step 1: Parse and validate configuration.md

Read `configuration.md` from workspace root. Scan **every** markdown table across **all** sections (including subsections like 2a, 2b, 3b, etc.). Each table row whose first column contains a back-ticked `Variable` name defines one configuration variable. Extract every such variable by reading its value from the **Configuration Value** column.

`configuration.md` is the single source of truth for which variables exist. Do not maintain a separate list of expected variable names in this skill.

#### Configuration validation (mandatory — run before anything else)

Each configuration table row has an **Example Value** column and a **Configuration Value** column. A variable's value is taken **exclusively** from the **Configuration Value** column. The Example Value column is documentation only — it must **never** be used as a fallback or default.

**Validation rules — ALL must pass or the skill stops immediately:**

1. **Every variable** found in the configuration tables must have a **non-empty Configuration Value**. A cell that is empty, contains only whitespace, or is absent means the variable is **unset**. **Exception:** variables whose description starts with "*(optional)*" in the required variables table below are exempt from this rule — they may be empty.
2. If a configuration section is commented out (e.g. wrapped in `[//]: #` markdown comments), **skip that section entirely** — do not parse or validate its variables.
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
| `BINDER_ARTIFACT_ID` | Name of the binder module directory |
| `TECH_NAME_LOWER` | Lowercase technology name, used in package names, config property prefixes, binder name |
| `TECH_NAME_UPPER` | Uppercase technology name, used in constants, log prefixes |
| `TECH_NAME_CLASS_NAME_USE` | Technology name as it appears in Java class name prefixes (e.g., `Sqs`, `MongoDB`) |
| `PROJECT_ROOT_GROUP_ID` | The project's Maven groupId, used to derive package names |
| `CLIENT_SDK_DEPENDENCY` | The external client SDK Maven dependency element |
| `TECH_OVERVIEW_PATH` | Relative path to the technology overview report |
| `BINDER_TYPE` | Comma-separated list of capabilities to generate: `producer`, `consumer`, or `producer,consumer` |
| `BINDER_PACKAGE` | Java package for all binder source files (e.g., `com.solace.microintegrations.binder.sqs`) |
| `TEST_SUPPORT_ARTIFACT_ID` | Name of the test-support module directory (used by the planning step to inspect test-support source code) |
| `HOST_OS` | Host operating system — determines path separators |
| `JDK_PATH` | Path to JDK installation for build verification |
| `BINDER_SKIP_GENERATION` | When `true`, skip binder generation entirely — checked in Step 1a |
| `BINDER_3PARTY_SOURCE_URL` | *(optional)* — GitHub URL of an existing third-party binder. May be empty. Checked in Step 1b |
| `BINDER_3PARTY_DEPENDENCY` | *(optional)* — Maven dependency for the third-party binder. May be empty. Checked in Step 1b |

#### Derive values (only after validation passes)

| Derived | Formula | Purpose |
|---|---|---|
| `BINDER_NAME` | `{TECH_NAME_LOWER}` | Binder name registered in `spring.binders`, referenced in per-binding `binder` and multi-binder `type` |
| `BINDER_CONNECTION_PREFIX` | `{TECH_NAME_LOWER}` | Root-level config namespace for connection properties (`@ConfigurationProperties` prefix) |
| `EXTENDED_BINDING_PREFIX` | `spring.cloud.stream.{TECH_NAME_LOWER}` | Namespace for extended per-binding properties and `DEFAULTS_PREFIX` |
| `BINDER_PACKAGE_PATH` | `BINDER_PACKAGE` with dots replaced by `/` | |
| `BINDER_TARGET_MODULE_DIR` | `{TARGET_PROJECT_FOLDER}/{BINDER_ARTIFACT_ID}` | |
| `BINDER_TARGET_SRC_DIR` | `{BINDER_TARGET_MODULE_DIR}/src/main/java/{BINDER_PACKAGE_PATH}` | |
| `BINDER_TARGET_RESOURCES_DIR` | `{BINDER_TARGET_MODULE_DIR}/src/main/resources` | |
| `BINDER_TARGET_TEST_SRC_DIR` | `{BINDER_TARGET_MODULE_DIR}/src/test/java/{BINDER_PACKAGE_PATH}` | |
| `BINDER_TARGET_TEST_RESOURCES_DIR` | `{BINDER_TARGET_MODULE_DIR}/src/test/resources` | |
| `TEST_SUPPORT_MODULE_DIR` | `{TARGET_PROJECT_FOLDER}/{TEST_SUPPORT_ARTIFACT_ID}` | Root of the test-support module — used by `references/plan-binder-impl.md` to inspect test-support source code and CLAUDE.md |

---

### Step 1a: Check BINDER_SKIP_GENERATION

**Blocked by:** Step 1 must complete successfully.

If `BINDER_SKIP_GENERATION` is set to `true` (case-insensitive), skip binder generation entirely:

- Print: **"Binder generation skipped — `BINDER_SKIP_GENERATION` is `true`."**
- Do **NOT** create any files or directories.
- Do **NOT** execute Steps 1b–8.
- Stop.

If `BINDER_SKIP_GENERATION` is `false`, proceed to Step 1b.

---

### Step 1b: Check for existing third-party Spring Cloud Stream binder

**Blocked by:** Step 1a must complete (and not skip).

Before generating a custom binder, check whether an existing third-party binder already covers the target technology. This step is a mandatory gate — if an active binder is found, the developer must explicitly confirm that custom generation should proceed.

#### 1b-1. Check `BINDER_3PARTY_SOURCE_URL` from configuration

If `BINDER_3PARTY_SOURCE_URL` has a non-empty Configuration Value, a third-party binder has already been identified. Record its URL and the value of `BINDER_3PARTY_DEPENDENCY` (if set). Proceed to **1b-3**.

#### 1b-2. Check the technology overview report

If `BINDER_3PARTY_SOURCE_URL` is empty, read the technology overview report at `{TECH_OVERVIEW_PATH}` (relative to workspace root). Look for **section 3.5 (Spring Cloud Stream Binder)** or any section that documents an existing Spring Cloud Stream binder for the technology.

If the report documents a binder **and** does not indicate the repository is archived or unmaintained, record the binder name, GitHub URL, and Maven dependency from the report. Proceed to **1b-3**.

If no binder is mentioned, or the report states the binder is archived/unmaintained, proceed directly to **Step 2** — custom binder generation is needed.

#### 1b-3. Ask whether to proceed with custom binder generation

An existing, actively maintained Spring Cloud Stream binder has been identified. Print:

**"An existing Spring Cloud Stream binder was found for {TECH_NAME_UPPER}:"**
- **Source:** {GitHub URL}
- **Dependency:** {Maven dependency, if known}

**"A custom binder is not required when a maintained third-party binder exists. Do you want to proceed with custom binder generation anyway?"**

Use `AskUserQuestion` to present the choice:
- **Skip binder generation** — stop this skill entirely; no files are created
- **Proceed anyway** — continue to Step 2 and generate a custom binder

**If the developer chooses to skip:** Print **"Binder generation skipped — using existing third-party binder."** and stop. Do not execute Steps 2–8.

**If the developer chooses to proceed:** Continue to Step 2.

---

### Step 2: Verify target module exists and is valid

**Blocked by:** Step 1b must complete successfully (or determine that custom generation should proceed).

Before generating any files, verify the target module exists and is correctly set up. This is a mandatory gate.

#### 2a. Directory and POM existence

1. Check that `{BINDER_TARGET_MODULE_DIR}` directory exists.
2. Check that `{BINDER_TARGET_MODULE_DIR}/pom.xml` exists.

If either is missing, print: **"Target module not found at `{BINDER_TARGET_MODULE_DIR}` — run `/init-mi-project` first"** and stop.

#### 2b. POM content validation

Read `{BINDER_TARGET_MODULE_DIR}/pom.xml` and verify:

1. **`<artifactId>`** matches `{BINDER_ARTIFACT_ID}`.
2. **Client SDK dependency is present** — the POM contains a `<dependency>` whose `<groupId>` and `<artifactId>` match those inside `{CLIENT_SDK_DEPENDENCY}`.
3. **No leftover template references** — the POM must not contain `com.solace.samples`, `abc-client`, or `abc-service`. If any are found, print a warning listing each occurrence.

If checks 1–2 fail, print: **"Target module POM validation failed"** with a table of failures and stop.

#### 2c. Source directories

1. If `{BINDER_TARGET_SRC_DIR}` does not exist, create it and all required subdirectories:
   - `{BINDER_TARGET_SRC_DIR}/config/`
   - `{BINDER_TARGET_SRC_DIR}/outbound/` (if `BINDER_TYPE` contains `producer`)
   - `{BINDER_TARGET_SRC_DIR}/inbound/` (if `BINDER_TYPE` contains `consumer`)
   - `{BINDER_TARGET_SRC_DIR}/inbound/acknowledge/` (if `BINDER_TYPE` contains `consumer`)
   - `{BINDER_TARGET_SRC_DIR}/properties/`
   - `{BINDER_TARGET_SRC_DIR}/provisioning/`
   - `{BINDER_TARGET_SRC_DIR}/util/`
2. If `{BINDER_TARGET_RESOURCES_DIR}/META-INF/spring/` does not exist, create it.
3. If `{BINDER_TARGET_TEST_SRC_DIR}/app/` does not exist, create it.
4. If `{BINDER_TARGET_TEST_RESOURCES_DIR}/` does not exist, create it.

---

### Step 3: Plan binder implementation

**Blocked by:** Step 2 must complete successfully.

Read `.claude/skills/add-binder/references/plan-binder-impl.md` and execute all its steps. This produces `{BINDER_TARGET_MODULE_DIR}/CLAUDE.md` — the design document that drives all subsequent code generation.

**This step blocks all subsequent steps.** Do not proceed until `plan-binder-impl.md` has completed all its steps and CLAUDE.md is written with all sections filled.

---

### Step 4: Generate shared infrastructure

**Blocked by:** Step 3 must complete successfully — `{BINDER_TARGET_MODULE_DIR}/CLAUDE.md` must exist.

Read `.claude/skills/add-binder/references/generate-shared.md` and execute all its steps. The file defines what it generates and validates its own output.

**Context available to `generate-shared.md`:** All configuration variables, derived values, and `{BINDER_TARGET_MODULE_DIR}/CLAUDE.md`.

---

### Step 5: Generate common test infrastructure

**Blocked by:** Step 4 must complete successfully — all shared infrastructure must be generated.

Read `.claude/skills/add-binder/references/generate-common-tests.md` and execute all its steps. This generates the shared test files (TestApplication, AbstractBase, application-multibinder.yml, MultiBinderIT) and the profile-specific application YAML files (`application-consumer.yml`, `application-sync-producer.yml`, and/or `application-async-producer.yml` based on `BINDER_TYPE` and `PRODUCER_IS_ASYNC`) that both producer and consumer integration tests depend on.

**Context available to `generate-common-tests.md`:** All configuration variables, derived values, and `{BINDER_TARGET_MODULE_DIR}/CLAUDE.md`.

---

### Step 6: Generate producer capability

**Blocked by:** Step 5 must complete successfully — all common test infrastructure must be generated.

Parse `BINDER_TYPE` into a list of capabilities (split on comma, trim whitespace). Valid values: `producer`, `consumer`.

If `BINDER_TYPE` contains an unrecognized value, print: **"Unknown binder type: `{value}` — expected `producer`, `consumer`, or `producer,consumer`"** and stop.

If `BINDER_TYPE` contains `producer`, read `.claude/skills/add-binder/references/generate-producer.md` and execute all its steps. The file defines what it generates and validates its own output.

**Context available to `generate-producer.md`:** All configuration variables, derived values, and `{BINDER_TARGET_MODULE_DIR}/CLAUDE.md`.

---

### Step 7: Generate consumer capability

**Blocked by:** Step 6 must complete successfully (or be skipped if `BINDER_TYPE` does not contain `producer`).

If `BINDER_TYPE` contains `consumer`, read `.claude/skills/add-binder/references/generate-consumer.md` and execute all its steps. The file defines what it generates (inbound channel adapter, acknowledgment callback, consumer destination record) and validates its own output. It then delegates to `generate-consumer-tests.md` for the consumer integration test class.

**Context available to `generate-consumer.md`:** All configuration variables, derived values, and `{BINDER_TARGET_MODULE_DIR}/CLAUDE.md`.

---

### Step 8: Build verification and summary (MANDATORY — must invoke /verify-binder)

**Blocked by:** Steps 6 and 7 must complete successfully (or be skipped per `BINDER_TYPE`).

**CRITICAL:** This step is the ONLY way to verify the generated binder works end-to-end. No prior Maven command (including any `compile`, `clean verify -DskipTests`, or `/fix-maven-dependencies` invocation) substitutes for this step. Even if a prior step reported `BUILD SUCCESS`, you MUST still invoke `/verify-binder` here — those commands do not run integration tests.

#### 8a. Invoke verify-binder via subagent

Launch a `general-purpose` subagent using the `Task` tool. Do NOT skip this invocation for any reason — a `BUILD SUCCESS` from a prior step does NOT mean integration tests pass.

The subagent runs in its own context window — all Maven output, error analysis, and repair iterations stay within that context. Only the structured summary is returned to this skill.

```
subagent_type: "general-purpose"
prompt: |
  You are running the verify-binder skill for the add-binder pipeline.

  PATH CONSTRAINT: All file operations must stay within exactly two directory trees:
  (1) the workspace root (the directory containing configuration.md), and
  (2) TARGET_PROJECT_FOLDER as read from configuration.md.
  Do NOT access, list, or search any parent directory of either location.
  Assume TARGET_PROJECT_FOLDER already exists — skip parent directory verification.
  When using Glob or Grep, always set the path parameter to one of these two roots.

  Invoke the skill `/verify-binder` using the Skill tool. This skill compiles the binder
  module, runs integration tests, and applies up to 5 targeted repair attempts on failure.
  It has its own repair logic — do NOT interfere with it or attempt your own fixes.

  Wait for the skill to complete fully.

  When the skill finishes, relay its structured summary exactly as printed — this includes:
  - Status (PASSED or FAILED)
  - Attempt count
  - Repair table (if repairs were applied): Attempt, Category, File, Fix
  - On failure: attempt history, current error, files modified, analysis, suggested next steps

  Return the structured summary and nothing else. Do not add commentary or extra text.
```

**If you complete code generation and do not invoke verify-binder via this subagent, the skill is INCOMPLETE and FAILED.**

#### 8b. Process result

Read the summary returned by `verify-binder`.

**If the summary reports PASSED:**

- Print: **"Build and integration tests passed — binder module verified."**
- If repairs were applied, print the repair table from the summary.
- If repairs were applied, proceed to **8b-update**. Otherwise, proceed to 8c.

**If the summary reports FAILED:**

- Print the full diagnostic from the summary (attempt history, current error, analysis, suggested next steps).
- **Stop and wait for developer input.** Do not proceed to 8c until the developer provides guidance and the issue is resolved.
- Once the issue is resolved (manually or via further repair), proceed to **8b-update** before 8c.

#### 8b-update. Synchronize CLAUDE.md with actual working state

**When to execute:** Only when repairs or adjustments were made during build verification — either by `verify-binder` autonomously or by the developer manually. If the build passed with no repairs, skip to 8c.

During build verification, source files may have been modified — imports corrected, method signatures adjusted, property keys renamed, SDK calls fixed, dependencies changed, or configuration restructured. If any of these changes alter facts documented in `{BINDER_TARGET_MODULE_DIR}/CLAUDE.md`, the CLAUDE.md must be updated to match the actual working code. A stale CLAUDE.md will cause downstream skills (especially `add-microintegration`) to generate incorrect code.

**Review each repair** from the verify-binder summary and check whether it affects any CLAUDE.md section:

| Repair type | CLAUDE.md sections to check |
|---|---|
| Import / class name changes | SDK Client, Generated Files |
| Method signature changes (publish, poll, connect) | SDK Client, Testing Notes |
| Connection property key or prefix changes | Configuration (connection properties table, `@ConfigurationProperties` prefix) |
| Extended binding property changes | Configuration (extended binding properties table) |
| Ack mode or callback changes | Key Design Decisions (ack modes) |
| Dependency changes (SDK version, added/removed deps) | Dependencies (if documented) |
| Test configuration changes (YAML keys, profiles) | Testing Notes |
| Provisioning or destination management changes | SDK Client (destination management methods) |

For each affected section, read the current CLAUDE.md content, compare it against the actual repaired source file, and update CLAUDE.md to reflect the working state. Do not remove sections or restructure the document — only update the specific values that changed.

After updating, print: **"CLAUDE.md updated to reflect {N} repair(s) applied during build verification."** List each changed section and what was updated.

#### 8c. Print final summary

Print:
- Binder type: **{BINDER_TYPE}**
- Target module path
- Active capabilities (producer, consumer, or both)
- Files created (with full paths relative to `{TARGET_PROJECT_FOLDER}`)
- Files modified during repair (if any, from verify-binder summary)
- CLAUDE.md adjustments (if any — list each section updated and the reason, or "no adjustments needed" if the build passed without repairs)
- The YAML configuration structure this binder supports (from CLAUDE.md Configuration section)

