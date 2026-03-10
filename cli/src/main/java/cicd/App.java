package cicd;

import cicd.cmd.DryrunCmd;
import cicd.cmd.ReportCmd;
import cicd.cmd.RunCmd;
import cicd.cmd.VerifyCmd;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/** CLI entry point for CI/CD pipeline management. */
@Command(
    name = "cicd",
    description = "CI/CD pipeline management tool",
    mixinStandardHelpOptions = true,
    version = "0.1.0",
    subcommands = {
        VerifyCmd.class,
        DryrunCmd.class,
        RunCmd.class,
        ReportCmd.class
    }
)
public class App implements Runnable {

  /** Launches the CLI application. */
  public static void main(String[] args) {
    int exitCode = new CommandLine(new App()).execute(args);
    System.exit(exitCode);
  }

  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }
}
