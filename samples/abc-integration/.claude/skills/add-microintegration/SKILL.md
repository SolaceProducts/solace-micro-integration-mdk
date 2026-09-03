---
name: add-microintegration
description: Generate Java source files, Spring Cloud Stream configuration, and integration tests for the micro-integration application module. Bridges the Solace event broker and the target technology using either a custom or third-party Spring Cloud Stream binder. Reads configuration from configuration.md; technology details are consolidated into the MI module's CLAUDE.md by the planning step.
tools_required:
  - Read
  - Write
  - Bash
  - Glob
  - Edit
  - Task
  - AskUserQuestion
---

# Add Micro-Integration

## Role

You are an expert Java developer specializing in Spring Cloud Stream binder development and usage of binders in custom Spring Cloud Stream applications. You have deep knowledge of the Solace micro-integration connector framework (`pubsubplus-connector-framework`), Spring Cloud Stream binding configuration, multi-binder setups, and the workflow-based message routing model. You use this expertise to adapt the ABC micro-integration template into a correct, idiomatic connector application that properly integrates the target technology's binder with the Solace binder.

## Overview

A micro-integration is a Spring Cloud Stream application that contains **two binders** — the Solace binder and a target technology binder — and is built on the Solace Micro-Integration Framework (`pubsubplus-connector-framework`). The connector framework provides workflow-based message routing, binding capabilities management, health monitoring, and configuration validation on top of Spring Cloud Stream's multi-binder architecture. The two binders enable bidirectional message flow: Solace→Technology (consumer direction) and Technology→Solace (producer direction).

This skill generates the Java source files, Spring Cloud Stream YAML configuration, and integration tests inside an already-created micro-integration application module. It reads the ABC micro-integration template files as structural blueprints and applies technology-specific transformations using configuration variables and the MI module's CLAUDE.md design document (produced by the planning step).

## Quick Start

```bash
/add-microintegration
```

## Scope

This skill generates the **Java source files, application YAML configuration, and integration tests** inside an already-created micro-integration module. It assumes the module directory and POM already exist (created by `init-mi-project`).

**What this skill does NOT do:**
- Create directory structures or POM files (that is `init-mi-project`)
- Generate the Spring Cloud Stream binder (that is `add-binder`)
- Generate test-support infrastructure (that is `add-test-support`)
- Analyse or fix Maven dependencies (that is `fix-maven-dependencies`)

---

## Step Execution Rules

**Step execution order is mandatory.** Each step must complete successfully before the next step begins. Do not skip ahead, parallelize steps, or begin a step while a previous step is incomplete. If a step fails, stop and report the failure — do not proceed to subsequent steps.

These rules apply to:
- All steps in this file (Steps 1, 2, 3, 4, 5, 6)
- All steps in delegated files (`references/plan-mi-impl.md`, `references/generate-mi.md`)
- Subagent invocations (`verify-microintegration` via Task tool)
- Cross-file transitions — a delegated file must complete all its steps before control returns and the next step in this file begins

---

## Naming Conventions

In all generated Java source (Javadoc, comments) and YAML comments:

- Use **"Solace event broker"** — never "Solace PubSub+" or "PubSub+".
- Use **"Solace Micro-Integration Framework"** — never "connector framework" or "Connector Framework".

---

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
| `TARGET_PROJECT_FOLDER` | Root of the generated project |
| `INTEGRATION_APP_ARTIFACT_ID` | Name of the micro-integration module directory |
| `TECH_NAME_LOWER` | Lowercase technology name — binder type, config prefix, package segments |
| `TECH_NAME_UPPER` | Uppercase technology name — comments, descriptions, log prefixes |
| `TECH_NAME_CLASS_NAME_USE` | Technology name in Java class name prefixes (e.g., `Sqs`) |
| `PROJECT_ROOT_GROUP_ID` | Maven groupId |
| `BINDER_PACKAGE` | Java package of the binder module (used in logging config references) |
| `BINDER_ARTIFACT_ID` | Binder module directory name (used to locate binder CLAUDE.md and spring.binders) |
| `MI_PACKAGE` | Java package for micro-integration source files |
| `MI_CONTAINER_IMAGE_NAME` | OCI container image name |
| `TEST_SUPPORT_ARTIFACT_ID` | Test-support module directory name (used to locate test-support CLAUDE.md) |
| `CLIENT_SDK_DEPENDENCY` | Client SDK Maven dependency (used in test setup patterns) |
| `HOST_OS` | Host operating system — determines path separators and shell commands |
| `JDK_PATH` | Path to JDK installation for build verification |
| `BINDER_SKIP_GENERATION` | Boolean (`true`/`false`) — determines custom vs third-party binder mode |

#### Determine binder mode

After validation, determine the binder mode using `BINDER_SKIP_GENERATION`:

- **Custom binder mode**: `BINDER_SKIP_GENERATION` is `false`. A custom binder was generated by `add-binder`. The micro-integration uses the custom binder from the project's binder module.
- **Third-party binder mode**: `BINDER_SKIP_GENERATION` is `true`. No custom binder was generated. The micro-integration uses an existing third-party binder.

