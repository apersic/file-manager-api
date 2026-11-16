package filemanager.repository;

import filemanager.domain.Node;
import filemanager.domain.NodeKind;
import filemanager.domain.SearchMatch;
import java.util.List;
import java.util.Optional;

public interface NodeRepositoryCustom {

  Optional<Node> insert(long parentId, NodeKind kind, String name);

  List<NodeRow> findChildren(long parentId, String afterNameKey, Long afterId, int limit);

  List<NodeRow> search(
      Long parentId,
      SearchMatch match,
      String lo,
      String hi,
      String lowerKey,
      long lowerId,
      int limit);

  List<Node> findPath(long id);
}
