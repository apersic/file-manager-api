package filemanager.controller;

import filemanager.domain.SearchMatch;
import filemanager.dto.CreateNodeRequest;
import filemanager.dto.NodeDetailResponse;
import filemanager.dto.NodePageResponse;
import filemanager.dto.NodeResponse;
import filemanager.mapper.NodeMapper;
import filemanager.service.NodeCatalog;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/nodes")
@Validated
@RequiredArgsConstructor
public class NodeController {

  private final NodeCatalog catalog;

  @GetMapping("/root")
  public NodeDetailResponse getRoot() {
    return NodeMapper.toDetail(catalog.getRoot());
  }

  @GetMapping("/search")
  public NodePageResponse search(
      @RequestParam("q")
          @NotBlank(message = "Query is required")
          @Size(max = 255, message = "Query must be at most 255 characters")
          String q,
      @RequestParam("match") SearchMatch match,
      @RequestParam(value = "parentId", required = false) Long parentId,
      @RequestParam(value = "cursor", required = false)
          @Size(max = 2048, message = "Cursor is too long")
          String cursor,
      @RequestParam(value = "limit", required = false)
          @Min(value = 1, message = "Limit must be between 1 and 100")
          @Max(value = 100, message = "Limit must be between 1 and 100")
          Integer limit) {
    return NodeMapper.toPage(catalog.search(q, match, parentId, cursor, limit));
  }

  @GetMapping("/{id}")
  public NodeDetailResponse get(@PathVariable("id") long id) {
    return NodeMapper.toDetail(catalog.get(id));
  }

  @GetMapping
  public NodePageResponse list(
      @RequestParam("parentId") long parentId,
      @RequestParam(value = "cursor", required = false)
          @Size(max = 2048, message = "Cursor is too long")
          String cursor,
      @RequestParam(value = "limit", required = false)
          @Min(value = 1, message = "Limit must be between 1 and 100")
          @Max(value = 100, message = "Limit must be between 1 and 100")
          Integer limit) {
    return NodeMapper.toPage(catalog.listChildren(parentId, cursor, limit));
  }

  @PostMapping
  public ResponseEntity<NodeResponse> create(@Valid @RequestBody CreateNodeRequest request) {
    var created = catalog.create(request.parentId(), request.kind(), request.name());
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.id())
            .toUri();
    return ResponseEntity.created(location).body(NodeMapper.toResponse(created));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable("id") long id) {
    catalog.delete(id);
    return ResponseEntity.noContent().build();
  }
}
