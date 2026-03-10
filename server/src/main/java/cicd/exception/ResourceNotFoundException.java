package cicd.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when a requested resource is not found. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

  /** Creates exception with the given message. */
  public ResourceNotFoundException(String message) {
    super(message);
  }
}
