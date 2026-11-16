package filemanager.mapper;

import filemanager.domain.Node;
import filemanager.domain.NodeDetails;
import filemanager.domain.NodePage;
import filemanager.dto.AncestorResponse;
import filemanager.dto.NodeDetailResponse;
import filemanager.dto.NodePageResponse;
import filemanager.dto.NodeResponse;

public final class NodeMapper {

  private NodeMapper() {}

  public static NodeResponse toResponse(Node node) {
    return new NodeResponse(
        node.id(), node.parentId(), node.kind(), node.name(), node.createdAt(), node.updatedAt());
  }

  public static NodeDetailResponse toDetail(NodeDetails details) {
    Node node = details.node();
    return new NodeDetailResponse(
        node.id(),
        node.parentId(),
        node.kind(),
        node.name(),
        node.createdAt(),
        node.updatedAt(),
        details.ancestors().stream().map(NodeMapper::toAncestor).toList());
  }

  public static NodePageResponse toPage(NodePage page) {
    return new NodePageResponse(
        page.items().stream().map(NodeMapper::toResponse).toList(), page.nextCursor());
  }

  private static AncestorResponse toAncestor(Node node) {
    return new AncestorResponse(node.id(), node.name(), node.kind());
  }
}
