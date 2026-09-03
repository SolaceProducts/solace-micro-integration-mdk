# container/proxy

Remote-backend test infrastructure connecting to a pre-running service (no container).

- **AbcServiceProxy** — plain Java class (`AutoCloseable`, no container). Reads connection URL and credentials from environment variables via dotenv-java. Creates an `AbcClient` on `start()` and closes it on `close()`. Exposes `getClient()`, `getConnectionUrl()`, `getBasicAuthUsername()`, `getBasicAuthPassword()` for tests.
- **AbcProxyTestExtension** — JUnit 5 extension (`BeforeAllCallback`/`AfterAllCallback`/`ParameterResolver`). Creates and starts the proxy in `beforeAll`, stores in `ExtensionContext`, injects `AbcServiceProxy` into test method parameters, closes in `afterAll`.
