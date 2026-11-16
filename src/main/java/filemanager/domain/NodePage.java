package filemanager.domain;

import java.util.List;

public record NodePage(List<Node> items, String nextCursor) {}
