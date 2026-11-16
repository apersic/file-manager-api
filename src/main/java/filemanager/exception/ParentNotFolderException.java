package filemanager.exception;

public final class ParentNotFolderException extends NodeException {

  public ParentNotFolderException() {
    super("Parent is not a folder");
  }
}
