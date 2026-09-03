---
name: verify-binder
description: Compile and run integration tests for a generated binder module. On failure, classify the error, attempt targeted repairs to generated source code or dependencies, and retry. Retries up to 5 times before escalating to the developer. Returns a structured summary to the caller.
tools_required:
  - Read
  - Write
  - Bash
  - Glob
  - Edit
  - WebFetch
---

# Verify Binder

## Role

You are an expert Java developer and build engineer. You can diagnose compilation errors, Spring context failures, test assertion failures, and Maven dependency issues in a Spring Cloud Stream binder module and apply targeted repairs. You have deep knowledge of Maven build lifecycles, Spring Boot auto-configuration, Testcontainers, and the Spring Cloud Stream binder SPI.

## Overview

Compile and run integration tests for a generated binder module. If any phase fails, classify the error, attempt a targeted repair, and retry. The build-test-repair loop runs up to 5 attempts. On success, print a structured summary. On exhaustion, print a diagnostic and ask the developer for guidance.

This skill is designed to run in its own context so that verbose Maven output and stack traces do not overflow the calling skill's context window. Only the structured summary at the end is relevant to the caller.

## Quick Start

```bash
/verify-binder
```

---

## Step Execution Rules

**Step execution order is mandatory.** Each step must complete successfully before the next step begins. Do not skip ahead, parallelize steps, or begin a step while a previous step is incomplete. If a step fails, stop and report the failure — do not proceed to subsequent steps.

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

- Do **NOT** run any Maven commands.
- Print the heading: **"Configuration incomplete — cannot proceed"**
- Print a table listing every missing/unset variable with its section number and the Description from its row in the configuration table.
- Stop.

#### Required variables for this skill

After parsing, confirm these variables are available:

| Variable | Used for |
|---|---|
| `TARGET_PROJECT_FOLDER` | Root of the generated project |
| `BINDER_ARTIFACT_ID` | Maven module name for the binder |
| `TECH_NAME_LOWER` | Lowercase technology name, used in package paths and config prefixes |
| `TECH_NAME_CLASS_NAME_USE` | Technology name as it appears in Java class name prefixes |
| `BINDER_PACKAGE` | Java package for all binder source files |
| `TECH_OVERVIEW_PATH` | Relative path to the technology overview report |
| `HOST_OS` | Host operating system — determines shell commands and path separators |
| `JDK_PATH` | Path to JDK installation; set as `JAVA_HOME` for Maven execution |

#### Derive values (only after validation passes)

| Derived | Formula | Purpose |
|---|---|---|
| `BINDER_PACKAGE_PATH` | `BINDER_PACKAGE` with dots replaced by `/` | |
| `BINDER_TARGET_MODULE_DIR` | `{TARGET_PROJECT_FOLDER}/{BINDER_ARTIFACT_ID}` | |
| `BINDER_TARGET_SRC_DIR` | `{BINDER_TARGET_MODULE_DIR}/src/main/java/{BINDER_PACKAGE_PATH}` | |
| `BINDER_TARGET_TEST_SRC_DIR` | `{BINDER_TARGET_MODULE_DIR}/src/test/java/{BINDER_PACKAGE_PATH}` | |

---

### Step 2: Verify prerequisites

**Blocked by:** Step 1 must complete successfully.

Before running any build, verify:

1. `{BINDER_TARGET_MODULE_DIR}` directory exists.
2. `{BINDER_TARGET_MODULE_DIR}/pom.xml` exists.
3. `{BINDER_TARGET_MODULE_DIR}/CLAUDE.md` exists — this is the design document needed for repair context.
4. Maven wrapper exists: `{TARGET_PROJECT_FOLDER}/mvnw.cmd` (if `HOST_OS` is `windows`) or `{TARGET_PROJECT_FOLDER}/mvnw` (if `linux`).
5. `{TARGET_PROJECT_FOLDER}/maven/settings.xml` exists.

If any check fails, print: **"Prerequisite check failed"** with the missing item and stop.

---

### Step 3: Build-test-repair loop

