# Build Verification

Run after all source files and POM updates are complete. Verifies the test-support module compiles and its integration tests pass.

## Phase 1: Compile check

Run `mvn compile` scoped to the test-support module from `{TARGET_PROJECT_FOLDER}`, using the Maven wrapper and `JAVA_HOME` from configuration:

**`windows`:**
```
cmd.exe //c "set JAVA_HOME={JDK_PATH}&& cd /d {TARGET_PROJECT_FOLDER} && .\mvnw.cmd compile -pl {TEST_SUPPORT_ARTIFACT_ID} -s maven\settings.xml" 2>&1
```

**`linux`:**
```
JAVA_HOME="{JDK_PATH}" {TARGET_PROJECT_FOLDER}/mvnw compile -pl {TEST_SUPPORT_ARTIFACT_ID} -s maven/settings.xml 2>&1
```

Use a 5-minute timeout (300000 ms) — first run may download dependencies.

### On compile success

Print: **"Phase 1 — compilation: PASSED"**

Proceed to Phase 2.

### On compile failure

Print the compilation errors, then invoke `/fix-maven-dependencies` to diagnose and fix dependency issues.

After `/fix-maven-dependencies` completes, re-run the compile check above. If it still fails after `fix-maven-dependencies` has finished its fix cycles, print the remaining errors and stop — do not proceed to Phase 2.

## Phase 2: Integration test (clean verify)

Run `mvn clean verify` scoped to the test-support module. This executes the `SimpleTestContainerIT` integration test via the Failsafe plugin, which starts the Testcontainer, connects the SDK client, and runs assertions.

**`windows`:**
```
cmd.exe //c "set JAVA_HOME={JDK_PATH}&& cd /d {TARGET_PROJECT_FOLDER} && .\mvnw.cmd clean verify -pl {TEST_SUPPORT_ARTIFACT_ID} -s maven\settings.xml" 2>&1
```

**`linux`:**
```
JAVA_HOME="{JDK_PATH}" {TARGET_PROJECT_FOLDER}/mvnw clean verify -pl {TEST_SUPPORT_ARTIFACT_ID} -s maven/settings.xml 2>&1
```

Use a 10-minute timeout (600000 ms) — container startup and image pull can be slow on first run.

**Prerequisite:** Docker must be running. If the output contains `Could not find a valid Docker environment` or `docker: not found`, print: **"Docker is required — cannot run integration tests"** and stop.

### On verify success

Print: **"Phase 2 — integration tests: PASSED"**

Proceed to the summary step.

### On verify failure

Read the Failsafe output and identify the root cause. Common failure categories:

1. **`NoClassDefFoundError` / `ClassNotFoundException`** — a transitive runtime dependency is excluded or missing. Remove the offending `<exclusion>` from the test-support POM or add the missing dependency explicitly. Re-run Phase 2.
2. **Connection refused / timeout** — the container started but the SDK client cannot connect. Check that the connection URL, port, and credentials in `createClient()` match the container's getter methods. Fix the wrapper source and re-run Phase 2.
3. **Test assertion failure** — the test expectation does not match reality. Fix the test class and re-run Phase 2.

Retry up to 3 times total (initial run + 2 repair attempts). If the test still fails after 3 attempts, print the remaining errors and stop.
