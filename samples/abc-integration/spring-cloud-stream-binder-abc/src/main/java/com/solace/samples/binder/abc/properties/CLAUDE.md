# properties/ package

Six classes backing two separate YAML namespaces. Every Spring Cloud Stream binder needs both namespaces even if one side is empty.

## Namespace 1 — Connection (`abc.*`)

How to reach the backend. Shared by all bindings on the same binder instance. In multi-binder mode, relocates under `spring.cloud.stream.binders.<name>.environment.abc`.

```yaml
abc:
  base-url: http://localhost:8088
  port: 9090
  authentication:
    type: basic
    username: user
    password: pass
```

- `AbcBinderConnectionProperties` — `@ConfigurationProperties("abc")`, `@Validated`. Fields: `baseUrl` (`@NotNull @Pattern`), `port`, `authentication` (`@NestedConfigurationProperty`).
- `AuthenticationConfig` — plain POJO, no Spring annotations. Nested inside connection properties. `toString()` redacts credentials.

## Namespace 2 — Extended binding (`spring.cloud.stream.abc.*`)

Per-binding consumer/producer settings with defaults. The `default` block provides fallback values; the `bindings` block overrides per channel.

```yaml
spring.cloud.stream:
  abc:                                # AbcExtendedBindingProperties root
    default:                          # DEFAULTS_PREFIX
      consumer:                       # AbcConsumerProperties defaults
        polling-interval: 1000
      producer:                        # AbcProducerProperties defaults
        write-timeout-ms: 5000
    bindings:                         # per-binding overrides
      myOutput-out-0:
        producer:
          write-timeout-ms: 10000
```

- `AbcExtendedBindingProperties` — `@ConfigurationProperties("spring.cloud.stream.abc")`, `@Validated`. Extends `AbstractExtendedBindingProperties<AbcConsumerProperties, AbcProducerProperties, AbcBindingProperties>`. Defines `DEFAULTS_PREFIX = "spring.cloud.stream.abc.default"` (`protected static final`).
- `AbcBindingProperties` — implements `BinderSpecificPropertiesProvider`. No Spring annotations. Holds one `AbcConsumerProperties` + one `AbcProducerProperties` per binding. `getConsumer()`/`getProducer()` return `Object` (interface contract).
- `AbcConsumerProperties` — `@ConfigurationProperties(DEFAULTS_PREFIX + ".consumer")`, `@SuppressWarnings("ConfigurationProperties")`. Has `pollingInterval` (default 5000ms).
- `AbcProducerProperties` — `@ConfigurationProperties(DEFAULTS_PREFIX + ".producer")`, `@SuppressWarnings("ConfigurationProperties")`. Has `writeTimeoutMs` (default 5000ms) — max time to wait for async publish to complete.

## How they get wired (outside this package)

1. `AbcBinderConfiguration` — `@EnableConfigurationProperties({AbcExtendedBindingProperties.class, AbcBinderConnectionProperties.class})` registers both roots with Spring Boot.
2. `ExtendedBindingHandlerMappingsProviderConfiguration` — maps `spring.cloud.stream.abc.bindings` to `spring.cloud.stream.abc.default` so per-binding values inherit defaults.
3. `AbcBinder` — holds both property objects; delegates `getExtendedConsumerProperties()`, `getExtendedProducerProperties()`, `getDefaultsPrefix()`, `getExtendedPropertiesEntryClass()` to `extendedBindingProperties`.

## Rules for adding fields

- **Connection concern** (URL, host, port, creds, TLS, timeouts) → `AbcBinderConnectionProperties`. Structured sub-blocks (auth, TLS) → separate POJO with `@NestedConfigurationProperty`.
- **Consumer concern** (poll interval, batch size, concurrency) → `AbcConsumerProperties`.
- **Producer concern** (retry, compression, write timeout) → `AbcProducerProperties`.
- Consumer/producer fields are automatically overridable per binding via the `ExtendedBindingProperties` + `MappingsProvider` mechanism — no extra code needed.
