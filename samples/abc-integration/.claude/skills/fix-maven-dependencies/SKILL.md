---
name: fix-maven-dependencies
description: Run Maven dependency analysis against a generated micro-integration project, detect dependency issues (conflicts, convergence failures, missing artifacts, scope problems), autonomously select and apply the best fix for each problem. Only asks the user for input when no viable solution exists. Reads configuration from configuration.md.
tools: Bash, Read, Write, Edit
user_invocable: false
---

# Fix Maven Dependencies

Analyse and fix Maven dependency problems in a generated micro-integration project.
Uses the project's own Maven wrapper (`mvnw` / `mvnw.cmd`) and its bundled `maven/settings.xml`.

## Critical Constraint — No Downgrades of Platform Artifacts

**`micro-integration-build-parent`, `micro-integration-platform-bom`, and every dependency they bring in (direct or transitive) must NEVER be downgraded.** These artifacts define the platform baseline. Any proposed fix that would lower the version of the build-parent, the platform BOM, or any artifact managed/provided by them is **forbidden**.

When a conflict involves a platform-managed dependency, the fix must adjust the **project-side** dependency instead — for example by upgrading the project's own dependency, adding an `<exclusion>`, removing a redundant explicit `<version>`, or aligning the project code to the version the platform provides.

**When no project-side fix exists:** If after exhausting all project-side strategies (exclusions, upgrades, dependencyManagement overrides, scope changes) the only remaining resolution requires downgrading a dependency managed by `micro-integration-build-parent` or `micro-integration-platform-bom`, do **NOT** apply the downgrade automatically. Instead:

1. **Stop and present the situation to the user.** Clearly state which platform-managed artifact would need to be downgraded, from what version to what version, and why no project-side fix is viable.
2. **Explain the root cause.** Identify the specific project-side dependency (client SDK, third-party library, etc.) whose transitive tree conflicts with the platform-managed version, and why upgrading it or excluding its transitives does not resolve the problem.
3. **Describe the risk.** Explain what could break if the platform-managed version were downgraded (e.g., other platform components compiled against the higher version, security patches lost, API incompatibilities with other platform-managed libraries).
4. **Propose alternatives for the user to choose from**, such as:
   - Downgrading the project-side client SDK to a version compatible with the platform's transitive versions.
   - Requesting an updated platform BOM from the platform team that accommodates the newer transitive version.
   - Accepting the downgrade with an explicit `<dependencyManagement>` override in the integration-app POM, understanding the risks.
5. **Wait for the user's decision** before making any changes.

## Client SDK Version Alignment

The external client SDK (identified by `CLIENT_SDK_DEPENDENCY`) must use a **single consistent version** across all project modules. Platform parents (`micro-integration-build-parent`, `pubsubplus-connector-component-build-parent`) may include `<dependencyManagement>` that pins the client SDK to a different version than the one declared in the project. When this happens:

1. **Test-support and binder must declare the same client SDK version.** If a version mismatch is detected between these two modules, align them to the version specified in `CLIENT_SDK_DEPENDENCY`.

2. **Integration-app must enforce the binder's client SDK version.** The binder is compiled against a specific client SDK version. When the integration-app's parent manages that SDK to a different version, the binder's compiled code and the runtime SDK become mismatched. To fix this, add a `<dependencyManagement>` entry in the integration-app POM that pins the client SDK to the same version the binder declares. This ensures the binder gets the SDK it was compiled against at runtime.

## Quick Start

```bash
/fix-maven-dependencies
```

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
- Do **NOT** attempt to fix any files.
- Print the heading: **"Configuration incomplete — cannot proceed"**
- Print a table listing every missing/unset variable with its section number and the Description from its row in the configuration table.
- Stop.

#### Required variables for this skill

After parsing, confirm these variables are available (they should already pass the generic validation above):

