package filemanager.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class SecurityConfigTest extends BaseControllerTest {

  @Test
  void apiRoutesArePublic() throws Exception {
    mockMvc.perform(get("/api/v1/does-not-exist")).andExpect(status().isNotFound());
  }

  @Test
  void nonHealthActuatorRouteIsDenied() throws Exception {
    mockMvc.perform(get("/actuator/info")).andExpect(status().isForbidden());
  }

  @Test
  void envProbeDoesNotRequireAuthentication() throws Exception {
    mockMvc.perform(get("/.env")).andExpect(status().isNotFound());
  }
}
