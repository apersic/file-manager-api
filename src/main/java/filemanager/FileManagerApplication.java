package filemanager;

import filemanager.config.CorsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(CorsProperties.class)
public class FileManagerApplication {

  public static void main(String[] args) {
    SpringApplication.run(FileManagerApplication.class, args);
  }
}
