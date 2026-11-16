package filemanager.domain;

import java.util.List;

public record NodeDetails(Node node, List<Node> ancestors) {}