| Variable | Used for |
|---|---|
| `TARGET_PROJECT_FOLDER` | Root of the generated project to analyse |
| `TEST_SUPPORT_ARTIFACT_ID` | Identifying the test-support module |
| `BINDER_ARTIFACT_ID` | Identifying the binder module |
| `INTEGRATION_APP_ARTIFACT_ID` | Identifying the integration-app module |
| `PROJECT_ROOT_GROUP_ID` | The project's Maven groupId |
| `PROJECT_VERSION` | Expected project version |
| `CLIENT_SDK_DEPENDENCY` | The external client SDK `<dependency>` element |
| `MICRO_INTEGRATION_PLATFORM_BOM_VERSION` | Expected BOM version |
| `MICRO_INTEGRATION_BUILD_PARENT_VERSION` | Expected build-parent version |
| `JDK_PATH` | Path to JDK installation; set as `JAVA_HOME` for Maven execution |
| `HOST_OS` | Host operating system — determines path separators, shell commands, and script extensions |

### Step 2: Verify target project exists

Before running any Maven command, verify the target project is present:

1. Check that `{TARGET_PROJECT_FOLDER}` directory exists.
2. Check that `{TARGET_PROJECT_FOLDER}/pom.xml` exists.
3. Check that the Maven wrapper exists: `mvnw.cmd` (if `HOST_OS` is `windows`) or `mvnw` (if `linux`).
4. Check that `{TARGET_PROJECT_FOLDER}/maven/settings.xml` exists.

If any check fails, print a clear error message identifying the missing item and stop.

### Step 3: Run Maven dependency analysis

#### 3a. Select Maven wrapper using `HOST_OS`