Store the binder mode for use by the planning step. Do **not** resolve the binder type name here — binder name resolution is performed by the planning step (`references/plan-mi-impl.md`) which reads `spring.binders` directly.

#### Derive paths (only after validation passes)

| Derived | Formula | Purpose |
|---|---|---|
| `MI_PACKAGE_PATH` | `MI_PACKAGE` with dots replaced by `/` | Source directory path |
| `MI_TARGET_MODULE_DIR` | `{TARGET_PROJECT_FOLDER}/{INTEGRATION_APP_ARTIFACT_ID}` | Module root |
| `MI_TARGET_SRC_DIR` | `{MI_TARGET_MODULE_DIR}/src/main/java/{MI_PACKAGE_PATH}` | Main source directory |
| `MI_TARGET_RESOURCES_DIR` | `{MI_TARGET_MODULE_DIR}/src/main/resources` | Main resources directory |
| `MI_TARGET_TEST_SRC_DIR` | `{MI_TARGET_MODULE_DIR}/src/test/java/{MI_PACKAGE_PATH}` | Test source directory |
| `MI_TARGET_TEST_RESOURCES_DIR` | `{MI_TARGET_MODULE_DIR}/src/test/resources` | Test resources directory |
| `BINDER_CLAUDE_MD` | `{TARGET_PROJECT_FOLDER}/{BINDER_ARTIFACT_ID}/CLAUDE.md` | Binder design document — exists in both modes: created by `add-binder` (custom) or `analyze-3party-binder` (third-party) |
| `TEST_SUPPORT_CLAUDE_MD` | `{TARGET_PROJECT_FOLDER}/{TEST_SUPPORT_ARTIFACT_ID}/CLAUDE.md` | Test-support design document |
| `MI_CLAUDE_MD` | `{MI_TARGET_MODULE_DIR}/CLAUDE.md` | MI design document (produced by the planning step) |

---

### Step 2: Verify target module exists and is valid

**Blocked by:** Step 1 must complete successfully.

#### 2a. Directory and POM existence

1. Check that `{MI_TARGET_MODULE_DIR}` directory exists.
2. Check that `{MI_TARGET_MODULE_DIR}/pom.xml` exists.

If either is missing, print: **"Target module not found at `{MI_TARGET_MODULE_DIR}` — run `/init-mi-project` first"** and stop.

#### 2b. POM content validation

Read `{MI_TARGET_MODULE_DIR}/pom.xml` and verify:

1. **`<artifactId>`** matches `{INTEGRATION_APP_ARTIFACT_ID}`.
2. **No leftover template references** — the POM must not contain `com.solace.samples`. If found, print a warning listing each occurrence.

#### 2c. Source directories

If `{MI_TARGET_SRC_DIR}` does not exist, create it and all parent directories.
If `{MI_TARGET_RESOURCES_DIR}` does not exist, create it.
If `{MI_TARGET_TEST_SRC_DIR}` does not exist, create it.
If `{MI_TARGET_TEST_RESOURCES_DIR}` does not exist, create it.

---

### Step 3: Plan micro-integration implementation

**Blocked by:** Step 2 must complete successfully.

Read `.claude/skills/add-microintegration/references/plan-mi-impl.md` and execute all its sections (Prerequisites, then Sections 1–9). This produces `{MI_CLAUDE_MD}` — the design document that drives all subsequent code generation. The planning sections cover:

1. **Resolve binder identity & verify upstream docs** (Prerequisites) — resolves `RESOLVED_BINDER_NAME` from `spring.binders`, verifies `{BINDER_CLAUDE_MD}` and `{TEST_SUPPORT_CLAUDE_MD}` exist
2. **Analyze binder capabilities, connection config, extended properties, payloads** (Sections 1–4) — extracts capability modes, ack modes, connection properties, extended binding properties, payload requirements, and interceptor decisions from `{BINDER_CLAUDE_MD}`
3. **Plan application wiring and test infrastructure** (Sections 5–8) — documents naming conventions, bean registration, configuration validators, test-support architecture, test profiles, test code patterns, and cross-references binder connection properties with test-support getters
4. **Write CLAUDE.md** (Section 9) — consolidates all findings into `{MI_CLAUDE_MD}` with source transformation guide, test configuration plan, and test Java plan

**This step blocks all subsequent steps.** Do not proceed until `plan-mi-impl.md` has completed all its steps and `{MI_CLAUDE_MD}` is written with all sections filled.

---

### Step 4: Generate micro-integration code

**Blocked by:** Step 3 must complete successfully — `{MI_CLAUDE_MD}` must exist.

Read `.claude/skills/add-microintegration/references/generate-mi.md` and execute all its steps. The generation step uses `{MI_CLAUDE_MD}` as the sole authority for transformation rules and technology-specific values, and `configuration.md` for structural variables (paths, artifact IDs). It:

1. **Generates main source files** — capabilities factories (conditional on verified capabilities), main application class with conditional beans, package-info
2. **Generates application.yml** — boilerplate bindings with technology-specific logging packages
3. **Generates test source files** — HealthAssertions with resolved health check paths, BasicMessagingIT with technology-specific imports, connection properties, SDK calls
4. **Generates test resource files** — both test profiles with resolved binder names and extended properties

