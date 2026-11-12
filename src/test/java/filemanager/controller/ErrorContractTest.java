package filemanager.controller;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class ErrorContractTest extends BaseControllerTest {

  @Test
  void unknownApiRouteUsesErrorEnvelope() throws Exception {
    mockMvc
        .perform(get("/api/v1/does-not-exist"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Not Found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.error").value("Not Found"))
        .andExpect(jsonPath("$.timestamp").exists())
        .andExpect(jsonPath("$.path").value("/api/v1/does-not-exist"));
  }

  @Test
  void probePathIsNotFoundWithoutDetail() throws Exception {
    mockMvc
        .perform(get("/.env"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Not Found"))
        .andExpect(jsonPath("$.message", not("secret database password")));
  }

  @Test
  void validationFailureIsFieldMap() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/error-probes/names")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.name").value("Name is required"))
        .andExpect(jsonPath("$.message").doesNotExist());
  }

  @Test
  void malformedJsonIsBadRequestEnvelope() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/error-probes/names").contentType(MediaType.APPLICATION_JSON).content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Malformed request body"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.error").value("Bad Request"));
  }

  @Test
  void missingParameterIsBadRequest() throws Exception {
    mockMvc
        .perform(get("/api/v1/error-probes/required"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Missing request parameter"))
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void mistypedParameterIsBadRequest() throws Exception {
    mockMvc
        .perform(get("/api/v1/error-probes/typed").param("id", "abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Mistyped request parameter"));
  }

  @Test
  void illegalArgumentUsesItsMessage() throws Exception {
    mockMvc
        .perform(get("/api/v1/error-probes/bad-argument"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Name is blank"))
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void unexpectedExceptionHidesTheCause() throws Exception {
    mockMvc
        .perform(get("/api/v1/error-probes/boom"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
        .andExpect(jsonPath("$.message", not("secret database password")))
        .andExpect(jsonPath("$.status").value(500))
        .andExpect(jsonPath("$.error").value("Internal Server Error"));
  }

  @Test
  void wrongMethodIsMethodNotAllowed() throws Exception {
    mockMvc
        .perform(post("/api/v1/error-probes/boom"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.message").value("Method not allowed"))
        .andExpect(jsonPath("$.status").value(405));
  }

  @Test
  void unsupportedMediaTypeIsNotAServerError() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/error-probes/names").contentType(MediaType.TEXT_PLAIN).content("name"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.message").value("Unsupported media type"))
        .andExpect(jsonPath("$.status").value(415));
  }

  @Test
  void allowedOriginPreflightEchoesTheViteOrigin() throws Exception {
    mockMvc
        .perform(
            options("/api/v1/error-probes/names")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
        .andExpect(status().isOk())
        .andExpect(
            header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
  }

  @Test
  void disallowedOriginIsRejected() throws Exception {
    mockMvc
        .perform(
            options("/api/v1/error-probes/names")
                .header(HttpHeaders.ORIGIN, "http://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
        .andExpect(status().isForbidden());
  }
}