| `HOST_OS` | Maven wrapper | Shell prefix | Path separator | Settings path |
|---|---|---|---|---|
| `windows` | `mvnw.cmd` | `cmd.exe //c` | `\` | `maven\settings.xml` |
| `linux` (includes macOS) | `./mvnw` (ensure executable: `chmod +x mvnw` if needed) | `bash -c` | `/` | `maven/settings.xml` |

#### 3b. Run `clean verify` (primary build check)

The primary check runs the full Maven build lifecycle. This catches enforcer-plugin violations (`requireUpperBoundDeps`, `requireReleaseDeps`), compilation errors, test failures, and any other lifecycle-bound checks that `dependency:tree` alone cannot detect.

Execute the following Maven command from `{TARGET_PROJECT_FOLDER}`, setting `JAVA_HOME` from `{JDK_PATH}`. Use the shell prefix, wrapper, and settings path from the `HOST_OS` table in Step 3a.

**`windows`:**
```
cmd.exe //c "set JAVA_HOME={JDK_PATH}&& cd /d {TARGET_PROJECT_FOLDER} && .\mvnw.cmd clean verify -DskipTests -s maven\settings.xml" 2>&1
```

**`linux`:**
```
JAVA_HOME="{JDK_PATH}" {TARGET_PROJECT_FOLDER}/mvnw clean verify -DskipTests -s maven/settings.xml 2>&1
```

> `-DskipTests` skips test execution but still compiles test sources and runs enforcer / other build plugins. This keeps the check fast while still catching dependency and plugin problems.

**Important execution notes:**
- Always set `JAVA_HOME` to `{JDK_PATH}` before invoking the Maven wrapper. Do **not** rely on the system environment having `JAVA_HOME` pre-configured.
- Use `2>&1` to capture both stdout and stderr.
- Set a generous timeout (5 minutes / 300000 ms) — first invocation may download the Maven distribution and dependencies.

Capture the **full output** for analysis.

#### 3c. Run `dependency:tree -Dverbose` (diagnostic tree)

Regardless of whether `clean verify` succeeded or failed, also run the verbose dependency tree. Use the `HOST_OS` conventions from Step 3a.

**`windows`:**
```
cmd.exe //c "set JAVA_HOME={JDK_PATH}&& cd /d {TARGET_PROJECT_FOLDER} && .\mvnw.cmd dependency:tree -Dverbose -s maven\settings.xml" 2>&1
```

**`linux`:**
```
JAVA_HOME="{JDK_PATH}" {TARGET_PROJECT_FOLDER}/mvnw dependency:tree -Dverbose -s maven/settings.xml 2>&1
```

Capture the **full output** for analysis.

#### 3d. Record exit codes

Record the exit code of **both** commands:

- **`clean verify` exit code 0**: Build lifecycle passed. No enforcer or compilation errors.
- **`clean verify` non-zero exit code**: Build failed. Examine the output for enforcer violations, compilation errors, or plugin failures — these are primary problems to fix.
- **`dependency:tree` exit code 0**: Tree generated successfully. Proceed to tree analysis (Step 4).
- **`dependency:tree` non-zero exit code**: Tree generation failed. Proceed to build-failure analysis (Step 4), focusing on the error output.

### Step 4: Analyse Maven output

Examine the captured output from **both** commands (`clean verify` and `dependency:tree -Dverbose`) and classify every problem found into one of the categories below. For **each** problem, record:

- **Category** (from the list below)
- **Module** where the problem occurs (root / test-support / binder / integration-app)
- **Artifact** (groupId:artifactId)
- **Details** (versions involved, conflict description, error message)

#### Problem categories

| # | Category | What to look for in output |
|---|---|---|
| 1 | **Missing artifact** | `Could not find artifact`, `Could not resolve dependencies`, `Non-resolvable parent POM` |
| 2 | **Version conflict** | `omitted for conflict with X.Y.Z`, multiple versions of the same GA in the tree |
| 3 | **Convergence failure** | `Require upper bound dependencies error`, `RequireUpperBoundDeps`, `Rule N: …` in enforcer output |
| 4 | **Duplicate dependency** | `omitted for duplicate` |
| 5 | **Scope mismatch** | A dependency needed at `compile` scope appearing only as `test` or `provided` |
| 6 | **Managed version override** | `(managed from X.Y.Z)` where the managed version is unexpected or stale |
| 7 | **SNAPSHOT in release path** | SNAPSHOT versions referenced where a release is expected |
| 8 | **POM parse error** | `Non-parseable POM`, `Malformed POM`, XML syntax errors |
| 9 | **Plugin resolution failure** | `Could not find artifact` for a `maven-plugin` |
| 10 | **Circular dependency** | `omitted for cycle` |
| 11 | **Enforcer violation** | `maven-enforcer-plugin`, `Rule violated`, `requireReleaseDeps`, `bannedDependencies` — only visible in `clean verify` output |
| 12 | **Compilation error** | `COMPILATION ERROR`, `cannot find symbol`, `package … does not exist` — only visible in `clean verify` output |

#### Additional validation checks

After (or instead of, if Maven failed) the tree analysis, also validate:

1. **BOM version consistency** — Read each module POM under `{TARGET_PROJECT_FOLDER}` and verify that every reference to `micro-integration-platform-bom` uses version `{MICRO_INTEGRATION_PLATFORM_BOM_VERSION}`.
2. **Build-parent version** — In `{INTEGRATION_APP_ARTIFACT_ID}/pom.xml`, verify the `<parent>` version for `micro-integration-build-parent` equals `{MICRO_INTEGRATION_BUILD_PARENT_VERSION}`.
3. **Cross-module version alignment** — Verify that cross-module `<dependency>` references (binder depending on test-support, integration-app depending on binder and test-support) use `{PROJECT_VERSION}` or the correct Maven property.
4. **Client SDK presence** — Verify the client SDK dependency from `{CLIENT_SDK_DEPENDENCY}` is present in the test-support and binder POMs.
5. **No leftover template artifacts** — Verify no POM still references `com.solace.samples`, `abc-client`, `abc-service`, `abc-test-support`, `spring-cloud-stream-binder-abc`, or `abc-micro-integration`.

### Step 5: Print diagnosis report

Print a structured report with the heading: **"Maven Dependency Analysis Report"**

The report must contain these sections:

#### 5a. Commands executed

Print the exact Maven commands that were run and their exit codes (both `clean verify` and `dependency:tree -Dverbose`).

#### 5b. Problems found

Print a markdown table:

| # | Category | Module | Artifact | Details |
|---|---|---|---|---|

If no problems were found, print: **"No dependency problems detected."** and skip to Step 5d.

#### 5c. Proposed fix (autonomous selection)

For **each** problem, select **one** fix — the single most native and reliable solution. **Do not ask the user to choose between multiple candidate fixes.** Evaluate all viable options against the priority order below, pick the highest-priority option that resolves the problem without violating constraints, and commit to it.

> **Reminder:** Downgrading `micro-integration-build-parent`, `micro-integration-platform-bom`, or any dependency they manage/provide is **never acceptable**. All fixes must keep the platform baseline intact and adjust the project side instead.

**Fix priority order — always select the highest-ranked viable option:**

1. **Use BOM/parent version management** — if a version conflict can be resolved by removing an explicit `<version>` and letting the BOM manage it, prefer that.
2. **Add `<exclusion>`** — if a transitive dependency from a project-side library conflicts with a platform-managed version, exclude the conflicting transitive at the point of import.
3. **Upgrade the project-side dependency** — if a project dependency brings in an older transitive that clashes with the platform version, upgrade the project dependency (or add a `<dependencyManagement>` entry) to align with the platform.
4. **Add `<dependencyManagement>` override (project-side only)** — pin a transitive dependency version in the project's `<dependencyManagement>` to match what the platform provides.
5. **Fix scope** — if a dependency has the wrong scope, correct it.
6. **Add missing dependency** — if an artifact is simply missing, add it.
7. **Fix version string** — if a version is wrong (typo, stale, mismatched), correct it to match or exceed the platform-managed version.

**When NO viable fix exists** (all options either violate the platform-downgrade constraint or would introduce new breaking problems), mark the problem as **"Unresolvable — user input required"** and follow the escalation procedure in the "Critical Constraint" section above. Do **not** skip or silently ignore such problems.

For each fix, show:
- The **file** to edit (relative to `{TARGET_PROJECT_FOLDER}`)
- The **exact XML change** (before → after)
- The **reason** this option was selected over alternatives (one sentence)

#### 5d. Summary

Print a one-line summary: `{N} problem(s) found, {M} fix(es) proposed.`

### Step 6: Apply fixes (autonomous)

#### 6a. Auto-apply all resolvable fixes

**Immediately apply every fix that was selected in Step 5c** using the Edit tool against the POM files under `{TARGET_PROJECT_FOLDER}`. Do **not** ask the user for confirmation — the skill has already chosen the best option for each problem using the priority order.

#### 6b. Escalate unresolvable problems

If any problems were marked **"Unresolvable — user input required"** in Step 5c, present them to the user **after** applying all resolvable fixes. For each unresolvable problem:

1. Clearly state which artifact is affected, what the conflict is, and why none of the seven fix strategies can resolve it without violating the platform-downgrade constraint.
2. Propose 2–4 alternative paths the user could take (see the "Critical Constraint" section for examples).
3. **Wait for the user's decision** before making any further changes for those specific problems.

#### 6c. Verify fixes

- After applying all auto-selected fixes, re-run **both** commands: `clean verify -DskipTests` (Step 3b) and `dependency:tree -Dverbose` (Step 3c) to verify the fixes resolved the issues.
- Print the result of the verification runs. If new problems appeared, repeat from Step 4 for the new output. Maximum **3 fix-verify cycles** before stopping and reporting remaining issues.
- If unresolvable problems remain after the user provides input and additional fixes are applied, include those in the cycle count.

### Step 7: Final verification summary

After all fixes are applied and verified (or if no fixes were needed), print:

**"Final Verification"**

- Re-run `clean verify -DskipTests` to confirm the full build lifecycle passes: `{MVNW} clean verify -DskipTests -s maven/settings.xml 2>&1`
- Re-run `dependency:tree` (without `-Dverbose`) for a clean tree: `{MVNW} dependency:tree -s maven/settings.xml 2>&1`
- Print whether both commands succeeded or failed.
- If both succeeded, print the clean dependency tree for each module as a collapsible block.
