package cicd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Spring Boot entry point for the worker. */
@SpringBootApplication
public class WorkerApplication {

  /** Starts the worker application. */
  public static void main(String[] args) {
    SpringApplication.run(WorkerApplication.class, args);
  }
}
