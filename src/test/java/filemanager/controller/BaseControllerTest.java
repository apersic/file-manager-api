package filemanager.controller;

import filemanager.config.CorsProperties;
import filemanager.config.SecurityConfig;
import filemanager.exception.RestExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ErrorProbeController.class)
@Import({SecurityConfig.class, RestExceptionHandler.class})
@EnableConfigurationProperties(CorsProperties.class)
@ActiveProfiles("test")
public abstract class BaseControllerTest {

  @Autowired protected MockMvc mockMvc;
}