**This step blocks Step 5.** Do not proceed until `generate-mi.md` has completed all its steps (Steps 1 through 4).

---

### Step 5: Build verification (MANDATORY — must invoke /verify-microintegration)

**Blocked by:** Step 4 must complete successfully.

**CRITICAL:** This step is the ONLY way to verify the generated code works end-to-end. No prior Maven command (including any `compile`, `clean verify -DskipTests`, or `/fix-maven-dependencies` invocation) substitutes for this step. Even if Step 4 or a dependency fix reported `BUILD SUCCESS`, you MUST still invoke `/verify-microintegration` here — those commands do not run integration tests.

#### 5a. Invoke verify-microintegration via subagent

Launch a `general-purpose` subagent using the `Task` tool. Do NOT skip this invocation for any reason — a `BUILD SUCCESS` from a prior step does NOT mean integration tests pass.

The subagent runs in its own context window — all Maven output, error analysis, and repair iterations stay within that context. Only the structured summary is returned to this skill.

```
subagent_type: "general-purpose"
prompt: |
  You are running the verify-microintegration skill for the add-microintegration pipeline.

  PATH CONSTRAINT: All file operations must stay within exactly two directory trees:
  (1) the workspace root (the directory containing configuration.md), and
  (2) TARGET_PROJECT_FOLDER as read from configuration.md.
  Do NOT access, list, or search any parent directory of either location.
  Assume TARGET_PROJECT_FOLDER already exists — skip parent directory verification.
  When using Glob or Grep, always set the path parameter to one of these two roots.

  Invoke the skill `/verify-microintegration` using the Skill tool. This skill compiles the
  micro-integration module with `-am` (also-make dependencies — binder and test-support modules),
  runs integration tests via Failsafe, and applies up to 5 targeted repair attempts on failure.
  It has its own repair logic — do NOT interfere with it or attempt your own fixes.

  Wait for the skill to complete fully.

  When the skill finishes, relay its structured summary exactly as printed — this includes:
  - Status (PASSED or FAILED)
  - Attempt count
  - Repair table (if repairs were applied): Attempt, Category, File, Fix
  - On failure: attempt history, current error, files modified, analysis, suggested next steps

  Return the structured summary and nothing else. Do not add commentary or extra text.
```

**If you complete code generation and do not invoke verify-microintegration via this subagent, the skill is INCOMPLETE and FAILED.**

#### 5b. Process verification result

- **On PASSED**: Print confirmation. If repairs were applied (M > 0), print the repair table from the summary. Proceed to Step 6.
- **On FAILED** (self-repair exhausted): Print the diagnostic from the summary (attempt history, current error, analysis, suggested next steps). **Stop** — do not proceed to Step 6. The developer decides what to do next.
- **On INFRASTRUCTURE_FAILURE**: Print the infrastructure failure diagnostic. **Stop** — the developer must resolve the Docker/Testcontainers issue before retrying.

#### 5b-update. Synchronize MI CLAUDE.md after repairs

**Only if `/verify-microintegration` returned PASSED with repairs applied (M > 0).**

Re-read `{MI_CLAUDE_MD}` and compare with the repaired source files. For each repair category, check whether the corresponding CLAUDE.md section still accurately describes the generated code. Update any section that has drifted.

| Repair type | MI CLAUDE.md sections to check |
|---|---|
| Import / class name changes | Source Transformation Guide (General Rules, file-specific), Generated Files |
| Bean registration or factory wiring | Source Transformation Guide (MicroIntegrationApplication, Capabilities Factories) |
| Connection property key / prefix changes | Connection Properties Mapping, Test Java Plan (BaseTest section) |
| Extended binding property changes | Extended Binding Properties, Test Configuration Plan |
| Ack mode or callback changes | Ack Modes |
| Dependency changes | (flag if significant) |
| Test configuration changes (YAML) | Test Configuration Plan |
| Test code changes (lifecycle, SDK calls) | Test Java Plan |
| Application YAML changes (bindings, logging) | Source Transformation Guide (application.yml / application-operator.yml) |
| Auto-configuration exclusion changes | Source Transformation Guide (MicroIntegrationApplication section) |

---

### Step 6: Print summary

**Blocked by:** Step 5 must complete successfully.

Print:
- **Binder mode**: custom or third-party (with binder type name from `{MI_CLAUDE_MD}`)
- **Verified capabilities**: producer, consumer, or both (as determined from binder code inspection)
- **Target module path**
- **Files created** (with paths relative to `{TARGET_PROJECT_FOLDER}`)
- **Connection property prefix** for the target technology
- **Test profiles**: `messaging-consumer`, `messaging-consumer-with-transforms`, `messaging-producer`, `messaging-producer-with-transforms`, `configurationValidation`
- **Build verification result**: PASSED (with N repairs) or FAILED
- **Repairs applied during verification** (repair table if any)
- **MI CLAUDE.md adjustments** (sections updated, or "no adjustments needed")
