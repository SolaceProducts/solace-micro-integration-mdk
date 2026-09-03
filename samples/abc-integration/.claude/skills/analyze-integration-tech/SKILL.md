---
name: analyze-integration-tech
description: Analyze backend technologies, SaaS services, and cloud data services from a Java development perspective. Generates markdown reports covering Java client libraries, Maven dependencies, Apache Camel integration, testing, security, and configuration.
tools_required:
  - WebFetch
  - Write
  - Read
  - Glob
---

# Integration Technology Analysis

Generate comprehensive, fact-checked analysis reports for backend technologies, SaaS services, and cloud services that hold data. All reports prioritize the Java ecosystem: Maven dependencies, Java client libraries, Apache Camel integration, and containerized testing. When no Java SDK exists, OpenAPI specifications are included for REST API integration.

## Scope

**Use for:**
- Message brokers that store messages (Apache Kafka, Apache ActiveMQ, Amazon SQS)
- Cloud messaging services (Amazon SNS, Google Pub/Sub, Azure Service Bus)
- Event streaming platforms that persist data (Apache Pulsar, Amazon Kinesis, Azure Event Hubs)
- Databases with integration capabilities (Redis, MongoDB, CouchDB, InfluxDB)
- Search engines and document stores (Elasticsearch, Solr, Amazon CloudSearch)
- Caching services (Memcached, Amazon ElastiCache, Azure Cache for Redis)
- File storage services (Amazon S3, Azure Blob Storage, Google Cloud Storage)
- Enterprise messaging systems (IBM MQ, Microsoft MSMQ)
- SaaS and ERP systems or their components (Salesforce, SAP, Oracle ERP Cloud, Microsoft Dynamics, ServiceNow)

**Don't use for:**
- Integration frameworks that don't hold data (Apache Camel, Spring Cloud Stream, MuleSoft ESB)
- API gateways and service meshes (Kong, Istio, Envoy, AWS API Gateway)
- Load balancers and proxies (NGINX, HAProxy, AWS ALB)
- Programming frameworks and libraries (Spring Framework, .NET Core)
- Build tools and CI/CD systems (Maven, Jenkins, GitHub Actions)
- Development tools and IDEs (IntelliJ, Visual Studio Code)
- Generic technology categories (ESB, Message Broker, Database) - use specific products instead

## Quick Start

```bash
/analyze-integration-tech "Amazon SNS"
/analyze-integration-tech "Apache Kafka"
/analyze-integration-tech "Elasticsearch"
/analyze-integration-tech "Redis"
/analyze-integration-tech "Amazon S3"
```

**Output**: Creates `overview-{technology-name}.md` in the current directory.

### Tips
- Use specific product names, not generic categories: `"Apache Kafka"` not `"message broker"`
- Include vendor names for cloud services: `"Amazon SNS"` not `"SNS"`, `"Google Pub/Sub"` not `"Pub/Sub"`
- Technology names are case-insensitive
- Focus on one technology per analysis for comprehensive coverage

### Execution Guidelines

**Proceed with analysis when:**
- Technology is a well-known, mainstream product (Apache Kafka, Redis, MongoDB)
- Product name is unambiguous and clearly identifiable

**Request clarification when:**
- Technology name is generic or unclear ("ESB", "message broker")
- Multiple products could match the name ("MQ" could mean IBM MQ or Amazon MQ)
- Technology appears outdated or deprecated
- Vendor/product relationship is ambiguous

**Stop execution when:**
- Technology cannot be definitively identified
- No official documentation or reliable sources found
- Technology appears to be fictional or non-existent
- Request contains only generic category terms

## Report Structure

Each generated report covers **7 essential sections**:

### 1. Technology Summary

**Single focused paragraph**: Concise technology classification, core purpose, and primary data model in 2-3 sentences maximum.

**Key Use Cases** (bulleted list only):
- 4-6 specific, technical use cases relevant to Java integration
- Focus on actual implementation scenarios, not marketing concepts
- Each bullet should be actionable/measurable

