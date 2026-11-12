package filemanager.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ErrorProbeController {

  @PostMapping("/api/v1/error-probes/names")
  void create(@Valid @RequestBody NameRequest request) {}

  @GetMapping("/api/v1/error-probes/boom")
  void boom() {
    throw new IllegalStateException("secret database password");
  }

  @GetMapping("/api/v1/error-probes/bad-argument")
  void badArgument() {
    throw new IllegalArgumentException("Name is blank");
  }

  @GetMapping("/api/v1/error-probes/required")
  void required(@RequestParam String q) {}

  @GetMapping("/api/v1/error-probes/typed")
  void typed(@RequestParam Long id) {}

  record NameRequest(@NotBlank(message = "Name is required") String name) {}
}
