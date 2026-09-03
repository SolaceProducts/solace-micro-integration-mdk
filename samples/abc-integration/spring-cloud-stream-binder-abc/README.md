# Spring Cloud Stream ABC Binder

A Spring Cloud Stream Binder implementation for integrating with the ABC service. The ABC service is a fictitious service created for demonstration purposes only.

## Overview

This module provides a custom Spring Cloud Stream Binder that enables Spring Boot applications to produce and consume messages through the ABC service using Spring Cloud Stream's declarative programming model.

## Features

- **Producer Support**: Send messages to ABC service destinations
- **Consumer Support**: Receive messages from ABC service destinations
- **Spring Boot Auto-configuration**: Automatic setup with minimal configuration
- **Health Checks**: Built-in health indicator integration
- **Extended Properties**: Custom configuration properties for ABC-specific settings

## Usage

### 1. Add Dependency

```xml
<dependency>
    <groupId>com.solace.samples</groupId>
    <artifactId>spring-cloud-stream-binder-abc</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. Configure Application

```yaml
spring:
  cloud:
    stream:
      default-binder: abc
      bindings:
        output:
          destination: my-destination
        input:
          destination: my-destination
          group: my-group
```

## Configuration Properties

Configure the ABC binder connection and behavior:

```yaml
spring:
  cloud:
    stream:
      abc:
        binder:
          # ABC service connection properties
          base-url: http://localhost:8088
          connection-timeout: 30
          read-timeout: 30
        bindings:
          # Channel-specific properties
          input:
            consumer:
              # ABC consumer properties
          output:
            producer:
              # ABC producer properties
```

## Testing

The module includes comprehensive integration tests that:
- Use Testcontainers to spin up ABC service instances
- Test message production and consumption
- Verify binder health and multi-binder scenarios

Tests run automatically as part of the full project build. To run them in isolation:

```bash
./mvnw verify -pl spring-cloud-stream-binder-abc -am
```

## Architecture

- **AbcBinder**: Core binder implementation extending AbstractMessageChannelBinder
- **AbcInboundChannelAdapter**: Handles message consumption from ABC service
- **AbcOutboundMessageHandler**: Handles message production to ABC service
- **AbcBinderProvisioner**: Manages destination provisioning
- **Configuration Classes**: Auto-configuration for Spring Boot integration