**Technology Classification**: One-line technical classification (e.g., "Graph Database with ACID compliance and Cypher query language support")

No ecosystem positioning, integration discussions, or forward-looking statements in this section. Those belong in Section 3.

### 2. Data Storage & Retrieval
- Supported formats (JSON, XML, Binary, Avro, Protobuf)
- Data organization model with simple visual representation (ASCII diagrams)
- Ordering guarantees (FIFO, partition-based, none)

### 3. Java Client Integration & APIs

#### 3.1 Java Client APIs
- **Maven Dependencies**: Latest versions looked up from Maven Central during research (never hardcode versions)
- **SDK Class Documentation**: Major client classes with explanations of their purpose. Include major exception classes with a brief description of when each exception occurs (e.g., authentication failure, connection timeout, resource not found, query syntax error). Cover only the primary exceptions a developer will encounter during typical operations — do not exhaustively list every exception subclass.
- **SDK Class Diagram**: ASCII diagram showing relationships between the major SDK classes documented above. Use `──creates──▶`, `──returns──▶`, `──contains──▶`, `──extends──▶` arrows to show how classes relate. Keep it to the core workflow classes (factory, client, session/connection, result, record) — do not include exception classes or configuration classes. Limit to 6-10 classes maximum.
- **Connection Management**: Client creation, connection establishment, and proper cleanup
- **Data Container Operations**: Creating/managing databases, tables, buckets, topics, queues, indexes, etc.
- **Data Operations**: Storing/retrieving records, sending/receiving messages, uploading/downloading objects
- **Complete Cycle Example**: Self-contained example showing connect -> create container -> data operation -> close
- **Batching Support**: Document actual SDK batching capabilities (if any) based on official documentation
- **Acknowledgements & Negative Acknowledgements**: Briefly state whether the SDK supports publish confirmations (acks) and/or negative acknowledgements (nacks) for data operations. If supported, describe the mechanism in 1-2 sentences (e.g., callback-based, future-based, synchronous return code) and include ack/nack handling in the Complete Cycle Example. If the technology has no concept of acks/nacks (e.g., synchronous databases where success/failure is implicit in the method return or exception), state that explicitly: "Acks/nacks: not applicable — operations are confirmed by successful return or indicated by exception."
- **Code Examples**: Fully commented Java implementation patterns with error handling

#### 3.2 Apache Camel Component (if available)
- Available Camel components with Maven artifacts and official documentation links
- Component capabilities and limitations
- URI patterns and configuration examples
- **Maven Artifact**: groupId, artifactId for the Camel component (look up latest version from Maven Central)
- **Official Documentation**: Link to the component page on camel.apache.org (use the latest stable version URL)
- **Connection Configuration Properties**: Authentication, endpoints, connection pooling
- **Data Publishing Operations**: Producer patterns, message transformation, batch processing
- **Data Retrieval Operations**: Consumer patterns, polling strategies, message processing
- **Data Container Creation Support**: Whether component can create/manage containers
- **Advanced Features**: Transactions, error handling, monitoring capabilities

#### 3.3 OpenAPI Specification (ONLY when NO official Java SDK exists)
- **OpenAPI Specification**: Link to official OpenAPI spec for REST API integration
- **API Endpoints**: Key endpoints for data container management, data operations, and administration
- **Authentication Methods**: Supported auth mechanisms for API access
- **API Usage Examples**: Sample HTTP requests for common operations

#### 3.4 Asynchronous Processing
- Async operation support in the client library (CompletableFuture, reactive streams, callbacks)
- Message delivery guarantees (at-most-once, at-least-once, exactly-once)
- In-order and out-of-order handling capabilities
- Async vs sync API trade-offs for the specific technology

#### 3.5 Spring Cloud Stream Binder (if available)

Search for a Spring Cloud Stream binder that integrates with the target technology. If no binder exists, state: "No Spring Cloud Stream binder available for {technology}." and skip the rest of this subsection.

When a binder is found **and** the project is actively maintained (not archived), emphasize clearly:

> **An existing Spring Cloud Stream binder is available for {technology}. No custom binder implementation is required.** The binder can be added to any Spring Cloud Stream application as a Maven dependency. GitHub repository: {url}

