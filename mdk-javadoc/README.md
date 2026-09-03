# MDK API Javadocs

The API documentation for the Solace Micro-Integration Development Kit (MDK) framework is distributed with the binary artifacts and is automatically available in modern IDEs (IntelliJ IDEA, Eclipse, VS Code) when the project is imported as a Maven project. Javadoc JARs can also be downloaded directly from Maven Central.

For general guidance on using the MDK, refer to the [Development Guide](../Micro-Integration%20Framework%20Development%20Guide.pdf) and the [Samples](../samples/README.md) for practical examples.

## Modules

- [Micro-Integration Framework: Platform (`pubsubplus-connector-framework`)](https://mvnrepository.com/artifact/com.solace.connector.core/pubsubplus-connector-framework):
  - The core platform module of the Micro-Integration Framework, which provides the core functionality for Micro-Integration runtime.
  - Use this for implementing the Micro-Integration application itself.
- [Micro-Integration Framework: IO Common (`pubsubplus-connector-io-common`)](https://mvnrepository.com/artifact/com.solace.connector.core/pubsubplus-connector-io-common):
  - Common IO utilities and interfaces used across the MDK.
  - Use this for implementing binders.
- [Micro-Integration Framework: Test Utilities (`pubsubplus-connector-framework-test-support-utilities`)](https://mvnrepository.com/artifact/com.solace.connector.core.test/pubsubplus-connector-framework-test-support-utilities):
  - Utilities for testing Micro-Integrations.
  - Use this for writing unit and integration tests for your Micro-Integration applications.
