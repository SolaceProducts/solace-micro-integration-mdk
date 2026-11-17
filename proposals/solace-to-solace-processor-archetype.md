# Proposal: a Solace-to-Solace `processor` archetype for the MDK generator

**Status:** Proposal, with a first draft of the generator change in this PR
**Scope:** Developer experience — a new generated shape plus three runtime-trap fixes
**Based on:** building a real Solace-to-Solace processing micro-integration on MDK 2.0.0 + Micro-Integration Framework 4.3.0 (consume raw XML from mesh queues, run a stateless transform, republish normalized events to the mesh).

---

## Summary

Add a first-class **`processor`** archetype to the generator for the "consume from the mesh → transform in Java/SpEL → publish back to the mesh" pattern. Today every generated shape bridges the Solace event broker to a *foreign* technology (a second binder); the in-mesh processor — arguably the most common integration shape — has no template. You adapt the bridge sample (ABC, with its custom binder) and hit several avoidable traps. One archetype removes them and makes the generated artifact start standalone, with a smoke test that proves it round-trips a message.

This is pure developer experience and sits inside the MDK's stated scope.

## Motivation — what prompted this

I built an MI that consumes raw XML from mesh queues, runs a stateless transform, and republishes normalized events to the mesh. It works and is live-verified against a broker. But every point of friction traced to one root cause: **the paved path is the external-system bridge (a foreign binder); the in-mesh processor isn't a generated option.** You reverse-engineer the bridge sample, and three traps follow — all of them specific to the Java-interceptor transform strategy:

1. **Capabilities-factory landmine.** The sample's `Abc*BindingCapabilitiesFactory` are keyed to the custom binder and *must not* be registered for Solace-to-Solace (the Solace binder self-registers via `BindingCapabilitiesRegistry` on both sides). Nothing documents this. "Mirror the sample" here does not compile.
2. **The jar won't start standalone.** Spring Boot 4 auto-configures only a Jackson 3 mapper (`tools.jackson`), but the interceptor pattern injects a Jackson 2 `com.fasterxml.jackson.databind.ObjectMapper`. The generated uber-jar fails with:
   ```
   Parameter 1 of method ... required a bean of type
   'com.fasterxml.jackson.databind.ObjectMapper' that could not be found.
   ```
   The framework runtime supplies this bean at deploy time, so it "works on the broker, breaks when the developer runs their own jar."
3. **Cryptic failure with no workflow config.** Running without an active workflow profile throws a bare `NullPointerException` in topology creation instead of a clear "no workflows configured."

Each is small on its own; together they turn a one-afternoon build into a day of spelunking.

## Proposed change

When the developer selects **processor** (both sides Solace, transform in the middle), the generator emits a skeleton that is correct and standalone-runnable:

- **App module only** — no foreign binder module; both input and output bindings bound to the Solace binder.
- **Transform strategy, defaulting to the paved path.** The framework already ships a declarative `transform:` SpEL block (generated commented-out on workflow 0 today). The processor archetype enables it by default for simple header/field/content-type transforms — no Java needed. For transforms too complex for SpEL, it falls back to a Java `ProducerBindingMessageInterceptor`, and *there* applies the three fixes:
  - **A correct `ObjectMapper` bean** (`@ConditionalOnMissingBean`, backs off to the framework's at deploy time) — fixes trap 2.
  - **No capabilities factories**, with a one-line comment that the Solace binder self-registers both sides — fixes trap 1.
  - **Fail-fast config validation** — a clear message when no workflow/profile is active — fixes trap 3.
- **A generated smoke test** that boots the app and round-trips one message on a Testcontainers Solace broker, so "does my artifact actually run?" is answered by the scaffold.

### Interceptor placement — an open design question

A `ProducerBindingMessageInterceptor.before()` runs on the **output** binding; a `ConsumerBindingMessageInterceptor.after()` runs on the **input** binding (post-consume). The reference build used the producer-side `before()` because the transform also set the output destination per record (`BinderHeaders.TARGET_DESTINATION`), which co-locates transform and routing. For a pure ingress normalization with a static output, the post-consume hook is just as valid. The draft defaults to producer-side and documents the choice; **which hook the archetype should model by default is worth a maintainer call.**

## What this PR drafts vs. what remains

This PR drafts the core generation rules, as skills/docs only (no Java in the template modules, so `./mvnw clean verify` is unaffected):

- **New** [`add-microintegration/references/generate-processor.md`](../samples/abc-integration/.claude/skills/add-microintegration/references/generate-processor.md) — the processor generation reference (both-ends-Solace bindings, enabled workflow 0, transform block on by default, the three fixes for the Java strategy, and the smoke test).
- **Edit** [`add-microintegration/references/generate-mi.md`](../samples/abc-integration/.claude/skills/add-microintegration/references/generate-mi.md) — a fourth case in Step 2b ("Processor (Solace → Solace)") that routes to the new reference.

Remaining wiring for the full archetype (maintainer epic):

- `generate-mi-flow/SKILL.md` — a processor mode that skips the binder step (`add-binder` / `analyze-3party-binder`) and trims `add-test-support` to the Solace broker only.
- `prepare-config/SKILL.md` — an explicit archetype/shape selector (today shape is implicit in `BINDER_TYPE` + `BINDER_SKIP_GENERATION`).
- `init-mi-project/SKILL.md` — confirm the two-module layout (no binder module) for the processor path.
- A `ProcessorRoundTripIT.java` + `application-messaging-processor.yml` template pair in the `abc-micro-integration` module.
- `clean-claude-files` / `clean-readme-files` — processor wording (no foreign tech to describe).

## Scope

**In scope:** generator templates for the processor archetype, the transform-strategy default, the three fixes, and the smoke test. Small, self-contained, DX-only.

**Out of scope — belongs in the stream plane, not the connector:** a managed state store, replay-based recovery, exactly-once. In the reference build these were deliberately left to the stream processor; the connector stays stateless.
