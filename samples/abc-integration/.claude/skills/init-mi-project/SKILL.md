---
name: init-mi-project
description: Bootstrap folder structure and Maven POMs for a new Solace micro-integration project. Reads configuration from configuration.md.
tools_required:
  - Read
  - Write
  - Bash
  - Skill
---


#### General principles

- The template POMs use `abc`/`Abc`/`ABC` as the technology name and `com.solace.samples` as the project groupId. These are placeholder identities — the generated POMs must use the real project values instead.
- **Distinguish project vs. framework coordinates**: Only transform `com.solace.samples` where it represents the project or sibling module identity. Framework groupIds like `com.solace.connector.core`, `com.solace.microintegration.core`, `com.solace.spring.cloud`, `org.springframework.*`, etc. must pass through to the generated POM untouched.
- **Client SDK ordering constraint**: When a template POM contains an `abc-client` dependency, substitute the entire `<dependency>…</dependency>` block with the `CLIENT_SDK_DEPENDENCY` value (indented to match context) **before** applying the project groupId transformation, to avoid partial matches on the old `com.solace.samples` groupId inside that block.
- **No useless comments in generated POMs**: The generated POM files must be clean production-ready XML. Do not carry over or introduce comments that serve no purpose — such as TODO placeholders, boilerplate explanatory comments from the template, or comments marking removed/omitted sections (e.g. `<!-- removed -->`, `<!-- omitted -->`). Only retain comments that provide genuine, lasting value to someone reading the generated POM (e.g. a non-obvious Maven configuration rationale). When in doubt, leave the comment out.
- **Naming convention — applies only to human-readable text (`<name>`, `<description>`, XML comments):** Use **"Solace event broker"** — never "Solace PubSub+" or "PubSub+". Use **"Solace Micro-Integration Framework"** — never "connector framework" or "Connector Framework".
---


# Initialize Micro-Integration Project

Create folders and Maven POM files for a new micro-integration project (3 modules when a custom binder is generated, 2 modules when using a third-party binder). Reads the abc template POMs, applies replacements, and writes results to `TARGET_PROJECT_FOLDER`. Copies Maven wrapper and settings verbatim.

## Quick Start

```bash
/init-mi-project
```

## Implementation Instructions

> **DO NOT read CLAUDE.md files from template modules.**
> This skill must absolutely ignore any `CLAUDE.md` file located inside the following directories (including all subfolders):
> - `abc-micro-integration/`
> - `abc-test-support/`
> - `spring-cloud-stream-binder-abc/`
> - `abc-parent/`
>
> These files contain instructions scoped to other skills and must not influence this skill's behaviour. Do not read, open, or act on them under any circumstances during execution of this skill.

### Step 0: Verify Docker is running

Before any other work, confirm that Docker (or Docker Desktop) is reachable on the host machine. This check must work on both Windows and Linux/macOS.

**How to check:** Run the following command via Bash:

```
docker info
```

- **Exit code 0** → Docker is running. Proceed to Step 1.
- **Non-zero exit code** → Docker is **not** running (or not installed).

**On failure — this is a hard stop:**

- Do **NOT** proceed to Step 1 or any subsequent step.
- Do **NOT** create any directories, files, or copies.
- Print the heading: **"Docker is required — cannot proceed"**
- Print the message: _"A running Docker daemon is required to generate a micro-integration project (integration tests use Testcontainers). Please start Docker Desktop (Windows/macOS) or the Docker service (`sudo systemctl start docker` on Linux) and re-run this skill."_
- Stop.

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

- Do **NOT** create any directories, files, or copies.
- Do **NOT** proceed to Step 2 or any subsequent step.
- Print the heading: **"Configuration incomplete — cannot proceed"**
- Print a table listing every missing/unset variable with its section number and the Description from its row in the configuration table.
- Stop.

#### Resolve OS conventions (only after validation passes)

`HOST_OS` determines OS-specific behaviour throughout the skill:

