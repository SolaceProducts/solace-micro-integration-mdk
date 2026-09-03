# ABC Client

Java client library for interacting with the ABC service. The ABC service is a fictitious service created for demonstration purposes only. Provides sync and async APIs for publishing messages, polling, acknowledging, rejecting messages, and managing destinations. Built on Spring `RestTemplate` and is thread-safe.

## Usage

```java
AbcClient client = AbcClient.builder()
    .withBaseUrl("http://localhost:8088")
    .withConnectionTimeout(30)
    .withReadTimeout(30)
    .build();
```
