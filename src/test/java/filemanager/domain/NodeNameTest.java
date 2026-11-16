package filemanager.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NodeNameTest {

  @Test
  void acceptsOrdinaryNames() {
    assertTrue(NodeName.isAllowed("Designs"));
    assertTrue(NodeName.isAllowed("Report.txt"));
    assertTrue(NodeName.isAllowed("a"));
    assertTrue(NodeName.isAllowed("..."));
  }

  @Test
  void rejectsBlankReservedSlashAndEdges() {
    assertFalse(NodeName.isAllowed(null));
    assertFalse(NodeName.isAllowed(""));
    assertFalse(NodeName.isAllowed(" "));
    assertFalse(NodeName.isAllowed(" Designs"));
    assertFalse(NodeName.isAllowed("Designs "));
    assertFalse(NodeName.isAllowed("."));
    assertFalse(NodeName.isAllowed(".."));
    assertFalse(NodeName.isAllowed("a/b"));
    assertFalse(NodeName.isAllowed("a\\b"));
    assertFalse(NodeName.isAllowed("a".repeat(256)));
    assertTrue(NodeName.isAllowed("a".repeat(255)));
  }

  @Test
  void checkRejectsABadName() {
    assertThrows(IllegalArgumentException.class, () -> NodeName.check(".."));
  }
}