| Aspect | `windows` | `linux` (includes macOS) |
|---|---|---|
| Path separator | `\` | `/` |
| Maven wrapper | `mvnw.cmd` | `./mvnw` |
| Shell prefix | `cmd /c` | `bash -c` |
| Script extension | `.cmd` / `.bat` | `.sh` |

When constructing filesystem paths, shell commands, or script invocations in any step, use the conventions matching `HOST_OS`.

#### Derive paths (only after validation passes)

| Derived | Formula |
|---|---|
| `ROOT_GROUP_PATH` | `PROJECT_ROOT_GROUP_ID` dots to `/` |
| `BINDER_PACKAGE_PATH` | `BINDER_PACKAGE` dots to `/` |
| `TEST_SUPPORT_PACKAGE_PATH` | `{ROOT_GROUP_PATH}/{TECH_NAME_LOWER}/testextension` |
| `INTEGRATION_PACKAGE_PATH` | `MI_PACKAGE` dots to `/` |

### Step 2: Create directories

Create under `{TARGET_PROJECT_FOLDER}`:

```
{TEST_SUPPORT_ARTIFACT_ID}/src/main/java/{TEST_SUPPORT_PACKAGE_PATH}/
{INTEGRATION_APP_ARTIFACT_ID}/src/main/java/{INTEGRATION_PACKAGE_PATH}/
{INTEGRATION_APP_ARTIFACT_ID}/src/main/resources/
{INTEGRATION_APP_ARTIFACT_ID}/src/test/java/{INTEGRATION_PACKAGE_PATH}/
{INTEGRATION_APP_ARTIFACT_ID}/src/test/resources/
```

**If `BINDER_SKIP_GENERATION` is NOT `true`**, also create the binder module directories:

```
{BINDER_ARTIFACT_ID}/src/main/java/{BINDER_PACKAGE_PATH}/config/
{BINDER_ARTIFACT_ID}/src/main/java/{BINDER_PACKAGE_PATH}/inbound/acknowledge/
{BINDER_ARTIFACT_ID}/src/main/java/{BINDER_PACKAGE_PATH}/outbound/
{BINDER_ARTIFACT_ID}/src/main/java/{BINDER_PACKAGE_PATH}/properties/
{BINDER_ARTIFACT_ID}/src/main/java/{BINDER_PACKAGE_PATH}/provisioning/
{BINDER_ARTIFACT_ID}/src/main/java/{BINDER_PACKAGE_PATH}/util/
{BINDER_ARTIFACT_ID}/src/main/resources/META-INF/spring/
{BINDER_ARTIFACT_ID}/src/test/java/{BINDER_PACKAGE_PATH}/
{BINDER_ARTIFACT_ID}/src/test/resources/
```

**If `BINDER_SKIP_GENERATION` is `true`**, skip all `{BINDER_ARTIFACT_ID}` directories — they are not needed.

### Step 3: Copy Maven infrastructure from template

Copy these verbatim from the workspace root into `{TARGET_PROJECT_FOLDER}`:
- `.mvn/wrapper/maven-wrapper.properties`
- `maven/settings.xml`
- `mvnw`
- `mvnw.cmd`

Use Read to read each file from the workspace root, then Write to place it at the target path.

### Step 4: Create POM files from abc templates

Use each abc template POM as a structural blueprint. Read it to understand its Maven structure, then generate a new POM at the target path by applying the transformation rules below. Each substitution is context-aware — apply it only in the correct XML position, not as a blind global string replacement.

Rules are organized per module to clarify exactly what to transform, what to omit, and what to carry forward unchanged from the template into the generated POM.

#### Template blueprints and output targets

| Blueprint (read from workspace root) | Generated POM (write to `{TARGET_PROJECT_FOLDER}/...`) | Condition |
|---|---|---|
| `pom.xml` (root) | `pom.xml` | Always |
| `abc-test-support/pom.xml` | `{TEST_SUPPORT_ARTIFACT_ID}/pom.xml` | Always |
| `spring-cloud-stream-binder-abc/pom.xml` | `{BINDER_ARTIFACT_ID}/pom.xml` | Only when `BINDER_SKIP_GENERATION` is NOT `true` |
| `abc-micro-integration/pom.xml` | `{INTEGRATION_APP_ARTIFACT_ID}/pom.xml` | Always |


---

#### 4a. Root aggregator POM

**Blueprint**: `pom.xml` (workspace root)

**Transform:**

| Template value | Generated value |
|---|---|
| `com.solace.samples` (project `<groupId>`) | `PROJECT_ROOT_GROUP_ID` |
| `solace-mdk-samples-components` (project `<artifactId>`) | `ROOT_ARTIFACT_ID` |
| `1.0.0-SNAPSHOT` (project `<version>`) | `PROJECT_VERSION` |
| `abc-test-support` (in `<module>`) | `TEST_SUPPORT_ARTIFACT_ID` |
| `spring-cloud-stream-binder-abc` (in `<module>`) | `BINDER_ARTIFACT_ID` — **only when `BINDER_SKIP_GENERATION` is NOT `true`** |
| `abc-micro-integration` (in `<module>`) | `INTEGRATION_APP_ARTIFACT_ID` |

**Omit:**

- The `<module>abc-parent</module>` line — that module is specific to the template project.
- **If `BINDER_SKIP_GENERATION` is `true`**: also omit the `<module>{BINDER_ARTIFACT_ID}</module>` line — the binder module will not be generated. The resulting `<modules>` block lists only `{TEST_SUPPORT_ARTIFACT_ID}` and `{INTEGRATION_APP_ARTIFACT_ID}`.

**Carry forward from template:** packaging, name, description — these pass through unchanged into the generated POM.

---

#### 4b. Test-support POM

**Blueprint**: `abc-test-support/pom.xml`

**Transform:**

| Template value | Generated value |
|--------------------------------------------------------------------------------------|---|
| `abc-test-support` (project `<artifactId>`) | `TEST_SUPPORT_ARTIFACT_ID` |
| `com.solace.samples` (project `<groupId>`) | `PROJECT_ROOT_GROUP_ID` |
| `1.0.0-SNAPSHOT` (project `<version>` and sibling cross-reference versions) | `PROJECT_VERSION` |
| `3.1.2` (in `micro-integration-platform-bom` `<version>`)                            | `MICRO_INTEGRATION_PLATFORM_BOM_VERSION` |
| `17` (in `maven.compiler.source`, `maven.compiler.target`, `maven.compiler.release`) | `JAVA_VERSION` |
| Entire `abc-client` `<dependency>` block | `CLIENT_SDK_DEPENDENCY` value (apply before groupId transformation) |

**Omit:**

- The entire `<dependency>` block for `abc-service` — a backend-service emulator used only by the sample project.
- The entire `<dependency>` block for `jib-core` — only needed when a custom Docker image must be built as part of the test setup. Not a baseline requirement for a new project.
- The entire `<dependency>` block for `dotenv-java`.

**Carry forward from template:** all remaining framework dependencies (JUnit Jupiter, Spring Test, Testcontainers, commons-compress) and all other elements pass through unchanged into the generated POM.

---

#### 4c. Binder POM

**Skip this entire substep if `BINDER_SKIP_GENERATION` is `true`.** No binder POM is generated — proceed directly to 4d.

**Blueprint**: `spring-cloud-stream-binder-abc/pom.xml`

**Transform:**

| Template value | Generated value |
|-------------------------------------------------------------------------------|---|
| `spring-cloud-stream-binder-abc` (project `<artifactId>`) | `BINDER_ARTIFACT_ID` |
| `com.solace.samples` (project `<groupId>` and sibling cross-references) | `PROJECT_ROOT_GROUP_ID` |
| `1.0.0-SNAPSHOT` (project `<version>` and sibling cross-reference versions) | `PROJECT_VERSION` |
| `3.1.2` (in `pubsubplus-connector-component-build-parent` parent `<version>`) | `BINDER_BUILD_PARENT_VERSION` |
| `17` (in `maven.compiler.release`) | `JAVA_VERSION` |
| Entire `abc-client` `<dependency>` block | `CLIENT_SDK_DEPENDENCY` value (apply before groupId transformation) |
| `abc-test-support` (in dependency `<artifactId>`) | `TEST_SUPPORT_ARTIFACT_ID` |

**Omit:**

- Every `<JAR_PATH>` entry in both `maven-surefire-plugin` and `maven-failsafe-plugin` configurations (environment variables and system properties). These reference `../abc-parent/abc-service/target/abc-service-${project.version}.jar` which is specific to the sample project and will not exist in the new project.

**Pass through unchanged:**

- The `<parent>` block groupId (`com.solace.connector.core`) and artifactId (`pubsubplus-connector-component-build-parent`) — these are framework references, not project values. Only the parent `<version>` is transformed (see row for `BINDER_BUILD_PARENT_VERSION` above).
- Framework dependency groupIds (`org.springframework.*`, `com.solace.connector.core`).

**Carry forward from template:** all build plugins (enforcer, flatten, surefire, failsafe), remaining framework dependencies, metadata stubs (licenses, developers, scm), and all other elements pass through unchanged into the generated POM.

The `<licenses>` block and the TODO comment above it must be copied **verbatim**. The generated project inherits the Solace Community License because it is derived from this SCL-licensed template; the TODO tells the developer they may relicense their own work. Do not remove the block, substitute another license, or fill in `<developers>`/`<scm>` — those remain empty stubs for the developer.

---

#### 4d. Micro-integration POM

**Blueprint**: `abc-micro-integration/pom.xml`

**Transform (common — always applied):**

| Template value | Generated value |
|-------------------------------------------------------------------------|---|
| `abc-micro-integration` (project `<artifactId>`) | `INTEGRATION_APP_ARTIFACT_ID` |
| `com.solace.samples` (project `<groupId>` and sibling cross-references) | `PROJECT_ROOT_GROUP_ID` |
| `1.0.0-SNAPSHOT` (project `<version>`) | `PROJECT_VERSION` |
| `3.1.2` (in `micro-integration-build-parent` parent `<version>`)        | `MICRO_INTEGRATION_BUILD_PARENT_VERSION` |
| `17` (in `maven.compiler.source`, `maven.compiler.target`) | `JAVA_VERSION` |
| `abc-test-support` (in dependency `<artifactId>`) | `TEST_SUPPORT_ARTIFACT_ID` |




**Transform (custom binder mode — when `BINDER_SKIP_GENERATION` is NOT `true`):**

| Template value | Generated value |
|---|---|
| `abc` in Maven property name `<abc-binder.version>` | `TECH_NAME_LOWER` (becomes `<{TECH_NAME_LOWER}-binder.version>`) |
| `${abc-binder.version}` in dependency version references | `${{TECH_NAME_LOWER}-binder.version}` |
| `spring-cloud-stream-binder-abc` (in dependency `<artifactId>`) | `BINDER_ARTIFACT_ID` |

**Note on Maven property references**: After the technology-name transformation, the property tag `<{TECH_NAME_LOWER}-binder.version>` and the `${}` references `${{TECH_NAME_LOWER}-binder.version}` are literal Maven property references that must appear verbatim in the generated POM — they are **not** skill placeholders to be resolved further.

**Transform (third-party binder mode — when `BINDER_SKIP_GENERATION` is `true`):**

Replace the entire custom binder `<dependency>` block (the one referencing `spring-cloud-stream-binder-abc`) with the `BINDER_3PARTY_DEPENDENCY` value from configuration.md. Also:

- **Remove** the `<abc-binder.version>` Maven property — it is no longer needed since the third-party binder manages its own version.
- **Remove** the `${abc-binder.version}` version reference from the replaced dependency block.
- **Fix the test-support dependency version** — the `abc-test-support` dependency also references `${abc-binder.version}`, which no longer exists. Replace its `<version>` with the value of the `PROJECT_VERSION` configuration variable (e.g., if `PROJECT_VERSION` is `1.0.0-SNAPSHOT`, the result is `<version>1.0.0-SNAPSHOT</version>`).

**Pass through unchanged:**

- The `<parent>` block groupId (`com.solace.microintegration.core`) — it is a framework reference, not the project groupId. Only the parent `<version>` is transformed (see row for `MICRO_INTEGRATION_BUILD_PARENT_VERSION` above).
- Framework dependency groupIds (`com.solace.connector.core`, `com.solace.spring.cloud`, `org.springframework.*`, etc.).

**Carry forward from template:** all properties (revision, changelist, next-revision), the empty `<dependencyManagement>` section, build plugins (enforcer, flatten), framework dependencies, metadata stubs, and all other elements pass through unchanged into the generated POM.

### Step 5: Build verification

Run after all POM files are written. Verifies that every generated module resolves its dependencies and compiles (even though no Java source exists yet, this validates POM correctness, dependency resolution, and reactor ordering).

#### Compile check

Run `mvn compile` on the full reactor from `{TARGET_PROJECT_FOLDER}`, using the Maven wrapper and `JAVA_HOME` from configuration:

**`windows`:**
```
cmd.exe //c "set JAVA_HOME={JDK_PATH}&& cd /d {TARGET_PROJECT_FOLDER} && .\mvnw.cmd compile -s maven\settings.xml" 2>&1
```

**`linux`:**
```
JAVA_HOME="{JDK_PATH}" {TARGET_PROJECT_FOLDER}/mvnw compile -s maven/settings.xml 2>&1
```

Use a 5-minute timeout (300 000 ms) — first run may download dependencies.

#### On success

Print: **"Build verification passed — all modules compile."**

Proceed to Step 6.

#### On failure

Print the compilation errors, then invoke `/fix-maven-dependencies` to diagnose and fix dependency issues.

After `/fix-maven-dependencies` completes, re-run the compile check above. If it still fails after `fix-maven-dependencies` has finished its fix cycles, print the remaining errors and stop — do not loop further.

### Step 6: Print summary

List the `TARGET_PROJECT_FOLDER` path and all generated module names (2 or 3 depending on `BINDER_SKIP_GENERATION`).