When a binder exists, document the following:

- **Project Status**: Whether the binder repository is actively maintained or archived. Include the date of the last release or last commit if the repository appears inactive. If archived, state clearly: "This binder is archived and no longer maintained — a custom binder may be required."
- **Documentation & Source Links**: Official documentation URL and GitHub repository URL. The GitHub URL is critical — it must always be included so downstream skills and developers can reference the binder source.
- **Maven Dependency Management**: Complete Maven BOM and dependency declarations needed to add the binder to a Spring Boot application. Include both BOM import in `<dependencyManagement>` and the runtime dependency. Look up latest versions from Maven Central.
  ```xml
  <!-- BOM import (if applicable) -->
  <dependencyManagement>
      <dependencies>
          <dependency>
              <groupId>...</groupId>
              <artifactId>...-bom</artifactId>
              <version>{latest-from-maven-central}</version>
              <type>pom</type>
              <scope>import</scope>
          </dependency>
      </dependencies>
  </dependencyManagement>

  <!-- Runtime dependency -->
  <dependency>
      <groupId>...</groupId>
      <artifactId>...-spring-cloud-stream-binder</artifactId>
  </dependency>
  ```

- **Binder Configuration Properties**: Document all key binder-specific configuration properties in YAML format (`application.yml`). Organize into logical groups:
  - **Connection properties**: Host, port, URL, virtual host, cluster addresses
  - **Authentication properties**: Username, password, API key, token, SSL/TLS keystore and truststore paths
  - **Producer properties**: Destination/topic/queue, partitioning, header mapping
  - **Consumer properties**: Group, concurrency, destination/topic/queue, prefetch, acknowledgement mode
  - Use real property names verified from official documentation. Never fabricate property names.

  ```yaml
  # Example structure — replace with real binder properties
  spring:
    cloud:
      stream:
        binders:
          {technology}:
            type: {binder-type}
            environment:
              spring:
                {technology}:
                  host: localhost
                  port: 5672
                  username: guest
                  password: guest
        bindings:
          input-in-0:
            binder: {technology}
            destination: my-destination
            group: my-group
          output-out-0:
            binder: {technology}
            destination: my-destination
  ```

- **Authentication Strategies**: List all authentication and connection security methods supported by the binder. For each method, provide the corresponding YAML configuration snippet:
  - Plain credentials (username/password)
  - API key or token-based authentication
  - SSL/TLS with client certificates (mutual TLS)
  - OAuth2 / SASL mechanisms (if supported)
  - Any vendor-specific authentication (e.g., cloud provider IAM)

- **Batching Support**: State whether the binder supports batch publishing or batch consumption. If supported, document the relevant configuration properties in YAML format (e.g., batch mode, batch size, batch timeout). If not supported, state: "Batching is not supported by this binder."

- **Concurrency & Asynchronous Processing**: Document whether the binder supports:
  - **Concurrent consumers**: Multiple threads consuming from the same binding (e.g., `spring.cloud.stream.bindings.<name>.consumer.concurrency`)
  - **Asynchronous publishing**: Non-blocking message production (e.g., reactive or async publish confirmations)
  - **Asynchronous consumption**: Non-blocking message receipt (e.g., reactive binder variant)
  - Include relevant YAML configuration for concurrency settings.

- **`org.springframework.integration.core.Pausable` Support**: State whether the binder's consumer endpoint implements the `Pausable` interface from Spring Integration. If supported, binding pause/resume operations are available at runtime. If unknown or not documented, state: "Pausable support could not be confirmed from available documentation."

### 4. Administrative Data Container Operations
- **Administrative Tools**: Web consoles, CLI tools, management interfaces
- **Infrastructure Setup**: Creating databases, topics, queues, buckets, indexes via admin tools
- **Resource Configuration**: Setting quotas, partitions, replication factors, storage classes
- **Operational Commands**: Administrative scripts and commands for container lifecycle
- **Prerequisites**: Required admin privileges and setup procedures

