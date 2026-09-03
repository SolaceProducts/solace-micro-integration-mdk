# ABC Service Integration

A sample Micro-Integration project demonstrating AI-enabled integration between the Solace event broker and the ABC service using the Solace MDK and Spring Cloud Stream. The ABC service is a fictitious service created for demonstration and educational purposes only.


## Project Structure

```
abc-integration/
├── abc-parent/                          - Parent project for ABC service ecosystem including service and client
│   ├── abc-service/                     - ABC backend service implementation
│   └── abc-client/                      - Java client library for ABC service
├── abc-test-support/                    - Testcontainers and utilities for integration testing, bootstrapping ABC service in tests
├── spring-cloud-stream-binder-abc/      - Spring Cloud Stream binder for ABC service
└── abc-micro-integration/               - Solace ↔ ABC Micro-Integration
```

## Modules

- **[abc-parent](./abc-parent/README.md)** - Parent POM for the ABC service ecosystem including service and client
- **[abc-test-support](./abc-test-support/README.md)** - Test utilities for integration testing
- **[spring-cloud-stream-binder-abc](./spring-cloud-stream-binder-abc/README.md)** - Spring Cloud Stream binder for ABC service
- **[abc-micro-integration](./abc-micro-integration/README.md)** - Micro-Integration bridging the Solace event broker and ABC service

## Build

```bash
./mvnw clean install
```

## Running Integration Tests

**Prerequisites:** Docker must be installed and running. The integration tests use [Testcontainers](https://www.testcontainers.org/) to start the ABC service in a Docker container automatically.

### Build and run all tests (including integration tests)

```bash
./mvnw clean verify
```


Then run integration tests for the desired module:

```bash
# Binder integration tests
./mvnw verify -pl spring-cloud-stream-binder-abc -am

# Test-support integration tests
./mvnw verify -pl abc-test-support -am

# Micro-integration integration tests (builds all dependencies)
./mvnw verify -pl abc-micro-integration -am
```

## Usage

Each module contains its own README with detailed usage instructions.
