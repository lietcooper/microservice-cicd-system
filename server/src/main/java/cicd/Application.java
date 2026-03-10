package cicd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Spring Boot entry point for the CI/CD server. */
@SpringBootApplication
public class Application {

  /** Starts the server application. */
  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
