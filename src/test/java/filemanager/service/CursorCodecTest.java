package filemanager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import filemanager.exception.InvalidCursorException;
import org.junit.jupiter.api.Test;

class CursorCodecTest {

  @Test
  void roundTripsNameAndId() {
    String token = CursorCodec.encode("déjà:name", 42L);

    CursorCodec.Position position = CursorCodec.decode(token);

    assertEquals("déjà:name", position.nameKey());
    assertEquals(42L, position.id());
  }

  @Test
  void rejectsGarbage() {
    assertThrows(InvalidCursorException.class, () -> CursorCodec.decode("not a cursor"));
    assertThrows(InvalidCursorException.class, () -> CursorCodec.decode("%%%%"));
    assertThrows(InvalidCursorException.class, () -> CursorCodec.decode(""));
  }
}