### 5. Testing & Development

#### 5.1 TestContainer Integration (when available)
- Always check if official TestContainer support exists first
- If NO official TestContainer exists, state clearly: "No official TestContainer support available"
- When TestContainer exists: provide Maven dependencies and integration examples
- Use real dockerized backends for testing, NOT mocks
- Integration test examples using actual database instances
- JUnit 5 integration with TestContainer lifecycle management

#### 5.2 Official Docker Images for Local Development
- Official Docker images with specific tags and versions
- Docker registry locations (Docker Hub, AWS ECR, Google Container Registry, Azure Container Registry)
- Image sizes and base operating systems
- Available variants (Alpine, Ubuntu, slim, etc.)
- Basic Docker run commands for development setup

#### 5.3 Docker Compose Configuration Examples
- Only single-instance development configurations for local development
- Volume mounts, networking, environment variables, and health checks
- Do not include unit tests, mock examples, load/performance testing, or JMeter configurations

#### 5.4 Local Testing Alternatives (ONLY when NO TestContainer AND NO official Docker image exists)
Skip this subsection entirely if TestContainers or Docker images are available. Only include when both are absent. Research in this order and document the first viable option found:
1. **Embedded servers** — in-process instances bundled as a Maven dependency (e.g., H2 for SQL databases, embedded Elasticsearch)
2. **Vendor-provided test helpers** — official mock/stub libraries or test SDKs from the vendor (e.g., DynamoDB Local JAR, Pub/Sub emulator)
3. **Local daemon alternatives** — downloadable standalone binaries that run natively without Docker

For each alternative found, provide: Maven dependency or download link, and a brief usage example. If no local alternative exists at all, state: "No local testing alternative available. Integration testing requires a live service instance — use a test/trial account from the vendor."

### 6. Security & Authentication mechanism
- **Basic Authentication Setup (preferable if available)** (username/password or api key-based)
  - Simple connection string configuration
  - Basic Java client authentication examples
   
- **Advanced Authentication Options** (brief mention only if Basic Authentication is available, detailed otherwise)
  - Advanced available authentication methods (OAuth, LDAP, certificates, etc.) with reference to official documentation
  - When basic Authentication is NOT available, provide details on the primary authentication method and how to configure it in the Java client
  - Sort advanced methods by complexity and typical use case from simple to complex (e.g., OAuth with client credentials flow is more complex than basic auth, but less complex than mutual TLS)
  - Choose and document simplest available advanced authentication method for the Complete Cycle Example in Section 3, if basic auth is not an option

- **Required Roles for primary authentication**
    - Minimum permissions needed for read operations
    - Minimum permissions needed for write operations
    - Administrative privileges for database/container creation

Focus on a simplest available authentication pattern for development and simple production setups.

### 7. Configuration Properties

**Only document REAL configuration properties that actually exist in official documentation. Never create made-up or generic properties.**

#### Configuration Property Categories (document separately and clearly label each):

**7.1 Java SDK/Driver Configuration**
- Document if the Java SDK uses programmatic configuration (Config builders) vs properties files
- Include only actual configuration methods/classes from official SDK documentation
- If no properties-based config exists, state: "Java SDK uses programmatic configuration only"

**7.2 Spring Boot Framework Integration Properties (if applicable)**
- Document actual Spring Boot properties (spring.{technology}.*)
- Verify properties exist in Spring Boot documentation or Spring Data modules
- Include autoconfiguration property names and default values

**7.3 Apache Camel Component Properties (if component exists)**
- Document real Camel component properties (camel.component.{technology}.*)
- Verify from official Apache Camel component documentation
- Include URI parameters and component configuration options

**7.4 Technology Server/Service Configuration (separate from client config)**
- Document server configuration files (e.g. elasticsearch.yml)
- Clearly label as "Server Configuration" not "SDK Configuration"
- Use actual server property syntax from official documentation

#### Verification Requirements:
- **Source Verification**: Every property must be traceable to official documentation
- **Category Isolation**: Never mix SDK, framework, server, and component properties
- **Real vs Generic**: Use actual property names, not template placeholders
- **Context Clarity**: Clearly state which configuration file or method each property belongs to

