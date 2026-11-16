package filemanager.service;

import filemanager.domain.Node;
import filemanager.domain.NodeDetails;
import filemanager.domain.NodeKind;
import filemanager.domain.NodePage;
import filemanager.domain.SearchMatch;

public interface NodeCatalog {

  NodeDetails getRoot();

  NodeDetails get(long id);

  NodePage listChildren(long parentId, String cursor, Integer limit);

  NodePage search(String query, SearchMatch match, Long parentId, String cursor, Integer limit);

  Node create(long parentId, NodeKind kind, String name);
}
