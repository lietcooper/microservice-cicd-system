package cicd;

import cicd.cmd.VerifyCmd;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command (
    name = "cicd",
    description = "CI/CD pipeline management tool",
    mixinStandardHelpOptions = true,
    version = "0.1.0",
    subcommands = {
        VerifyCmd.class
    }
)
public class App implements Runnable {

  public static void main(String[] args) {
    int exitCode = new CommandLine(new App()).execute(args);
    System.exit(exitCode);
  }

  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }
}