**Blocked by:** Step 2 must complete successfully.

This is the core of the skill. It runs a loop of up to **5 attempts**. Each attempt progresses through three phases. The loop exits on the first successful completion of Phase 2, or after 5 failed attempts.

Maintain a repair log — a list of records, one per repair attempted:

| Field | Description |
|---|---|
| `attempt` | Attempt number (1–5) |
| `phase` | Which phase failed: `compile` or `verify` |
| `category` | Error category from the classification table |
| `file` | Path of the file that was fixed (or `N/A` if no fix was applied) |
| `fix` | One-line description of the fix |
| `outcome` | `fixed` if the repair was applied, `no-fix` if the error could not be addressed |

#### Phase 1 — Compile

Run `mvn test-compile` scoped to the binder module. Use a 5-minute timeout (300 000 ms).

**If `HOST_OS` is `windows`:**
```
cmd.exe //c "set JAVA_HOME={JDK_PATH}&& cd /d {TARGET_PROJECT_FOLDER} && .\mvnw.cmd test-compile -pl {BINDER_ARTIFACT_ID} -s maven\settings.xml" 2>&1
```

**If `HOST_OS` is `linux`:**
```
JAVA_HOME="{JDK_PATH}" {TARGET_PROJECT_FOLDER}/mvnw test-compile -pl {BINDER_ARTIFACT_ID} -s maven/settings.xml 2>&1
```

- **On success** → proceed to Phase 2.
- **On failure** → proceed to Phase 3 (Repair) with the compile output.

#### Phase 2 — Integration tests

Run `mvn verify` scoped to the binder module. Use a 10-minute timeout (600 000 ms) — Testcontainers may pull Docker images on first run.

**If `HOST_OS` is `windows`:**
```
cmd.exe //c "set JAVA_HOME={JDK_PATH}&& cd /d {TARGET_PROJECT_FOLDER} && .\mvnw.cmd verify -pl {BINDER_ARTIFACT_ID} -s maven\settings.xml" 2>&1
```

**If `HOST_OS` is `linux`:**
```
JAVA_HOME="{JDK_PATH}" {TARGET_PROJECT_FOLDER}/mvnw verify -pl {BINDER_ARTIFACT_ID} -s maven/settings.xml 2>&1
```

- **On success** → exit the loop, proceed to Step 4.
- **On failure** → proceed to Phase 3 (Repair) with the verify output.

#### Phase 3 — Repair

Classify the failure into one of the categories below using the detection patterns. Then apply the corresponding fix strategy.

| Category | Detection patterns | Fix strategy |
|---|---|---|
| **Dependency issue** | `Could not find artifact`, `Could not resolve dependencies`, `version conflict`, `Require upper bound dependencies error`, enforcer violations | Invoke `/fix-maven-dependencies`. After it completes, continue to the next attempt. |
| **Compilation error** | `COMPILATION ERROR`, `cannot find symbol`, `package does not exist`, `incompatible types`, `method does not override` | Read the compiler error to identify the source file and line number. Read the source file. Read `{BINDER_TARGET_MODULE_DIR}/CLAUDE.md` SDK Client section for the correct API. If CLAUDE.md is insufficient, read the technology overview report at `{TECH_OVERVIEW_PATH}`. Identify the corresponding ABC template file under `spring-cloud-stream-binder-abc/` for the structural pattern. Fix the source file. |
| **Spring context failure** | `BeanCreationException`, `NoSuchBeanDefinitionException`, `UnsatisfiedDependencyException`, `ApplicationContextException`, `Failed to load ApplicationContext` | Read the stack trace to identify the failing bean and class. Read the auto-configuration classes under `{BINDER_TARGET_SRC_DIR}/config/`, property classes under `{BINDER_TARGET_SRC_DIR}/properties/`, and META-INF registration files under `{BINDER_TARGET_MODULE_DIR}/src/main/resources/META-INF/`. Cross-reference with CLAUDE.md Configuration section and the ABC template's equivalent config/properties/META-INF files. Fix the wiring issue. |
| **Test assertion failure** | `AssertionError`, `AssertionFailedError`, `expected:`, `but was:`, `Expecting`, test method name in stack trace | Read the failing test class source and the test output. Determine whether the problem is in the test expectations (wrong SDK accessor, wrong destination name, wrong assertion) or in the binder implementation (wrong publish logic, wrong header filtering, wrong message conversion). Fix the appropriate source file. |
| **Test infrastructure failure** | `ContainerLaunchException`, `connection refused`, `TimeoutException` during container startup, Docker daemon errors | This is an **environment problem**, not a code problem. Do **NOT** count this attempt against the 5-attempt limit. Print: **"Test infrastructure failure (Docker/Testcontainers) — retrying..."** and retry Phase 2 only. After **2 consecutive** infrastructure failures, stop and print: **"Persistent test infrastructure failure — check Docker daemon and network."** then proceed to Step 4 with a FAILED status. |
| **Unknown / unclassifiable** | None of the above patterns match | Print the full error output. Increment the attempt counter. On the next attempt, perform a broader analysis: re-read `{BINDER_TARGET_MODULE_DIR}/CLAUDE.md`, the overview report at `{TECH_OVERVIEW_PATH}`, and the ABC template equivalent file to look for discrepancies with the generated code. |

