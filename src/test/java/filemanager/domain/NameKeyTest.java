package filemanager.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NameKeyTest {

  @Test
  void foldsAsciiOnly() {
    assertEquals("report", NameKey.fold("Report"));
    assertEquals("É", NameKey.fold("É"));
    assertEquals("100%", NameKey.fold("100%"));
    assertEquals("a_b", NameKey.fold("A_b"));
  }

  @Test
  void prefixEndIsTheNextCodePoint() {
    assertEquals("ac", NameKey.prefixEnd("ab"));
    assertEquals("{", NameKey.prefixEnd("z"));
    assertNull(NameKey.prefixEnd("\uDBFF\uDFFF"));
    assertTrue(NameKey.compare("ab", "ac") < 0);
    assertTrue(NameKey.compare("azz", "b") < 0);
    assertTrue(NameKey.compare("ab", "ab") == 0);
  }
}
