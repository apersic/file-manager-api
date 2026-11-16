package filemanager.domain;

import java.time.Instant;

public record Node(
    long id, Long parentId, NodeKind kind, String name, Instant createdAt, Instant updatedAt) {}