#### Repair rules

These rules apply to every repair in every category:

1. **Targeted fixes only** — fix the specific error identified. Do not rewrite unrelated code, refactor surrounding methods, or "improve" code that is not broken.
2. **Re-read before editing** — before modifying any file, re-read it to get the current state. Prior repairs may have changed it.
3. **Verify alignment** — after each repair, re-read CLAUDE.md and the ABC template equivalent to confirm the fix is consistent with both.
4. **Log the repair** — after each repair, print: `Attempt {N}: [{category}] {file} — {one-line fix description}`
5. **One fix per attempt** — apply one targeted fix per attempt, then re-run the build to check if it resolved the issue. Do not stack multiple speculative fixes in a single attempt.

---

### Step 4: Print summary

**Blocked by:** Step 3 must complete (either by success or by exhausting all attempts).

Print a structured summary. This output is what the calling skill (or the developer) sees.

#### On success (loop exited via Phase 2 success)

```
## Binder Verification: PASSED

- **Result:** All compilation and integration tests passed
- **Attempts:** {N} of 5
- **Repairs applied:** {M}
```

If repairs were applied (M > 0), also print:

```
### Repairs applied

| Attempt | Category | File | Fix |
|---|---|---|---|
```

One row per entry in the repair log.

#### On exhaustion (5 attempts failed without success)

```
## Binder Verification: FAILED — developer guidance needed

- **Result:** Self-repair exhausted after 5 attempts
- **Last failure phase:** {compile | verify}
- **Last error category:** {category}

### Attempt history

| Attempt | Phase | Category | File | Fix | Outcome |
|---|---|---|---|---|---|
```

One row per attempt from the repair log.

```
### Current error

{Key lines from the last failed build output — compiler errors, stack trace summary, or assertion message. Truncate verbose output to the essential diagnostic lines.}

### Files modified during repair

| File | Edits |
|---|---|
```

One row per unique file modified, with the count of edits applied to it.

```
### Analysis

{A concise description of the root cause as understood after 5 attempts: what the generated code is trying to do, what the SDK or framework expects, and where the mismatch lies.}

### Suggested next steps

1. {First concrete action the developer could take}
2. {Second concrete action}
3. {Third concrete action, if applicable}
```

Then **stop**. The calling skill or developer decides what to do next.

#### On infrastructure failure (2 consecutive Docker/Testcontainers failures)

```
## Binder Verification: FAILED — test infrastructure unavailable

- **Result:** Docker/Testcontainers failed 2 consecutive times
- **Compilation:** {passed | not reached}
- **Error:** {container launch error summary}

### Suggested next steps

1. Verify Docker daemon is running: `docker info`
2. Check available disk space and memory
3. Re-run `/verify-binder` after resolving the infrastructure issue
```
