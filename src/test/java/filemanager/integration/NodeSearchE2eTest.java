package filemanager.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import filemanager.TestContainersManager;
import filemanager.dto.NodeDetailResponse;
import filemanager.dto.NodePageResponse;
import filemanager.dto.NodeResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class NodeSearchE2eTest {

  @LocalServerPort private int port;

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
  void exactMatchStaysInTheFolderAndIgnoresCase() {
    long rootId = rootId();
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String name = "Re" + suffix;
    long box = create(rootId, "FOLDER", "Box " + suffix);
    long reports = create(box, "FOLDER", "Reports " + suffix);
    create(box, "FILE", name);
    create(box, "FILE", "Other " + suffix);
    create(reports, "FILE", name);

    NodePageResponse inFolder = search("re" + suffix, "EXACT", box, null, null);
    assertThat(inFolder.items()).extracting(NodeResponse::name).containsExactly(name);
    assertThat(inFolder.items().get(0).kind().name()).isEqualTo("FILE");

    NodePageResponse everywhere = search("re" + suffix, "EXACT", null, null, null);
    assertThat(everywhere.items()).extracting(NodeResponse::name).containsExactly(name, name);
  }

  @Test
  void prefixMatchIsAStartRangeAndPercentIsLiteral() {
    long rootId = rootId();
    String token = UUID.randomUUID().toString().substring(0, 8);
    long box = create(rootId, "FOLDER", "Wide " + token);
    create(box, "FILE", token + " desk");
    create(box, "FOLDER", token + " designs");
    create(box, "FILE", token + " other");
    long nested = create(box, "FOLDER", token + " nested");
    create(nested, "FILE", token + " deep");
    create(box, "FILE", token + " 100%done");
    create(box, "FILE", token + " 100Xdone");
    create(box, "FILE", token + " a_c");
    create(box, "FILE", token + " abc");

    NodePageResponse first = search(token + " d", "PREFIX", null, null, 1);
    assertThat(first.items()).extracting(NodeResponse::name).containsExactly(token + " deep");
    assertThat(first.nextCursor()).isNotBlank();

    NodePageResponse second = search(token + " d", "PREFIX", null, first.nextCursor(), 10);
    assertThat(second.items())
        .extracting(NodeResponse::name)
        .containsExactly(token + " designs", token + " desk");

    NodePageResponse inFolder = search(token + " d", "PREFIX", box, null, 10);
    assertThat(inFolder.items())
        .extracting(NodeResponse::name)
        .containsExactly(token + " designs", token + " desk");

    assertThat(search(token + " 100%", "PREFIX", box, null, 10).items())
        .extracting(NodeResponse::name)
        .containsExactly(token + " 100%done");
    assertThat(search(token + " a_c", "PREFIX", box, null, 10).items())
        .extracting(NodeResponse::name)
        .containsExactly(token + " a_c");
  }

  @Test
  void missingFolderAndFileParentAreRejected() {
    long rootId = rootId();
    long fileId = create(rootId, "FILE", "loose " + UUID.randomUUID());

    client
        .get()
        .uri(
            uri ->
                uri.path("/api/v1/nodes/search")
                    .queryParam("q", "a")
                    .queryParam("match", "EXACT")
                    .queryParam("parentId", 9_000_000_000L)
                    .build())
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.message")
        .isEqualTo("Folder was not found");

    client
        .get()
        .uri(
            uri ->
                uri.path("/api/v1/nodes/search")
                    .queryParam("q", "a")
                    .queryParam("match", "PREFIX")
                    .queryParam("parentId", fileId)
                    .build())
        .exchange()
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.message")
        .isEqualTo("Parent is not a folder");
  }

  @Test
  void homeIsFoundByItsExactName() {
    NodePageResponse page = search("home", "EXACT", null, null, null);
    assertThat(page.items()).extracting(NodeResponse::name).contains("Home");
    assertThat(page.items())
        .filteredOn(node -> node.name().equals("Home"))
        .allMatch(node -> node.parentId() == null && node.kind().name().equals("FOLDER"));
  }

  private NodePageResponse search(
      String query, String match, Long parentId, String cursor, Integer limit) {
    return client
        .get()
        .uri(
            uri -> {
              var builder =
                  uri.path("/api/v1/nodes/search")
                      .queryParam("q", query)
                      .queryParam("match", match);
              if (parentId != null) {
                builder.queryParam("parentId", parentId);
              }
              if (cursor != null) {
                builder.queryParam("cursor", cursor);
              }
              if (limit != null) {
                builder.queryParam("limit", limit);
              }
              return builder.build();
            })
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(NodePageResponse.class)
        .returnResult()
        .getResponseBody();
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
}
