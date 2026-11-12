package filemanager;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public final class TestContainersManager {

  private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:17.4");
  private static PostgreSQLContainer<?> postgreSQLContainer;

  static {
    // Docker 29 answers GET /v1.32/info with 400. Testcontainers 1.21 still sends 1.32
    // unless the docker-java API version is set before the client is created.
    if (System.getenv("DOCKER_API_VERSION") == null && System.getProperty("api.version") == null) {
      System.setProperty("api.version", "1.44");
    }
  }

  private TestContainersManager() {}

  public static synchronized PostgreSQLContainer<?> getPostgreSQLContainer() {
    if (postgreSQLContainer == null) {
      postgreSQLContainer =
          new PostgreSQLContainer<>(POSTGRES_IMAGE)
              .withDatabaseName("file_manager")
              .withUsername("file_manager_user")
              .withPassword("file_manager_password");
    }
    return postgreSQLContainer;
  }

  public static synchronized void startPostgreSQLContainer() {
    PostgreSQLContainer<?> container = getPostgreSQLContainer();
    if (!container.isRunning()) {
      container.start();
    }
  }
}