## Implementation Instructions

### Phase 1: Setup and Initialization
1. **Validate Technology Name**
   - Confirm technology is well-known and clearly identifiable
   - Sanitize name for filename generation (lowercase, hyphens for spaces/special chars)
   - Stop if technology is generic or fictional

2. **Check for Existing File**
   - Use Glob to check if `overview-{technology-name}.md` already exists
   - If it exists, overwrite it with the new analysis

3. **Initialize Output File**
   - Create the file with the title: `# [Technology] Integration Technology Analysis`

### Phase 2: Section-by-Section Generation with Validation

For EACH of the 7 sections, execute this validation loop with a maximum of **5 correction attempts** per section:

1. **Research and Draft**: Use WebFetch to find official documentation. Use WebFetch to extract details from primary sources. Draft complete section content.
2. **Validate Against Sources**: Verify every claim against official documentation. Check version numbers against Maven Central and official releases. Confirm configuration properties exist in official docs. Ensure code examples use correct API syntax.
3. **Correct Issues**: If any claim cannot be verified, either find an authoritative source or remove the claim. Perform additional WebFetch as needed. Never include unverified information. Return to step 2 if corrections were made.
4. **Halt on repeated failure**: If after 5 correction attempts the section still contains unresolved issues, **stop generation entirely**. Do not write the section. Instead, report the failure to the user with: the section number and name, which specific claims could not be verified, which sources were attempted, and the nature of the verification failure (e.g., source unavailable, conflicting information, no documentation found).
5. **Write Section to File**: Only reached when validation passes. Read current file content, append the validated section using Write tool.
6. **Proceed**: Move to the next section.

Never proceed to the next section until the current section passes validation. Never write unverified content to the output file.

### Phase 3: Final Assembly
1. Review entire document for cross-section consistency
2. Verify all 7 sections are present and complete
3. Compile and append the sources bibliography at the end
4. Ensure proper markdown formatting throughout

### Handling Insufficient Information

When WebFetch fails or returns insufficient results for a section:
- State clearly what could not be verified (e.g., "Official TestContainer support could not be confirmed")
- Do not fabricate information to fill gaps
- Provide what is known from available sources and note the limitations
- For technologies with very limited documentation, produce a shorter but accurate report rather than padding with assumptions

### Java Code Example Requirements

All Java code examples in the report must:
1. **Include complete import statements** at the top of every snippet
2. **Organize imports** in standard order: java/javax, third-party libraries, technology-specific
3. **Use specific class imports**, not wildcards
4. **Include static imports** when necessary (e.g., static assertions in tests)

Only include framework-specific imports (Spring, etc.) when that framework is being demonstrated.

### Implementation Rules

- Never include unverified configuration properties
- Never assume SDK capabilities not found in documentation
- Never use outdated version numbers or deprecated features
- Always look up latest versions from Maven Central during research
- Verify every technical claim with official sources
- Correct all identified issues before writing to file
- **Naming convention:** In all generated report content, use **"Solace event broker"** — never "Solace PubSub+" or "PubSub+"

## Research Methodology

The skill performs fact-checked research using prioritized sources:

1. **Official vendor documentation** - Primary authoritative source (especially for actual SDK capabilities)
2. **SDK API documentation** - For verifying actual method signatures and batching support
3. **Identity and Access Management (IAM) documentation** - For required roles and permissions
4. **Apache Camel component docs** - For integration patterns
5. **Spring Cloud Stream binder docs and GitHub repos** - For binder properties, authentication, and feature support
6. **Maven Central Repository** - Latest dependency versions
7. **Docker Hub official images** - Container testing
8. **GitHub official repos** - Code examples and quickstarts

### Configuration Properties Research

For Section 7 configuration properties:
- **Java SDK Config**: Verify from official Java SDK documentation or API docs
- **Spring Boot Properties**: Verify from Spring Boot reference documentation or Spring Data modules
- **Apache Camel Properties**: Verify from official Camel component documentation
- **Server Configuration**: Verify from server/service configuration reference docs
- **Cross-Reference**: Check property names in multiple official sources when possible
- **Documentation Links**: Include direct links to documentation sections where properties are defined
- If a property cannot be found in official documentation, do not include it

