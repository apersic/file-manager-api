package filemanager.exception;

public abstract sealed class NodeException extends RuntimeException
    permits NodeNotFoundException,
        DuplicateNodeNameException,
        ParentNotFolderException,
        InvalidCursorException {

  protected NodeException(String message) {
    super(message);
  }
}
