package filemanager.exception;

public final class DuplicateNodeNameException extends NodeException {

  public DuplicateNodeNameException() {
    super("A file or folder with this name already exists");
  }
}
