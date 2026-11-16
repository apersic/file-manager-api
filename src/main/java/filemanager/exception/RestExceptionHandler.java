package filemanager.exception;

import filemanager.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RestExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
    Map<String, String> errors = new LinkedHashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
    ex.getBindingResult()
        .getGlobalErrors()
        .forEach(error -> errors.put(error.getObjectName(), error.getDefaultMessage()));
    log.warn("Validation error: {}", errors);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<Map<String, String>> handleConstraintViolation(
      ConstraintViolationException ex) {
    Map<String, String> errors = new LinkedHashMap<>();
    for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
      errors.put(parameterName(violation), violation.getMessage());
    }
    log.warn("Validation error: {}", errors);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  public ResponseEntity<Map<String, String>> handleMethodValidation(
      HandlerMethodValidationException ex) {
    Map<String, String> errors = new LinkedHashMap<>();
    for (ParameterValidationResult result : ex.getParameterValidationResults()) {
      String name = parameterName(result);
      String message =
          result.getResolvableErrors().isEmpty()
              ? "Invalid value"
              : result.getResolvableErrors().get(0).getDefaultMessage();
      errors.put(name, message);
    }
    log.warn("Validation error: {}", errors);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
  }

  @ExceptionHandler(NodeException.class)
  public ResponseEntity<ErrorResponse> handleNode(NodeException ex, HttpServletRequest request) {
    HttpStatus status =
        switch (ex) {
          case DuplicateNodeNameException ignored -> HttpStatus.CONFLICT;
          case ParentNotFolderException ignored -> HttpStatus.CONFLICT;
          case RootDeleteException ignored -> HttpStatus.CONFLICT;
          case InvalidCursorException ignored -> HttpStatus.BAD_REQUEST;
          case NodeNotFoundException ignored -> HttpStatus.NOT_FOUND;
        };
    log.warn("Node request failed: {}", ex.getMessage());
    return error(ex.getMessage(), status, request);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadable(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
    log.warn("Malformed request body: {}", ex.getMostSpecificCause().getClass().getSimpleName());
    return error("Malformed request body", HttpStatus.BAD_REQUEST, request);
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ErrorResponse> handleMissingParameter(
      MissingServletRequestParameterException ex, HttpServletRequest request) {
    log.warn("Missing request parameter: {}", ex.getParameterName());
    return error("Missing request parameter", HttpStatus.BAD_REQUEST, request);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(
      MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
    log.warn("Mistyped request parameter: {}", ex.getName());
    return error("Mistyped request parameter", HttpStatus.BAD_REQUEST, request);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
    log.warn("Method not supported: {}", ex.getMethod());
    return error("Method not allowed", HttpStatus.METHOD_NOT_ALLOWED, request);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMediaType(
      HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
    log.warn("Unsupported media type: {}", ex.getContentType());
    return error("Unsupported media type", HttpStatus.UNSUPPORTED_MEDIA_TYPE, request);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleIllegalArgument(
      IllegalArgumentException ex, HttpServletRequest request) {
    log.warn("Invalid argument: {}", ex.getMessage());
    return error(ex.getMessage(), HttpStatus.BAD_REQUEST, request);
  }

  @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
  public ResponseEntity<ErrorResponse> handleNotFound(Exception ex, HttpServletRequest request) {
    if (isProbePath(request.getRequestURI())) {
      log.debug("Probe request ignored: {}", request.getRequestURI());
    } else {
      log.debug("No resource found: {}", request.getRequestURI());
    }
    return error("Not Found", HttpStatus.NOT_FOUND, request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
    log.error("Unexpected error", ex);
    return error("An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR, request);
  }

  private ResponseEntity<ErrorResponse> error(
      String message, HttpStatus status, HttpServletRequest request) {
    ErrorResponse body =
        new ErrorResponse(
            message,
            status.value(),
            status.getReasonPhrase(),
            Instant.now(),
            request.getRequestURI());
    return ResponseEntity.status(status).body(body);
  }

  private static String parameterName(ConstraintViolation<?> violation) {
    String path = violation.getPropertyPath().toString();
    int dot = path.lastIndexOf('.');
    return dot < 0 ? path : path.substring(dot + 1);
  }

  private static String parameterName(ParameterValidationResult result) {
    RequestParam requestParam =
        result.getMethodParameter().getParameterAnnotation(RequestParam.class);
    if (requestParam != null && !requestParam.value().isBlank()) {
      return requestParam.value();
    }
    String name = result.getMethodParameter().getParameterName();
    return name == null ? "request" : name;
  }

  static boolean isProbePath(String path) {
    if (path == null) {
      return false;
    }
    String lower = path.toLowerCase();
    return lower.contains(".php")
        || lower.contains("/vendor/")
        || lower.contains("/wp-admin")
        || lower.contains("/wp-login")
        || lower.contains("eval-stdin")
        || lower.contains("/.env")
        || lower.contains("/.git/")
        || lower.contains("wp-content")
        || lower.contains("phpunit");
  }
}
