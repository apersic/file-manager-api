package filemanager.exception;

public final class RootDeleteException extends NodeException {

  public RootDeleteException() {
    super("The root folder cannot be deleted");
  }
}
