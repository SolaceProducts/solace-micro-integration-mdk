# Generate a Solace-to-Solace Processor Micro-Integration

Instructions for generating a **processor** micro-integration: both the input and the output bind to the Solace event broker, and a Java/SpEL transform sits in the middle (consume from the mesh → transform → publish back to the mesh). This is a distinct shape from the producer/consumer/both cases in `generate-mi.md`, which always bridge Solace to a *foreign* technology.

Use this reference when `configuration.md` selects the processor archetype (`MI_ARCHETYPE: processor`, or `BINDER_SKIP_GENERATION: true` with both bindings resolved to `solace`). The processor shape has **no foreign binder module and no foreign Testcontainer** — the Solace binder is on both ends — so `generate-mi-flow` skips the binder step and `add-test-support` only needs the Solace test broker.

The input to this step is `{MI_CLAUDE_MD}` (produced by the planning step) and configuration variables from `configuration.md` (parsed in SKILL.md Step 1). The output is generated source in `{MI_TARGET_MODULE_DIR}`.

## Path constraint

All file operations must stay within exactly two directory trees:

1. **Workspace root** — the directory containing `configuration.md` (template source).
2. **`TARGET_PROJECT_FOLDER`** — as read from `configuration.md` (generated output).

Do **NOT** access, list, or search any parent directory of either location. When using `Glob` or `Grep`, always set the `path` parameter to one of these two roots or a subdirectory within them.

---

## Data Sources

- **`{MI_CLAUDE_MD}`** — the MI design document from the planning step. For a processor it carries: the transform strategy (declarative SpEL vs. Java interceptor), the source/target content types, and (for the Java strategy) the interceptor class name and output-destination rule.
- **`configuration.md`** — user-provided configuration (paths, package names, artifact IDs). Already parsed in SKILL.md Step 1.

A processor needs **no** Connection Properties Mapping, Binder Capability Modes, or foreign-SDK sections — there is no foreign technology to connect to.

---

## Template Files

Reuse the ABC template module, but a processor emits a **smaller** set of files than the bridge shapes. There is no foreign binder, so **no foreign capabilities factories and no foreign connection properties** are generated.

| Template file (read from workspace root) | Purpose |
|---|---|
| `abc-micro-integration/src/main/java/com/solace/samples/microintegration/MicroIntegrationApplication.java` | Spring Boot main class |
| `abc-micro-integration/src/main/java/com/solace/samples/microintegration/AbcProducerBindingInterceptorFactory.java` | Structural template for the Java transform strategy only (Step 2) |
| `abc-micro-integration/src/main/java/com/solace/samples/microintegration/package-info.java` | Non-null annotations |
| `abc-micro-integration/src/main/resources/application.yml` | Framework boilerplate (workflow routing) |
| `abc-micro-integration/src/main/resources/application-operator.yml` | Operator config — bindings, the enabled workflow, transform block |

> **Do not** generate `Abc*BindingCapabilitiesFactory`, `Abc*ConsumerBindingMessageInterceptorFactory`, `BaseTest.java`'s foreign-connection constants, or any `application-messaging-*.yml` profile that injects foreign connection properties. See Trap 1 below.

---

## Step 1: Choose the transform strategy

Read the transform strategy from `{MI_CLAUDE_MD}`. There are two, and the archetype defaults to the first:

**(A) Declarative SpEL transform (default).** For header/field remapping and content-type conversion, enable the framework's built-in `transform:` block on the workflow (Step 3). No Java is generated beyond the main class. This is the paved path — prefer it whenever the transform can be expressed in SpEL.

**(B) Java `ProducerBindingMessageInterceptor` (escape hatch).** When the transform is too complex for SpEL (parsing, calling a library, assembling a structured payload), generate a `ProducerBindingMessageInterceptor` whose `before(Message)` performs the transform and returns the new message. Generate the Java bean in Step 2; the three traps below are fixed there. Everything in Step 2 is skipped for strategy (A).

> **Interceptor placement is a design choice, document it in the generated CLAUDE.md.** A `ProducerBindingMessageInterceptor.before()` runs on the **output** binding — the natural home when the transform also sets the output destination (e.g. per-record dynamic routing via `BinderHeaders.TARGET_DESTINATION`). The alternative, `ConsumerBindingMessageInterceptor.after()`, runs on the **input** binding (post-consume) and suits a pure ingress normalization with a static output destination. The archetype defaults to the producer-side `before()` because it co-locates transform and routing; emit a one-line comment stating which hook was used and why.

---

## Step 2: Java transform strategy — main class and interceptor

**Only for strategy (B). Skip entirely for strategy (A).**

### 2a. Main application class with the three fixes

Read `MicroIntegrationApplication.java`, apply general transformations, and emit these three beans/guards. Each fixes a trap that otherwise only surfaces at runtime on a live broker.

**Fix 1 — register only the interceptor factory; no capabilities factories.** For a Solace-to-Solace processor the Solace binder self-registers its binding capabilities on **both** sides via `BindingCapabilitiesRegistry`. Registering foreign `*BindingCapabilitiesFactory` beans (as the bridge sample does) is wrong here and will not compile against the Solace binder. Emit the interceptor factory bean and a one-line comment:

```java
@Bean
public <Name>ProducerInterceptorFactory <name>ProducerInterceptorFactory(
        FeedRoutingProperties routing, ObjectMapper objectMapper) {
    return new <Name>ProducerInterceptorFactory(routing, objectMapper);
}
// No BindingCapabilitiesFactory beans: the Solace binder self-registers capabilities on
// both sides for a Solace-to-Solace processor. Those are only needed when bridging a
// foreign technology binder.
```

