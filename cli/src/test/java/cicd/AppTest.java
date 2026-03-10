package cicd;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class AppTest {

  @Test
  void testHelpOption() {
    App app = new App();
    CommandLine cmd = new CommandLine(app);
    int exitCode = cmd.execute("--help");
    assertEquals(0, exitCode);
  }

  @Test
  void testVersionOption() {
    App app = new App();
    CommandLine cmd = new CommandLine(app);
    int exitCode = cmd.execute("--version");
    assertEquals(0, exitCode);
  }
}
