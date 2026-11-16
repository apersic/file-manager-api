package filemanager.dto;

import java.util.List;

public record NodePageResponse(List<NodeResponse> items, String nextCursor) {}