**Fix 2 — a backing `ObjectMapper` bean so the uber-jar runs standalone.** Spring Boot 4 auto-configures a Jackson 3 mapper (`tools.jackson`), not a Jackson 2 `com.fasterxml.jackson.databind.ObjectMapper`. The interceptor needs the Jackson 2 type. The Micro-Integration Framework runtime supplies this bean at deploy time, but without it the generated jar fails to start standalone with `required a bean of type 'com.fasterxml.jackson.databind.ObjectMapper' that could not be found`. Emit:

```java
/**
 * Jackson 2 ObjectMapper for the interceptor's JSON work. The Micro-Integration Framework
 * runtime supplies one at deploy time; @ConditionalOnMissingBean backs off to it and keeps
 * the uber-jar runnable standalone.
 */
@Bean
@ConditionalOnMissingBean
public ObjectMapper objectMapper() {
    return new ObjectMapper();
}
```

**Fix 3 — fail fast when no workflow is configured.** Running without an active workflow profile otherwise throws a bare `NullPointerException` deep in topology creation instead of a clear message. Emit a `@PostConstruct` guard (or validate in the interceptor factory):

```java
if (workflows == null || workflows.isEmpty()) {
    throw new IllegalStateException(
        "No workflows configured. Activate the operator profile (application-operator.yml) "
      + "or define solace.connector.workflows.* before starting this micro-integration.");
}
```

### 2b. Producer interceptor

Read `AbcProducerBindingInterceptorFactory.java` as the structural template. Generate `{MI_TARGET_SRC_DIR}/{Name}ProducerInterceptorFactory.java` implementing `ProducerBindingMessageInterceptorFactory`, whose inner `ProducerBindingMessageInterceptor.before(Message)` runs the transform from `{MI_CLAUDE_MD}` and returns the transformed `Message`. If the transform routes output dynamically, set `BinderHeaders.TARGET_DESTINATION` on the returned message. Do **not** generate a consumer interceptor or any capabilities factory.

---

## Step 3: Generate application-operator.yml (both strategies)

Read `abc-micro-integration/src/main/resources/application-operator.yml` and adapt it for the processor shape.

#### Bindings & workflow — Processor (Solace → Solace)

Both ends use the Solace binder; the single workflow is **enabled**:

```yaml
spring:
  cloud:
    stream:
      bindings:
        input-0:
          binder: solace
          # Solace source destination (queue) — set per operator deployment
        output-0:
          binder: solace
          # Solace target destination — static here, or set dynamically by the
          # interceptor via BinderHeaders.TARGET_DESTINATION (Java strategy)
      solace:
        default:
          producer:
            destination-type: topic
```

- Remove `input-1` / `output-1` and workflow `1` entirely (a processor is a single flow).
- Set workflow `0: enabled: true` (the bridge shapes leave `0` disabled; the processor's one flow **is** workflow 0).
- There is **no** `{RESOLVED_BINDER_NAME}` section and **no** foreign connection-properties comment block — both sides are Solace.

#### Transform block — enabled by default (strategy A) or omitted (strategy B)

For **strategy (A)**, un-comment and enable the `transform:` block on workflow `0`, filling content types and SpEL expressions from `{MI_CLAUDE_MD}`:

```yaml
solace:
  connector:
    workflows:
      0:
        enabled: true
        transform:
          enabled: true
          source-payload:
            content-type: "{SOURCE_CONTENT_TYPE}"   # e.g. application/xml
          target-payload:
            content-type: "{TARGET_CONTENT_TYPE}"   # e.g. application/json
          expressions:
            - transform: "target['payload']['...'] = source['payload']['...']"
```

For **strategy (B)**, leave the `transform:` block commented out — the Java interceptor performs the transform — but keep workflow `0: enabled: true`.

Carry over the `solace.java`, `management`, `endpoint`, and `logging` sections from the template unchanged. In `logging.level`, drop the `com.solace.samples.binder.abc` line (there is no foreign binder) and keep `com.solace.connector.core` and `com.solace.spring.cloud.stream.binder`. No `abc`/`Abc`/`ABC` references should remain.

Write to `{MI_TARGET_RESOURCES_DIR}/application-operator.yml`.

---

## Step 4: Generate application.yml

Read `abc-micro-integration/src/main/resources/application.yml` (framework boilerplate, workflow routing `input-{N}` → `output-{N}`), apply general transformations, and write to `{MI_TARGET_RESOURCES_DIR}/application.yml`.

---

## Step 5: Generate the smoke test (Solace → Solace round-trip)

The archetype ships one green integration test so "does my artifact actually run?" is answered by the scaffold, not discovered on a live broker later.

Using the Solace Testcontainer from the test-support module, generate `{MI_TARGET_TEST_SRC_DIR}/ProcessorRoundTripIT.java` that:

1. Boots the app on the operator profile against a Testcontainers Solace broker.
2. Publishes one message to the `input-0` source destination.
3. Asserts exactly one message arrives on the `output-0` target destination, with the expected transformed payload/content-type.

Generate the matching `{MI_TARGET_TEST_RESOURCES_DIR}/application-messaging-processor.yml` profile (both bindings `solace`, workflow `0` enabled, transform block matching the chosen strategy). No foreign connection properties are injected.

This test needs only the Solace test broker — no foreign Testcontainer — so it runs under the existing `verify-microintegration` flow with `./mvnw clean verify`.
