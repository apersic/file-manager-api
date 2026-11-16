package filemanager.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import filemanager.config.CorsProperties;
import filemanager.config.SecurityConfig;
import filemanager.domain.Node;
import filemanager.domain.NodeKind;
import filemanager.exception.RestExceptionHandler;
import filemanager.service.NodeCatalog;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = NodeController.class)
@Import({SecurityConfig.class, RestExceptionHandler.class})
@EnableConfigurationProperties(CorsProperties.class)
@ActiveProfiles("test")
class NodeControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private NodeCatalog catalog;

  @Test
  void createReturnsLocationAndKeepsTheTypedName() throws Exception {
    Instant stamp = Instant.parse("2026-01-02T03:04:05Z");
    when(catalog.create(1L, NodeKind.FOLDER, "Designs"))
        .thenReturn(new Node(4L, 1L, NodeKind.FOLDER, "Designs", stamp, stamp));

    mockMvc
        .perform(
            post("/api/v1/nodes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentId\":1,\"kind\":\"FOLDER\",\"name\":\"Designs\"}"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/v1/nodes/4")))
        .andExpect(jsonPath("$.id").value(4))
        .andExpect(jsonPath("$.name").value("Designs"))
        .andExpect(jsonPath("$.kind").value("FOLDER"))
        .andExpect(jsonPath("$.parentId").value(1));
  }

  @Test
  void blankDotSlashAndOverlongNamesAreFieldErrors() throws Exception {
    assertNameRejected(" ", "Name is required");
    assertNameRejected(
        ".", "Name cannot start or end with a space, be \".\" or \"..\", or contain a slash");
    assertNameRejected(
        "..", "Name cannot start or end with a space, be \".\" or \"..\", or contain a slash");
    assertNameRejected(
        "a/b", "Name cannot start or end with a space, be \".\" or \"..\", or contain a slash");
    assertNameRejected(
        "a\\b", "Name cannot start or end with a space, be \".\" or \"..\", or contain a slash");
    assertNameRejected(
        " Designs",
        "Name cannot start or end with a space, be \".\" or \"..\", or contain a slash");
    assertNameRejected("a".repeat(256), "Name must be at most 255 characters");
    verify(catalog, never()).create(anyLong(), any(), any());
  }

  @Test
  void limitAboveTheCapIsAFieldError() throws Exception {
    mockMvc
        .perform(get("/api/v1/nodes").param("parentId", "1").param("limit", "101"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.limit").value("Limit must be between 1 and 100"));

    mockMvc
        .perform(get("/api/v1/nodes").param("parentId", "1").param("limit", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.limit").value("Limit must be between 1 and 100"));

    verify(catalog, never()).listChildren(anyLong(), any(), eq(101));
    verify(catalog, never()).listChildren(anyLong(), isNull(), eq(0));
  }

  private void assertNameRejected(String name, String message) throws Exception {
    String body =
        "{\"parentId\":1,\"kind\":\"FOLDER\",\"name\":\""
            + name.replace("\\", "\\\\").replace("\"", "\\\"")
            + "\"}";
    mockMvc
        .perform(post("/api/v1/nodes").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.name").value(message));
  }
}
