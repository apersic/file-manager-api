package filemanager.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import filemanager.TestContainersManager;
import filemanager.dto.NodeDetailResponse;
import filemanager.dto.NodeResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class NodeDeleteE2eTest {

  @LocalServerPort private int port;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private TransactionTemplate transactions;

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
  void deleteRemovesADeepTreeAndLeavesNoOrphans() {
    long rootId = rootId();
    String token = token();
    long holder = create(rootId, "FOLDER", token + "h");
    long current = holder;
    List<Long> chain = new ArrayList<>();
    for (int depth = 0; depth < 24; depth++) {
      current = create(current, "FOLDER", token + String.format("%02d", depth));
      chain.add(current);
    }
    long fileId = create(current, "FILE", token + "file");

    client.delete().uri("/api/v1/nodes/{id}", chain.get(0)).exchange().expectStatus().isNoContent();

    assertThat(countById(chain.get(0))).isZero();
    assertThat(countById(fileId)).isZero();
    assertThat(countByNamePrefix(token)).isEqualTo(1);
    assertThat(countById(holder)).isEqualTo(1);
    assertThat(countById(rootId)).isEqualTo(1);
    assertNoOrphans();

    client.get().uri("/api/v1/nodes/{id}", chain.get(0)).exchange().expectStatus().isNotFound();
  }

  @Test
  void deleteRemovesAWideTreeAndLeavesNoOrphans() {
    long rootId = rootId();
    String token = token();
    long holder = create(rootId, "FOLDER", token + "h");
    long folder = create(holder, "FOLDER", token + "f");
    List<Long> files = new ArrayList<>();
    for (int i = 0; i < 40; i++) {
      files.add(create(folder, "FILE", token + String.format("%02d", i)));
    }

    client.delete().uri("/api/v1/nodes/{id}", folder).exchange().expectStatus().isNoContent();

    assertThat(countById(folder)).isZero();
    assertThat(countById(files.get(0))).isZero();
    assertThat(countById(files.get(files.size() - 1))).isZero();
    assertThat(countByNamePrefix(token)).isEqualTo(1);
    assertThat(countById(holder)).isEqualTo(1);
    assertThat(countById(rootId)).isEqualTo(1);
    assertNoOrphans();
  }

  @Test
  void missingDeleteIsNotFoundAndASecondDeleteIsNotFound() {
    long fileId = create(rootId(), "FILE", "gone-" + UUID.randomUUID());

    client.delete().uri("/api/v1/nodes/{id}", fileId).exchange().expectStatus().isNoContent();

    client
        .delete()
        .uri("/api/v1/nodes/{id}", fileId)
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.message")
        .isEqualTo("Node was not found");

    client
        .delete()
        .uri("/api/v1/nodes/{id}", 9_000_000_000L)
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.message")
        .isEqualTo("Node was not found");
  }

  @Test
  void rootDeleteIsConflictAndSqlDeleteIsRejected() {
    long rootId = rootId();
    long child = create(rootId, "FOLDER", "keep-" + UUID.randomUUID());

    client
        .delete()
        .uri("/api/v1/nodes/{id}", rootId)
        .exchange()
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.message")
        .isEqualTo("The root folder cannot be deleted");

    assertThat(countById(rootId)).isEqualTo(1);
    assertThat(countById(child)).isEqualTo(1);

    assertThatThrownBy(() -> jdbc.update("delete from node where id = ?", rootId))
        .hasMessageContaining("root folder cannot be deleted");
    assertThat(countById(rootId)).isEqualTo(1);
    assertThat(countById(child)).isEqualTo(1);
    assertNoOrphans();
  }

  @Test
  void failedDeleteRollsBackAndLeavesNoOrphans() {
    long rootId = rootId();
    long folderId = create(rootId, "FOLDER", "rollback-" + UUID.randomUUID());
    String pending = "pending-" + UUID.randomUUID();

    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    status -> {
                      jdbc.update(
                          "insert into node (parent_id, kind, name) values (?, 'FILE', ?)",
                          folderId,
                          pending);
                      jdbc.update("delete from node where id = ?", rootId);
                    }))
        .hasMessageContaining("root folder cannot be deleted");

    Integer pendingRows =
        jdbc.queryForObject(
            "select count(*) from node where parent_id = ? and name = ?",
            Integer.class,
            folderId,
            pending);
    assertThat(pendingRows).isZero();
    assertThat(countById(rootId)).isEqualTo(1);
    assertThat(countById(folderId)).isEqualTo(1);
    assertNoOrphans();
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
    NodeResponse created =
        client
            .post()
            .uri("/api/v1/nodes")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("parentId", parentId, "kind", kind, "name", name))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(NodeResponse.class)
            .returnResult()
            .getResponseBody();
    return created.id();
  }

  private int countById(long id) {
    Integer count =
        jdbc.queryForObject("select count(*) from node where id = ?", Integer.class, id);
    return count == null ? 0 : count;
  }

  private int countByNamePrefix(String prefix) {
    Integer count =
        jdbc.queryForObject(
            "select count(*) from node where name like ?", Integer.class, prefix + "%");
    return count == null ? 0 : count;
  }

  private void assertNoOrphans() {
    Integer orphans =
        jdbc.queryForObject(
            """
            select count(*) from node child
            where child.parent_id is not null
              and not exists (select 1 from node parent where parent.id = child.parent_id)
            """,
            Integer.class);
    assertThat(orphans).isZero();
  }

  private static String token() {
    return "t" + UUID.randomUUID().toString().replace("-", "");
  }
}
