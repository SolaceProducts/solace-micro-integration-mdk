# Framework fix: let a `MessageSplitter` route its own outputs

**Status:** proposal + ready-to-apply patch (`splitter-target-destination-fix.patch`, in this folder).
**Target:** `SolaceDev/solace-connector-framework` (the framework binary, not this kit repo).
**Why it's here:** a direct PR to that repo is blocked for external/forked contributors by org fork policy, so the change is carried here — alongside the processor archetype it enables — for maintainer visibility. Companion to **SolaceProducts/solace-micro-integration-mdk#17**.

## Problem

Framework 4.3.0 ships a native 1→N `MessageSplitter`: `split()` returns `SplitBatch(payloads, headers)` with per-element headers, including `org.springframework.cloud.stream.binder.BinderHeaders.TARGET_DESTINATION` so a splitter can route each output. In practice that routing header never reaches the wire — it's silently dropped, so a splitter can fan out payloads but **cannot route its own outputs**. The only working path today is a separate `ProducerBindingMessageInterceptor` that sets `TARGET_DESTINATION` after the transform — undocumented and surprising for something the split API explicitly returns.

## Trace

- `BatchMessagePipe.buildTransformedMessage` runs each split element through `applyTransformations(payload, splitterHeaders)` and takes `transformedMessage.getHeaders()` for the output row.
- `TransformMessageHandler.transform` uses the incoming headers only as *source* headers and returns `MessageBuilder.createMessage(targetPayload, targetHeaders)`, where `targetHeaders` is rebuilt from the serializer — so the splitter's `TARGET_DESTINATION` is gone.

## Fix (scoped to the split path)

In `BatchMessagePipe`, re-apply the splitter's `TARGET_DESTINATION` at the split call site — gated on `messageSplitterHandler != null`, and only when the transform didn't already set a destination (so a transform that deliberately routes still wins). Non-split flows are untouched. Full change in `splitter-target-destination-fix.patch` (`BatchMessagePipe.java` + two `BatchMessagePipeTest` cases).

```java
outputBatchHeaders[i] = messageSplitterHandler != null
    ? preserveSplitterRouting(headers, transformedMessage.getHeaders())
    : transformedMessage.getHeaders();
// ...
private static MessageHeaders preserveSplitterRouting(
    MessageHeaders splitterHeaders, MessageHeaders transformedHeaders) {
  Object target = splitterHeaders.get(BinderHeaders.TARGET_DESTINATION);
  if (target == null || transformedHeaders.containsKey(BinderHeaders.TARGET_DESTINATION)) {
    return transformedHeaders;
  }
  MessageHeaderAccessor accessor = MessageHeaderAccessor.fromMessageHeaders(transformedHeaders);
  accessor.setLeaveMutable(true);
  accessor.setHeader(BinderHeaders.TARGET_DESTINATION, target);
  return accessor.getMessageHeaders();
}
```

## Alternative for maintainers

The more general fix is to preserve `TARGET_DESTINATION` inside `TransformMessageHandler` itself — but `transform()` runs for *every* flow, so that would change non-split behavior. This patch keeps the change at the split call site to target exactly the reported bug; happy to move it into `TransformMessageHandler` if the general behavior is preferred.

## Tests

Two `BatchMessagePipeTest` cases in the existing Mockito style: the routing header survives a transform that drops it (fails before the change, passes after), and a transform-set destination is not overridden by the splitter's.

**Build note:** the patch was verified by inspection (every API it uses is already used in that class; `BinderHeaders.TARGET_DESTINATION` confirmed in `spring-cloud-stream`). The tests are **CI-pending** — the framework test module depends on internal `com.solace:solace-semp-*-java-client` artifacts not on Maven Central, so the suite can't run outside Solace CI/Artifactory.
