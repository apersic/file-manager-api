package filemanager.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import filemanager.TestContainersManager;
import filemanager.domain.NameKey;
import filemanager.domain.SearchMatch;
import filemanager.repository.NodeSearchSql;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class NodeSearchPlanTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Autowired private JdbcClient jdbc;

  @Autowired private TransactionTemplate transactions;

  @DynamicPropertySource
  static void registerPgProperties(DynamicPropertyRegistry registry) {
    TestContainersManager.startPostgreSQLContainer();
    var container = TestContainersManager.getPostgreSQLContainer();
    registry.add("spring.datasource.url", container::getJdbcUrl);
    registry.add("spring.datasource.username", container::getUsername);
    registry.add("spring.datasource.password", container::getPassword);
  }

  @Test
  void javaFoldMatchesPostgresCLower() {
    String folded =
        jdbc.sql("SELECT lower(CAST(:q AS varchar) COLLATE \"C\")")
            .param("q", "ReportÉ%")
            .query((rs, row) -> rs.getString(1))
            .single();
    assertThat(folded).isEqualTo(NameKey.fold("ReportÉ%"));
  }

  @Test
  void folderSearchUsesTheSiblingIndex() {
    long wide = insert("FOLDER", 1L, "plan-wide");
    List<Long> folders = new ArrayList<>();
    try {
      for (int i = 0; i < 80; i++) {
        long folder = insert("FOLDER", 1L, "plan-folder-" + i);
        folders.add(folder);
        insert("FILE", folder, "plan-shared");
        insert("FILE", folder, "p" + i);
      }
      for (int i = 0; i < 80; i++) {
        insert("FILE", wide, "pwide-" + i);
      }
      jdbc.sql("ANALYZE node").update();

      long parent = folders.get(0);
      inPlan(
          () -> {
            assertIndex(
                explain(NodeSearchSql.select(true, SearchMatch.EXACT, false, false), parent, false),
                "uq_node_sibling_name");
            assertIndex(
                explain(NodeSearchSql.select(true, SearchMatch.PREFIX, true, false), wide, true),
                "uq_node_sibling_name");
          });
    } finally {
      jdbc.sql("DELETE FROM node WHERE id = :id").param("id", wide).update();
      for (long folder : folders) {
        jdbc.sql("DELETE FROM node WHERE id = :id").param("id", folder).update();
      }
    }
  }

  @Test
  void globalSearchUsesTheNameIndex() {
    inPlan(
        () -> {
          assertIndex(
              explain(NodeSearchSql.select(false, SearchMatch.EXACT, false, false), null, false),
              "ix_node_name_key");
          assertIndex(
              explain(NodeSearchSql.select(false, SearchMatch.PREFIX, true, false), null, true),
              "ix_node_name_key");
        });
  }

  private long insert(String kind, long parentId, String name) {
    return jdbc.sql(
            """
            INSERT INTO node (parent_id, kind, name)
            VALUES (:parentId, :kind, :name)
            RETURNING id
            """)
        .param("parentId", parentId)
        .param("kind", kind)
        .param("name", name)
        .query((rs, row) -> rs.getLong(1))
        .single();
  }

  private void inPlan(Runnable check) {
    transactions.executeWithoutResult(
        status -> {
          jdbc.sql("SET LOCAL enable_seqscan = off").update();
          jdbc.sql("SET LOCAL enable_bitmapscan = off").update();
          check.run();
        });
  }

  private String explain(String sql, Long parentId, boolean prefix) {
    var query = jdbc.sql("EXPLAIN (FORMAT JSON) " + sql).param("limit", 10);
    if (parentId != null) {
      query = query.param("parentId", parentId);
    }
    if (prefix) {
      query = query.param("lowerKey", "p").param("hi", "q");
    } else {
      query = query.param("lo", "plan-shared");
    }
    return query.query((rs, row) -> rs.getString(1)).single();
  }

  private static void assertIndex(String plan, String indexName) throws RuntimeException {
    try {
      JsonNode root = JSON.readTree(plan);
      assertThat(indexNames(root)).as(plan).contains(indexName);
      assertThat(plan).as(plan).doesNotContain("Seq Scan");
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalStateException(plan, ex);
    }
  }

  private static List<String> indexNames(JsonNode node) {
    List<String> names = new ArrayList<>();
    if (node.has("Index Name")) {
      names.add(node.get("Index Name").asText());
    }
    node.elements().forEachRemaining(child -> names.addAll(indexNames(child)));
    return names;
  }
}
