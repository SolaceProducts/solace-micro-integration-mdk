# ABC Micro-Integration

A Solace micro-integration that bridges data between the Solace event broker and the ABC service. The ABC service is a fictitious service created for demonstration purposes only.

## Overview

This micro-integration application uses the Solace Micro-Integration framework to enable bidirectional data flow between the Solace event broker and the ABC service. It leverages Spring Cloud Stream bindings to consume events from Solace topic/queue and forward them to ABC destinations, or vice versa.

## Features

- **Bidirectional Integration**: Bridge events between the Solace event broker and ABC service
- **Multiple Workflow Support**: Pre-configured with 20 input/output binding pairs
- **Message Transformation**: Built-in support for message interceptors and transformations
- **Health Monitoring**: Actuator endpoints for health checks and metrics
- **Configuration Validation**: Validates ABC-specific configuration requirements

## Architecture

The integration uses:
- **Spring Cloud Stream**: For declarative binding configuration
- **Solace Binder**: For Solace event broker connectivity
- **ABC Binder**: Custom binder for ABC service integration
- **Connector Framework**: Solace micro-integration platform core

## Configuration

### Basic Setup

The application pre-configures 20 workflow bindings (0-19), each linking an input binding to an output binding. Configure your specific workflows in the deployment configuration.

### Sample Workflow Configuration

```yaml
solace:
  connector:
    workflows:
      0:
        input-bindings:
          - input-0
        output-bindings:
          - output-0

spring:
  cloud:
    stream:
      bindings:
        input-0:
          destination: "source/topic"
          binder: solace
          group: my-consumer-group
        output-0:
          destination: "target-queue"
          binder: abc
```

### Payload Transformation

Workflows support built-in payload transformation between source and target formats. Enable transforms on a workflow and define expressions to map fields:

```yaml
solace:
  connector:
    workflows:
      0:
        transform:
          enabled: true
          source-payload:
            content-type: "application/xml"
          target-payload:
            content-type: "application/json"
          expressions:
            - transform: "target['payload']['static'] = 'new-payload-value'"
            - transform: "target['payload']['product_name'] = source['payload']['products']['item'][0]['name']"
```

This example converts an XML source payload to JSON and maps specific fields using transform expressions. Expressions use `source['payload']` to read from the inbound message and `target['payload']` to write to the outbound message.

## Deployment

1. **Build the application** (from the project root):
   ```bash
   ./mvnw clean package -pl abc-micro-integration -am
   ```

2. **Configure environment-specific properties**

3. **Run the application**:
   ```bash
   java -jar target/abc-micro-integration-1.0.0-SNAPSHOT.jar
   ```

## Testing

Tests run automatically as part of the full project build. To run them in isolation:

```bash
./mvnw verify -pl abc-micro-integration -am
```

## Management

The application exposes management endpoints on port 8090:
- Health: `/actuator/health`
- Metrics: `/actuator/metrics`
- Workflows: `/actuator/workflows`
- Bindings: `/actuator/bindings`

## Key Components

- **MicroIntegrationApplication**: Main application class with binding factories
- **AbcConsumerBindingCapabilitiesFactory**: Defines ABC consumer capabilities
- **AbcProducerBindingCapabilitiesFactory**: Defines ABC producer capabilities
- **Message Interceptors**: Custom logic for message transformation