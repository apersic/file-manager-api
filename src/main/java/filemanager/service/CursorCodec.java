package filemanager.service;

import filemanager.exception.InvalidCursorException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class CursorCodec {

  private CursorCodec() {}

  public record Position(String nameKey, long id) {}

  public static String encode(String nameKey, long id) {
    byte[] key = nameKey.getBytes(StandardCharsets.UTF_8);
    byte[] idBytes = Long.toString(id).getBytes(StandardCharsets.UTF_8);
    byte[] raw = new byte[key.length + 1 + idBytes.length];
    System.arraycopy(key, 0, raw, 0, key.length);
    raw[key.length] = 0;
    System.arraycopy(idBytes, 0, raw, key.length + 1, idBytes.length);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
  }

  public static Position decode(String token) {
    try {
      byte[] raw = Base64.getUrlDecoder().decode(token);
      int separator = indexOfZero(raw);
      if (separator < 0 || separator == raw.length - 1) {
        throw new InvalidCursorException();
      }
      String nameKey = new String(raw, 0, separator, StandardCharsets.UTF_8);
      String idText =
          new String(raw, separator + 1, raw.length - separator - 1, StandardCharsets.UTF_8);
      long id = Long.parseLong(idText);
      if (id <= 0 || idText.startsWith("+") || idText.startsWith("-")) {
        throw new InvalidCursorException();
      }
      return new Position(nameKey, id);
    } catch (IllegalArgumentException ex) {
      throw new InvalidCursorException();
    }
  }

  private static int indexOfZero(byte[] raw) {
    for (int i = 0; i < raw.length; i++) {
      if (raw[i] == 0) {
        return i;
      }
    }
    return -1;
  }
}
