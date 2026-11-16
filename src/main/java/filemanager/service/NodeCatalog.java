package filemanager.service;

import filemanager.domain.Node;
import filemanager.domain.NodeDetails;
import filemanager.domain.NodeKind;
import filemanager.domain.NodePage;

public interface NodeCatalog {

  NodeDetails getRoot();

  NodeDetails get(long id);

  NodePage listChildren(long parentId, String cursor, Integer limit);

  Node create(long parentId, NodeKind kind, String name);
}
