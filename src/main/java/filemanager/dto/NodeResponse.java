package filemanager.dto;

import filemanager.domain.NodeKind;
import java.time.Instant;

public record NodeResponse(
    long id, Long parentId, NodeKind kind, String name, Instant createdAt, Instant updatedAt) {}
