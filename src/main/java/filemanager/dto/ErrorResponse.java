package filemanager.dto;

import java.time.Instant;

public record ErrorResponse(
    String message, int status, String error, Instant timestamp, String path) {}
