package filemanager.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

  private String allowedOriginPatterns = "http://localhost:5173,http://127.0.0.1:5173";

  public String getAllowedOriginPatterns() {
    return allowedOriginPatterns;
  }

  public void setAllowedOriginPatterns(String allowedOriginPatterns) {
    this.allowedOriginPatterns = allowedOriginPatterns;
  }

  public List<String> originPatterns() {
    return Arrays.stream(allowedOriginPatterns.split(","))
        .map(String::trim)
        .filter(pattern -> !pattern.isEmpty())
        .toList();
  }
}
