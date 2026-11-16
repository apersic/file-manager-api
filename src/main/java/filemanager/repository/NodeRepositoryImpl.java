package filemanager.repository;

import filemanager.domain.Node;
import filemanager.domain.NodeKind;
import filemanager.domain.NodeName;
import filemanager.domain.SearchMatch;
import filemanager.exception.DuplicateNodeNameException;
import filemanager.exception.NodeNotFoundException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NodeRepositoryImpl implements NodeRepositoryCustom {

  private final JdbcClient jdbc;

  @Override
  public Optional<Node> insert(long parentId, NodeKind kind, String name) {
    try {
      List<Node> created =
          jdbc.sql(
                  """
                  INSERT INTO node (parent_id, kind, name)
                  VALUES (:parentId, :kind, :name)
                  ON CONFLICT (parent_id, name_key) DO NOTHING
                  RETURNING id, parent_id, kind, name, created_at, updated_at
                  """)
              .param("parentId", parentId)
              .param("kind", kind.name())
              .param("name", name)
              .query((rs, row) -> mapNode(rs))
              .list();
      return created.stream().findFirst();
    } catch (DataIntegrityViolationException ex) {
      throw translate(ex);
    }
  }

  @Override
  public List<NodeRow> findChildren(long parentId, String afterNameKey, Long afterId, int limit) {
    if (afterNameKey == null) {
      return jdbc.sql(
              """
              SELECT id, parent_id, kind, name, name_key, created_at, updated_at
              FROM node
              WHERE parent_id = :parentId
              ORDER BY name_key, id
              LIMIT :limit
              """)
          .param("parentId", parentId)
          .param("limit", limit)
          .query((rs, row) -> mapRow(rs))
          .list();
    }
    return jdbc.sql(
            """
            SELECT id, parent_id, kind, name, name_key, created_at, updated_at
            FROM node
            WHERE parent_id = :parentId
              AND (
                name_key > CAST(:afterNameKey AS varchar) COLLATE "C"
                OR (
                  name_key = CAST(:afterNameKey AS varchar) COLLATE "C"
                  AND id > :afterId
                )
              )
            ORDER BY name_key, id
            LIMIT :limit
            """)
        .param("parentId", parentId)
        .param("afterNameKey", afterNameKey)
        .param("afterId", afterId)
        .param("limit", limit)
        .query((rs, row) -> mapRow(rs))
        .list();
  }

  @Override
  public List<NodeRow> search(
      Long parentId,
      SearchMatch match,
      String lo,
      String hi,
      String lowerKey,
      long lowerId,
      int limit) {
    boolean upperBound = hi != null;
    boolean resumed = lowerId > 0;
    var query =
        jdbc.sql(NodeSearchSql.select(parentId != null, match, upperBound, resumed))
            .param("limit", limit);
    if (parentId != null) {
      query = query.param("parentId", parentId);
    }
    if (resumed) {
      query = query.param("lowerId", lowerId);
    }
    query =
        switch (match) {
          case EXACT -> query.param("lo", lo);
          case PREFIX ->
              upperBound
                  ? query.param("lowerKey", lowerKey).param("hi", hi)
                  : query.param("lowerKey", lowerKey);
        };
    return query.query((rs, row) -> mapRow(rs)).list();
  }

  @Override
  public List<Node> findPath(long id) {
    return jdbc.sql(
            """
            WITH RECURSIVE walk AS (
                SELECT id, parent_id, 0 AS depth
                FROM node
                WHERE id = :id
                UNION ALL
                SELECT parent.id, parent.parent_id, walk.depth + 1
                FROM node parent
                INNER JOIN walk ON parent.id = walk.parent_id
                WHERE walk.depth < 1000
            )
            SELECT node.id, node.parent_id, node.kind, node.name, node.created_at, node.updated_at
            FROM walk
            INNER JOIN node ON node.id = walk.id
            ORDER BY walk.depth DESC
            """)
        .param("id", id)
        .query((rs, row) -> mapNode(rs))
        .list();
  }

  private static NodeRow mapRow(ResultSet rs) throws SQLException {
    return new NodeRow(mapNode(rs), rs.getString("name_key"));
  }

  private static Node mapNode(ResultSet rs) throws SQLException {
    return new Node(
        rs.getLong("id"),
        rs.getObject("parent_id", Long.class),
        NodeKind.valueOf(rs.getString("kind")),
        rs.getString("name"),
        instant(rs, "created_at"),
        instant(rs, "updated_at"));
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    return rs.getObject(column, OffsetDateTime.class).toInstant();
  }

  private static RuntimeException translate(DataIntegrityViolationException ex) {
    String message = String.valueOf(ex.getMostSpecificCause().getMessage());
    if (message.contains("uq_node_sibling_name")) {
      return new DuplicateNodeNameException();
    }
    if (message.contains("fk_node_parent")) {
      return new NodeNotFoundException("Folder was not found");
    }
    if (message.contains("ck_node_name")) {
      return new IllegalArgumentException(NodeName.MESSAGE);
    }
    return ex;
  }
}
