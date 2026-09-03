package com.solace.samples.abc.testextension.container.from_binaries;

import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.DockerDaemonImage;
import com.google.cloud.tools.jib.api.Jib;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.Port;
import com.solace.samples.abc.client.AbcClient;
import com.solace.samples.abc.client.AbcClient.AbcClientBuilder;
import com.solace.samples.abc.client.AbcClient.BasicAuthenticationProvider;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Test container that builds a Docker image for the Abc service on the fly using Jib library. This
 * allows us to test against the abc service in a containerized environment without needing to
 * pre-build and push an image to a registry.
 * <pre>
 *   The image will be built from the JAR file produced by the abc-service module.
 *   The JAR path is provided via a system property (set by the Maven Failsafe plugin) or defaults to a relative path.
 *   The image is built using the Eclipse Temurin 17 JRE base image and exposes the abc service port.
 * </pre>
 * Note: under normal circumstances, one would use a vendor provided official docker image or even
 * better an official test container.
 */
public class AbcTestContainer extends
    GenericContainer<AbcTestContainer> {

  private static final Logger LOGGER = LoggerFactory.getLogger(
      AbcTestContainer.class);
  private static final String IMAGE_NAME = "abc-service:latest";
  private static final int SERVICE_PORT = 8088;
  private final String basicAuthUsername = "testuser1";
  private final String basicAuthPassword = "testpassword1";

  static {
    // THIS CODE BLOCK IS EXECUTED ONCE WHEN THE CLASS IS LOADED, BEFORE ANY TEST METHODS ARE RUN.
    // IT IS USED TO BUILD THE DOCKER IMAGE USING JIB BEFORE ANY TESTS ARE EXECUTED.
    // THIS ENSURES THAT THE IMAGE IS READY TO USE WHEN THE TESTS START.
    // THIS IS USUALLY NEWER NEEDED FOR TESTS. IN THIS PARTICULAR CASE IMAGE
    // BUILT FROM THE CURRENT CODEBASE, RATHER THAN USING A PRE-BUILT IMAGE.
    // Get JAR path from Maven environment variable
    try {
      String jarPathString = System.getProperty("JAR_PATH",
          "../abc-parent/abc-service/target/abc-service-1.0.0-SNAPSHOT.jar");
      Path jarFile = Paths.get(jarPathString);
      if (!jarFile.toFile().exists()) {
        throw new ExceptionInInitializerError(
            "JAR file for docker image not found at: " + jarPathString);
      }
      LOGGER.info("Building Docker image with Jib from: {}", jarFile.toAbsolutePath());

      // Build the container image using Jib
      JibContainerBuilder builder = Jib.from("eclipse-temurin:17-jre")
          .addLayer(
              List.of(jarFile),
              AbsoluteUnixPath.get("/app")
          )
          .setEntrypoint("java", "-jar", "/app/" + jarFile.getFileName().toString())
          .addExposedPort(Port.tcp(SERVICE_PORT));

      // Containerize to local Docker daemon
      Containerizer containerizer = Containerizer.to(
          DockerDaemonImage.named(IMAGE_NAME)
      );

      builder.containerize(containerizer);
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
    LOGGER.info("Successfully built image: {}", IMAGE_NAME);
  }

  public AbcTestContainer() {
    super(IMAGE_NAME);

    withExposedPorts(SERVICE_PORT);
    // Choosing appropriate waiting strategy to ensure the service is ready before tests start.
    // In this case, we wait for the health endpoint to return a 200 status code.
    waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200));

    // TODO: set-up authentication credentials as well in a specific way the service requires it,
    //  for example by setting specific environment variables
    // or passing them as command line arguments inside the container, in this example we will just use basic authentication.
    // Example how to setup variable for the container:
    // withEnv("BASIC_AUTH_USERNAME", basicAuthUsername) and
    // withEnv("BASIC_AUTH_PASSWORD", basicAuthPassword)
    withEnv("BASIC_AUTH_USERNAME", basicAuthUsername);
    withEnv("BASIC_AUTH_PASSWORD", basicAuthPassword);

    // COMMENT FOR AI: In this example, the client is created in the constructor after configuring the waiting strategy.
    // This ensures that the client is ready to use when injected into tests. Depending on the service and client implementation,
    // you might need to perform additional steps to establish a connection or initialize the client after the container is ready.

  }

  @Override
  public void start() {
    super.start();
  }


  @Override
  public void stop() {
    super.stop();
  }

  public String getBasicAuthUsername() {
    return basicAuthUsername;
  }

  public String getBasicAuthPassword() {
    return basicAuthPassword;
  }

  public String getConnectionUrl() {
    // This particular service is http based
    return "http://%s:%d".formatted(getHost(), getMappedPort(SERVICE_PORT));
  }


}
