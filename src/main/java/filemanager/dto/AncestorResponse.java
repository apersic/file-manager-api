package filemanager.dto;

import filemanager.domain.NodeKind;

public record AncestorResponse(long id, String name, NodeKind kind) {}
