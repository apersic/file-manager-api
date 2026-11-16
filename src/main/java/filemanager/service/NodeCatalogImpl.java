package filemanager.service;

import filemanager.domain.Node;
import filemanager.domain.NodeDetails;
import filemanager.domain.NodeKind;
import filemanager.domain.NodeName;
import filemanager.domain.NodePage;
import filemanager.entity.NodeEntity;
import filemanager.exception.DuplicateNodeNameException;
import filemanager.exception.NodeNotFoundException;
import filemanager.exception.ParentNotFolderException;
import filemanager.repository.NodeRepository;
import filemanager.repository.NodeRow;
import filemanager.service.CursorCodec.Position;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NodeCatalogImpl implements NodeCatalog {

  static final int DEFAULT_LIMIT = 50;
  static final int MAX_LIMIT = 100;

  private final NodeRepository nodes;

  @Override
  public NodeDetails getRoot() {
    NodeEntity root =
        nodes
            .findByParentIdIsNull()
            .orElseThrow(() -> new IllegalStateException("Root folder is missing"));
    return new NodeDetails(toNode(root), List.of());
  }

  @Override
  public NodeDetails get(long id) {
    List<Node> path = nodes.findPath(id);
    if (path.isEmpty()) {
      throw new NodeNotFoundException("Node was not found");
    }
    Node node = path.get(path.size() - 1);
    if (node.parentId() != null && path.get(0).parentId() != null) {
      throw new IllegalStateException("Ancestor walk stopped before the root");
    }
    return new NodeDetails(node, List.copyOf(path.subList(0, path.size() - 1)));
  }

  @Override
  public NodePage listChildren(long parentId, String cursor, Integer limit) {
    requireFolder(parentId);
    int size = pageSize(limit);
    Position position = decode(cursor);
    List<NodeRow> rows =
        position == null
            ? nodes.findChildren(parentId, null, null, size + 1)
            : nodes.findChildren(parentId, position.nameKey(), position.id(), size + 1);
    boolean hasMore = rows.size() > size;
    List<NodeRow> page = hasMore ? rows.subList(0, size) : rows;
    String nextCursor = null;
    if (hasMore) {
      NodeRow last = page.get(page.size() - 1);
      nextCursor = CursorCodec.encode(last.nameKey(), last.node().id());
    }
    return new NodePage(page.stream().map(NodeRow::node).toList(), nextCursor);
  }

  @Override
  @Transactional
  public Node create(long parentId, NodeKind kind, String name) {
    NodeName.check(name);
    requireFolder(parentId);
    return nodes.insert(parentId, kind, name).orElseThrow(DuplicateNodeNameException::new);
  }

  private void requireFolder(long parentId) {
    NodeEntity parent =
        nodes
            .findById(parentId)
            .orElseThrow(() -> new NodeNotFoundException("Folder was not found"));
    if (parent.getKind() != NodeKind.FOLDER) {
      throw new ParentNotFolderException();
    }
  }

  private static Position decode(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    return CursorCodec.decode(cursor);
  }

  private static int pageSize(Integer limit) {
    int size = limit == null ? DEFAULT_LIMIT : limit;
    if (size < 1 || size > MAX_LIMIT) {
      throw new IllegalArgumentException("Limit must be between 1 and 100");
    }
    return size;
  }

  private static Node toNode(NodeEntity entity) {
    return new Node(
        entity.getId(),
        entity.getParentId(),
        entity.getKind(),
        entity.getName(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
