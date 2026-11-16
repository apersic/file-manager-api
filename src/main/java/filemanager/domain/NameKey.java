package filemanager.domain;

public final class NameKey {

  private static final int MAX_CODE_POINT = 0x10FFFF;

  private NameKey() {}

  /** Same fold as PostgreSQL {@code lower(text COLLATE "C")}: ASCII A-Z only. */
  public static String fold(String raw) {
    StringBuilder folded = new StringBuilder(raw.length());
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (c >= 'A' && c <= 'Z') {
        folded.append((char) (c + ('a' - 'A')));
      } else {
        folded.append(c);
      }
    }
    return folded.toString();
  }

  /**
   * Exclusive end of the prefix range in code-point order. Null when every key at or after {@code
   * folded} still starts with it.
   */
  public static String prefixEnd(String folded) {
    int count = folded.codePointCount(0, folded.length());
    int[] points = new int[count];
    int index = 0;
    for (int i = 0; i < folded.length(); ) {
      int point = folded.codePointAt(i);
      points[index++] = point;
      i += Character.charCount(point);
    }
    for (int i = points.length - 1; i >= 0; i--) {
      if (points[i] < MAX_CODE_POINT) {
        points[i]++;
        return new String(points, 0, i + 1);
      }
    }
    return null;
  }

  public static int compare(String left, String right) {
    int i = 0;
    int j = 0;
    while (i < left.length() && j < right.length()) {
      int a = left.codePointAt(i);
      int b = right.codePointAt(j);
      if (a != b) {
        return Integer.compare(a, b);
      }
      i += Character.charCount(a);
      j += Character.charCount(b);
    }
    if (i >= left.length() && j >= right.length()) {
      return 0;
    }
    return i >= left.length() ? -1 : 1;
  }
}
