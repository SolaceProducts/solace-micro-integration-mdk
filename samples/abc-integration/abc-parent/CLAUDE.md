# abc-parent — Template SDK Dependencies

The `abc-parent` module contains two sub-modules that serve as placeholder dependencies in the abc template project.

## abc-client

Java client library that talks to the target technology's service. Used as a compile dependency in the binder and in test-support. In a new project, replace with the dedicated client SDK maven dependency.

## abc-service

Backend service JAR used to build Docker test images via Jib/Testcontainers. Runs as a containerized mock of the target technology during integration tests. In a new project, it is replaced in most cases with a dedicated testcontainer image for the target technology.