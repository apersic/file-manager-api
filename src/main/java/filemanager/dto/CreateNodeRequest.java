package filemanager.dto;

import filemanager.domain.NodeKind;
import filemanager.validation.ValidNodeName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateNodeRequest(
    @NotNull(message = "Parent is required") Long parentId,
    @NotNull(message = "Kind is required") NodeKind kind,
    @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must be at most 255 characters")
        @ValidNodeName
        String name) {}
