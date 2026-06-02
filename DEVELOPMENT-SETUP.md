# Development Setup

## Prerequisites

- Java 17+
- Maven 3.6+
- [AEM as a Cloud Service SDK](https://experienceleague.adobe.com/en/docs/experience-manager-learn/cloud-service/local-development-environment-set-up/aem-runtime) (for integration testing)

## Building

```bash
mvn clean install
```

This compiles all modules, runs unit tests, and installs artifacts to your local
Maven repository.

## Running Tests

```bash
mvn test
```

To run a single test class:

```bash
mvn test -pl core -Dtest=FileUtilsTest
```

