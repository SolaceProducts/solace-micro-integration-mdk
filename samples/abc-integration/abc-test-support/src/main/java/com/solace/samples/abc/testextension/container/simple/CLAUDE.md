# container/simple

Testcontainers-based test infrastructure using existing test container (`container.from_binaries.AbcTestContainer`).

- **AbcTestContainerWithConnectedClient** — extends `AbcTestContainer` (treated as an external/community container class not under our control), adds an `AbcClient` that auto-connects on `start()` and closes on `stop()`. Exposes `getClient()` for tests.
- **AbcSimpleContainerTestExtension** — JUnit 5 extension (`BeforeAllCallback`/`ParameterResolver`). Uses `getOrComputeIfAbsent` for lazy shared-container creation on first `beforeAll`; subsequent test classes reuse the same container. Injects `AbcTestContainerWithConnectedClient` into test method parameters. Cleanup is handled by the `SharedContainerResource` inner class (`ExtensionContext.Store.CloseableResource`) — the container is automatically stopped when the root `ExtensionContext` store closes at end of test suite.
