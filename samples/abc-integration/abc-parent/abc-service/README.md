# ABC Service

A fictitious ABC service implemented as a Spring Boot application for demonstration purposes only. This service provides REST APIs for message publishing and receiving from known destinations. It is built automatically as part of the multi-module project and is used via Testcontainers during integration tests.

## Features

- Create and list destinations (queues)
- Queue messages to specific destinations
- Poll for messages from destinations
- Acknowledge messages after processing
- List all messages in a destination
- Delete all messages from a destination
- In-memory implementation using Java BlockingQueue
- Docker containerization with Jib

## API Documentation

When the service is running, OpenAPI documentation is available at:

- Swagger UI: http://localhost:8088/swagger-ui.html
- OpenAPI JSON: http://localhost:8088/api-docs

For quick API testing, see the IntelliJ HTTP Client file at [abc-service.http](../abc-service.http).
