package filemanager.exception;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RestExceptionHandlerTest {

  @Test
  void probePathsMatchKnownScanners() {
    assertTrue(RestExceptionHandler.isProbePath("/.env"));
    assertTrue(RestExceptionHandler.isProbePath("/wp-admin/install.php"));
    assertTrue(RestExceptionHandler.isProbePath("/.git/config"));
    assertFalse(RestExceptionHandler.isProbePath("/api/v1/nodes"));
    assertFalse(RestExceptionHandler.isProbePath(null));
  }
}
