package filemanager.domain;

public final class NodeName {

  public static final int MAX_LENGTH = 255;

  public static final String MESSAGE =
      "Name cannot start or end with a space, be \".\" or \"..\", or contain a slash";

  private NodeName() {}

  public static boolean isAllowed(String name) {
    if (name == null || name.isBlank() || name.length() > MAX_LENGTH) {
      return false;
    }
    if (!name.equals(name.strip())) {
      return false;
    }
    if (".".equals(name) || "..".equals(name)) {
      return false;
    }
    return name.indexOf('/') < 0 && name.indexOf('\\') < 0;
  }

  /**
   * Blank and overlong names are reported by the other bean constraints. This check owns the rest.
   */
  public static boolean passesShapeConstraint(String name) {
    if (name == null || name.isBlank() || name.length() > MAX_LENGTH) {
      return true;
    }
    return isAllowed(name);
  }

  public static void check(String name) {
    if (!isAllowed(name)) {
      throw new IllegalArgumentException(MESSAGE);
    }
  }
}