## Example Output Format

The examples below show the expected structure for key report sections. When generating actual reports, replace all placeholders with real technology-specific APIs, methods, and configuration properties verified from official documentation.

### Java Client Integration (Section 3 format)

**Maven Dependencies:**
```xml
<!-- Primary Java client dependency - look up latest version from Maven Central -->
<dependency>
    <groupId>com.example</groupId>
    <artifactId>technology-java-client</artifactId>
    <version>{latest-from-maven-central}</version>
</dependency>
```

**SDK Class Diagram:**
```
GraphDatabase ──creates──▶ Driver ──creates──▶ Session ──creates──▶ Transaction
                                                  │                     │
                                                  └──returns──▶ Result ◀┘
                                                                  │
                                                           ──contains──▶ Record ──contains──▶ Value
```

**Complete Cycle Example:**
```java
import com.example.technology.TechnologyClient;
import com.example.technology.UserRecord;

public class TechnologyExample {
    public static void main(String[] args) {
        TechnologyClient client = TechnologyClient.builder()
            .endpoint("localhost:8080")
            .credentials("user", "pass")
            .build();

        try {
            client.createDatabase("myapp");
            client.createTable("users");

            UserRecord user = new UserRecord("john", "john@example.com");
            client.insert("users", user);

            UserRecord retrieved = client.get("users", "john");
            System.out.println("Retrieved: " + retrieved.getEmail());
        } finally {
            client.close();
        }
    }
}
```

### Configuration Properties (Section 7 format)

Each category must be clearly separated with source attribution:

```java
// CATEGORY: Java SDK (Programmatic Configuration)
// SOURCE: https://kafka.apache.org/documentation/#producerconfigs
Properties props = new Properties();
props.put("bootstrap.servers", "localhost:9092");
props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
```

```properties
# CATEGORY: Spring Boot Auto-Configuration
# SOURCE: Spring Boot Reference Documentation
# FILE: application.properties
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
```

```properties
# CATEGORY: Apache Camel Component Configuration
# SOURCE: https://camel.apache.org/components/latest/kafka-component.html
# FILE: application.properties
camel.component.kafka.brokers=localhost:9092
camel.component.kafka.security-protocol=PLAINTEXT
```

```properties
# CATEGORY: Server Configuration
# SOURCE: https://kafka.apache.org/documentation/#brokerconfigs
# FILE: server.properties
num.network.threads=3
log.retention.hours=168
```

## Validation Checklist

Apply this checklist to each section before writing it to the output file:

**Source and Accuracy:**
- Every technical claim traceable to official vendor documentation
- All version numbers verified against Maven Central or official releases
- Configuration properties confirmed in official documentation
- Code examples validated against current API documentation
- No assumptions about undocumented SDK capabilities
- No generic placeholder examples or template syntax in the final report

**Code Quality:**
- All Java examples include complete import statements
- Examples use correct API syntax and patterns
- All URLs accessible and pointing to current documentation

**Completeness:**
- All required subsections present for the section
- No contradictions with previously written sections
- Configuration categories properly separated (SDK vs Spring Boot vs Camel vs Server)

**Configuration Properties (Section 7 specific):**
- No fictional property names - every property verified in official docs
- No mixing of SDK, server, framework, and component properties in the same subsection
- No template placeholders like `{technology}.generic.property=value`
- Each property specifies which file or method it belongs to
- Documentation source URL provided for each category

## Output File Naming

Files are created with sanitized names for cross-platform compatibility:
- `"Amazon SNS"` -> `overview-amazon-sns.md`
- `"Apache Kafka"` -> `overview-apache-kafka.md`
- `"Spring Cloud Stream"` -> `overview-spring-cloud-stream.md`

Invalid characters are replaced with hyphens, and names are lowercased. If a file with the same name already exists, it will be overwritten with the new analysis.
