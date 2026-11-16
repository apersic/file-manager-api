package filemanager.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import filemanager.TestContainersManager;
import filemanager.dto.NodeDetailResponse;
import filemanager.dto.NodePageResponse;
import filemanager.dto.NodeResponse;
import filemanager.service.CursorCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class NodeApiE2eTest {

  @LocalServerPort private int port;

  @Autowired private JdbcTemplate jdbc;

  private WebTestClient client;

  @DynamicPropertySource
  static void registerPgProperties(DynamicPropertyRegistry registry) {
    TestContainersManager.startPostgreSQLContainer();
    var container = TestContainersManager.getPostgreSQLContainer();
    registry.add("spring.datasource.url", container::getJdbcUrl);
    registry.add("spring.datasource.username", container::getUsername);
    registry.add("spring.datasource.password", container::getPassword);
  }

  @BeforeEach
  void setUp() {
    client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
  }

  @Test
  void rootIsTheSeededHomeFolder() {
    client
        .get()
        .uri("/api/v1/nodes/root")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.name")
        .isEqualTo("Home")
        .jsonPath("$.kind")
        .isEqualTo("FOLDER")
        .jsonPath("$.parentId")
        .value(org.hamcrest.Matchers.nullValue())
        .jsonPath("$.ancestors.length()")
        .isEqualTo(0);
  }

  @Test
  void createFolderAndFileThenPageTheChildren() {
    long rootId = rootId();
    long folderId = create(rootId, "FOLDER", "Designs " + UUID.randomUUID());
    create(folderId, "FILE", "c.txt");
    create(folderId, "FILE", "a.txt");
    create(folderId, "FILE", "b.txt");

    client
        .get()
        .uri("/api/v1/nodes/{id}", folderId)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.ancestors[0].id")
        .isEqualTo((int) rootId)
        .jsonPath("$.ancestors[0].name")
        .isEqualTo("Home")
        .jsonPath("$.ancestors.length()")
        .isEqualTo(1);

    NodePageResponse first =
        client
            .get()
            .uri("/api/v1/nodes?parentId={parentId}&limit=2", folderId)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(NodePageResponse.class)
            .returnResult()
            .getResponseBody();
    assertThat(first.items()).extracting(NodeResponse::name).containsExactly("a.txt", "b.txt");
    assertThat(first.nextCursor()).isNotBlank();

    NodePageResponse second =
        client
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path("/api/v1/nodes")
                        .queryParam("parentId", folderId)
                        .queryParam("cursor", first.nextCursor())
                        .queryParam("limit", 2)
                        .build())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(NodePageResponse.class)
            .returnResult()
            .getResponseBody();
    assertThat(second.items()).extracting(NodeResponse::name).containsExactly("c.txt");
    assertThat(second.nextCursor()).isNull();
  }

  @Test
  void duplicateSiblingNameIsConflictAndCaseDoesNotMakeASecondRow() {
    long folderId = create(rootId(), "FOLDER", "dup " + UUID.randomUUID());
    create(folderId, "FILE", "Report");

    client
        .post()
        .uri("/api/v1/nodes")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(Map.of("parentId", folderId, "kind", "FILE", "name", "report"))
        .exchange()
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.message")
        .isEqualTo("A file or folder with this name already exists")
        .jsonPath("$.status")
        .isEqualTo(409);

    long otherFolder = create(rootId(), "FOLDER", "other " + UUID.randomUUID());
    create(otherFolder, "FILE", "Report");
  }

  @Test
  void fileCannotBeAParent() {
    long folderId = create(rootId(), "FOLDER", "files " + UUID.randomUUID());
    long fileId = create(folderId, "FILE", "notes.txt");

    client
        .post()
        .uri("/api/v1/nodes")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(Map.of("parentId", fileId, "kind", "FILE", "name", "child"))
        .exchange()
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.message")
        .isEqualTo("Parent is not a folder");

    client
        .get()
        .uri("/api/v1/nodes?parentId={parentId}", fileId)
        .exchange()
        .expectStatus()
        .isEqualTo(409);

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into node (parent_id, kind, name) values (?, 'FOLDER', ?)",
                    fileId,
                    "sql-child"))
        .hasMessageContaining("fk_node_parent");
  }

  @Test
  void unknownIdsAreNotFound() {
    client
        .get()
        .uri("/api/v1/nodes/{id}", 9_000_000_000L)
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.message")
        .isEqualTo("Node was not found");

    client
        .post()
        .uri("/api/v1/nodes")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(Map.of("parentId", 9_000_000_000L, "kind", "FOLDER", "name", "Missing"))
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.message")
        .isEqualTo("Folder was not found");
  }

  @Test
  void tamperedCursorIsRejectedAndACraftedPositionDoesNotFail() {
    long folderId = create(rootId(), "FOLDER", "cursor " + UUID.randomUUID());
    create(folderId, "FILE", "a");
    create(folderId, "FILE", "m");
    create(folderId, "FILE", "z");

    client
        .get()
        .uri(
            uriBuilder ->
                uriBuilder
                    .path("/api/v1/nodes")
                    .queryParam("parentId", folderId)
                    .queryParam("cursor", "not-a-cursor")
                    .build())
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.message")
        .isEqualTo("Invalid cursor");

    String crafted = CursorCodec.encode("m", Long.MAX_VALUE);
    client
        .get()
        .uri(
            uriBuilder ->
                uriBuilder
                    .path("/api/v1/nodes")
                    .queryParam("parentId", folderId)
                    .queryParam("cursor", crafted)
                    .build())
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.items.length()")
        .isEqualTo(1)
        .jsonPath("$.items[0].name")
        .isEqualTo("z");
  }

  @Test
  void pageCapAndDefaultPageSize() {
    client
        .get()
        .uri("/api/v1/nodes?parentId={parentId}&limit=101", rootId())
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.limit")
        .isEqualTo("Limit must be between 1 and 100");

    long folderId = create(rootId(), "FOLDER", "page " + UUID.randomUUID());
    for (int i = 0; i < 51; i++) {
      create(folderId, "FILE", "n" + String.format("%02d", i));
    }

    client
        .get()
        .uri("/api/v1/nodes?parentId={parentId}", folderId)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.items.length()")
        .isEqualTo(50)
        .jsonPath("$.nextCursor")
        .exists()
        .jsonPath("$.items[0].name")
        .isEqualTo("n00")
        .jsonPath("$.items[49].name")
        .isEqualTo("n49");
  }

  @Test
  void concurrentDuplicateCreatesLeaveOneRow() throws Exception {
    long folderId = create(rootId(), "FOLDER", "race " + UUID.randomUUID());
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    List<Integer> statuses = new ArrayList<>();
    List<Integer> safeStatuses = java.util.Collections.synchronizedList(statuses);
    try {
      for (int i = 0; i < 2; i++) {
        pool.submit(
            () -> {
              ready.countDown();
              try {
                start.await(10, TimeUnit.SECONDS);
              } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
              }
              int status =
                  client
                      .post()
                      .uri("/api/v1/nodes")
                      .contentType(MediaType.APPLICATION_JSON)
                      .bodyValue(Map.of("parentId", folderId, "kind", "FILE", "name", "same"))
                      .exchange()
                      .returnResult(String.class)
                      .getStatus()
                      .value();
              safeStatuses.add(status);
            });
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
    } finally {
      pool.shutdown();
    }
    assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
    assertThat(safeStatuses).containsExactlyInAnyOrder(201, 409);

    Integer rows =
        jdbc.queryForObject(
            "select count(*) from node where parent_id = ? and name = 'same'",
            Integer.class,
            folderId);
    assertThat(rows).isEqualTo(1);
  }

  @Test
  void databaseRejectsAParentChange() {
    long rootId = rootId();
    long folderId = create(rootId, "FOLDER", "move " + UUID.randomUUID());
    long otherId = create(rootId, "FOLDER", "dest " + UUID.randomUUID());

    assertThatThrownBy(
            () -> jdbc.update("update node set parent_id = ? where id = ?", otherId, folderId))
        .hasMessageContaining("parent_id cannot be updated");
  }

  private long rootId() {
    NodeDetailResponse root =
        client
            .get()
            .uri("/api/v1/nodes/root")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(NodeDetailResponse.class)
            .returnResult()
            .getResponseBody();
    return root.id();
  }

  private long create(long parentId, String kind, String name) {
    var result =
        client
            .post()
            .uri("/api/v1/nodes")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("parentId", parentId, "kind", kind, "name", name))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(NodeResponse.class)
            .returnResult();
    NodeResponse created = result.getResponseBody();
    assertThat(result.getResponseHeaders().getLocation()).isNotNull();
    assertThat(result.getResponseHeaders().getLocation().getPath())
        .isEqualTo("/api/v1/nodes/" + created.id());
    return created.id();
  }
}
