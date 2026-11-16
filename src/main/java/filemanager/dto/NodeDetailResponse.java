package filemanager.dto;

import filemanager.domain.NodeKind;
import java.time.Instant;
import java.util.List;

public record NodeDetailResponse(
    long id,
    Long parentId,
    NodeKind kind,
    String name,
    Instant createdAt,
    Instant updatedAt,
    List<AncestorResponse> ancestors) {}
