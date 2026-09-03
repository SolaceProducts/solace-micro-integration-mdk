# ABC Test Support

Template test-support module demonstrating three approaches for integration testing with a backend service. The ABC service is a fictitious service created for demonstration purposes only. Each approach provides a JUnit 5 extension with parameter injection so tests receive a ready-to-use client.

## Module Structure

```
src/main/java/.../testextension/
  container/
    from_binaries/   AbcTestContainer            — Jib-built image from JAR
    simple/          AbcTestContainerWithConnectedClient
                     AbcSimpleContainerTestExtension
    proxy/           AbcServiceProxy
                     AbcProxyTestExtension

src/test/java/.../testextension/
    SimpleAbcTestContainerIT                      — tests the simple container
    SimpleAbcProxyIT                              — tests the proxy
```

## Approaches

### 1. From Binaries (`container/from_binaries`)

Builds a Docker image at runtime from a service JAR using [Jib](https://github.com/GoogleContainerTools/jib). Extends `GenericContainer`. Useful when no official Docker image exists and you have the service artifact locally.

- `AbcTestContainer` — builds the image in a `static` initializer, configures port, wait strategy, and credentials.

### 2. Simple Container Wrapper (`container/simple`)

Extends an existing container class (here `AbcTestContainer`) and adds a connected SDK client. Use this pattern when a container class already exists (official Testcontainers module or community) and you need to embed a client.

- `AbcTestContainerWithConnectedClient` — extends `AbcTestContainer`, creates an `AbcClient` on `start()`, closes it on `stop()`.
- `AbcSimpleContainerTestExtension` — JUnit 5 extension managing the wrapper lifecycle and injecting it into test methods.

### 3. Remote Backend Proxy (`container/proxy`)

Connects to a pre-running service (cloud, SaaS, or local) via environment variables. No container, no Docker required. Uses [dotenv-java](https://github.com/cdimascio/dotenv-java) to load a `.env` file.

- `AbcServiceProxy` — plain Java class (`AutoCloseable`). Reads connection URL and credentials from environment variables, creates an `AbcClient` on `start()`.
- `AbcProxyTestExtension` — JUnit 5 extension managing the proxy lifecycle and injecting it into test methods.
- `.env` — template file in the module root with connection variables and security notes.

## Usage Pattern

All approaches follow the same test pattern — `@ExtendWith` with parameter injection:

```java
@ExtendWith(AbcSimpleContainerTestExtension.class)
class MyIntegrationTest {

    @Test
    void myTest(AbcTestContainerWithConnectedClient container) {
        AbcClient client = container.getClient();
        // use client
    }
}
```

For the proxy approach, substitute `AbcProxyTestExtension` and `AbcServiceProxy`.

## Dependencies

- Java 17+
- Docker (container approaches only)
- JUnit 5
- Testcontainers (container approaches only)
- dotenv-java (proxy approach only)

## Running Tests

Tests run automatically as part of the full project build (`./mvnw clean install` from the project root). To run them in isolation:

```bash
# Container-based integration test (requires Docker)
./mvnw verify -pl abc-test-support -am

# Proxy-based test (requires running backend + .env configured)
./mvnw verify -pl abc-test-support -am -Dit.test=SimpleAbcProxyIT
```
