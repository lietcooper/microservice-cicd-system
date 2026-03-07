package cicd.cmd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * Tests for VerifyCmd.
 *
 * <p>VerifyCmd checks for .git directory in the working directory to confirm
 * we are in a git repo root. The test working directory is set to rootDir in
 * build.gradle, which IS a git repo, so git repo checks pass.
 */
class VerifyCmdTest {

  @TempDir
  Path tmp;

  private ByteArrayOutputStream outContent;
  private ByteArrayOutputStream errContent;
  private PrintStream originalOut;
  private PrintStream originalErr;

  @BeforeEach
  void setUpStreams() {
    outContent = new ByteArrayOutputStream();
    errContent = new ByteArrayOutputStream();
    originalOut = System.out;
    originalErr = System.err;
    System.setOut(new PrintStream(outContent));
    System.setErr(new PrintStream(errContent));
  }

  void restoreStreams() {
    System.setOut(originalOut);
    System.setErr(originalErr);
  }

  // ── Valid file cases ─────────────────────────────────────────────────────────

  @Test
  void validDefaultPipelineFile() {
    // The project's own .pipelines/default.yaml should be valid
    int code = new CommandLine(new VerifyCmd()).execute(".pipelines/default.yaml");
    restoreStreams();
    assertEquals(0, code);
    assertTrue(outContent.toString().contains("Valid!"));
  }

  @Test
  void validDirectory() throws IOException {
    // Use a temp directory with only valid files to avoid side effects
    // from the project's .pipelines/ which may contain invalid fixtures
    Path dir = tmp.resolve("cleanpipes");
    Files.createDirectory(dir);

    String yaml = """
        pipeline:
          name: cleanpipeline
        stages:
          - build
        compile:
          stage: build
          image: alpine
          script: echo build
        """;
    Files.writeString(dir.resolve("clean.yaml"), yaml);

    int code = new CommandLine(new VerifyCmd()).execute(dir.toString());
    restoreStreams();
    assertEquals(0, code);
  }

  // ── Invalid file cases ───────────────────────────────────────────────────────

  @Test
  void fileNotFound() {
    int code = new CommandLine(new VerifyCmd()).execute(
        tmp.resolve("nonexistent.yaml").toString());
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("file not found"));
  }

  @Test
  void invalidYamlFileProducesErrors() throws IOException {
    String invalidYaml = "not: valid: yaml: content: :::";
    Path f = tmp.resolve("invalid.yaml");
    Files.writeString(f, invalidYaml);

    int code = new CommandLine(new VerifyCmd()).execute(f.toString());
    restoreStreams();
    assertEquals(1, code);
  }

  @Test
  void pipelineWithEmptyStageIsInvalid() throws IOException {
    // A stage defined but no job assigned to it
    String yaml = """
        pipeline:
          name: emptystage
        stages:
          - build
          - test
        compile:
          stage: build
          image: alpine
          script: echo build
        """;
    Path f = tmp.resolve("emptystage.yaml");
    Files.writeString(f, yaml);

    int code = new CommandLine(new VerifyCmd()).execute(f.toString());
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("has no jobs"));
  }

  @Test
  void pipelineWithCycleIsInvalid() throws IOException {
    String yaml = """
        pipeline:
          name: cycle
        stages:
          - build
        jobA:
          stage: build
          image: alpine
          script: echo A
          needs:
            - jobB
        jobB:
          stage: build
          image: alpine
          script: echo B
          needs:
            - jobA
        """;
    Path f = tmp.resolve("cycle.yaml");
    Files.writeString(f, yaml);

    int code = new CommandLine(new VerifyCmd()).execute(f.toString());
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("cycle detected"));
  }

  @Test
  void emptyYamlFileIsInvalid() throws IOException {
    Path f = tmp.resolve("empty.yaml");
    Files.writeString(f, "");

    int code = new CommandLine(new VerifyCmd()).execute(f.toString());
    restoreStreams();
    assertEquals(1, code);
    org.junit.jupiter.api.Assertions.assertFalse(outContent.toString().contains("Valid!"));
  }

  // ── Directory cases ──────────────────────────────────────────────────────────

  @Test
  void emptyDirectoryProducesError() throws IOException {
    Path dir = tmp.resolve("pipelines");
    Files.createDirectory(dir);

    int code = new CommandLine(new VerifyCmd()).execute(dir.toString());
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("No YAML files found"));
  }

  @Test
  void directoryWithValidFilesSucceeds() throws IOException {
    Path dir = tmp.resolve("pipes");
    Files.createDirectory(dir);

    String yaml = """
        pipeline:
          name: unique-pipeline-1
        stages:
          - build
        compile:
          stage: build
          image: alpine
          script: echo build
        """;
    Files.writeString(dir.resolve("pipe.yaml"), yaml);

    int code = new CommandLine(new VerifyCmd()).execute(dir.toString());
    restoreStreams();
    assertEquals(0, code);
  }

  @Test
  void directoryWithDuplicateNamesProducesError() throws IOException {
    Path dir = tmp.resolve("dupes");
    Files.createDirectory(dir);

    String yaml1 = """
        pipeline:
          name: samename
        stages:
          - build
        compile:
          stage: build
          image: alpine
          script: echo 1
        """;
    String yaml2 = """
        pipeline:
          name: samename
        stages:
          - build
        compile:
          stage: build
          image: alpine
          script: echo 2
        """;
    Files.writeString(dir.resolve("a.yaml"), yaml1);
    Files.writeString(dir.resolve("b.yaml"), yaml2);

    int code = new CommandLine(new VerifyCmd()).execute(dir.toString());
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("samename"));
  }

  @Test
  void directoryWithOnlyYmlExtensionFilesSucceeds() throws IOException {
    Path dir = tmp.resolve("ymlpipes");
    Files.createDirectory(dir);

    String yaml = """
        pipeline:
          name: ymltest
        stages:
          - build
        compile:
          stage: build
          image: alpine
          script: echo
        """;
    Files.writeString(dir.resolve("pipe.yml"), yaml);

    int code = new CommandLine(new VerifyCmd()).execute(dir.toString());
    restoreStreams();
    assertEquals(0, code);
  }

  // ── Missing pipeline.name tests ──────────────────────────────────────────────

  @Test
  void pipelineMissingNameIsValid() throws IOException {
    // The current implementation does not validate missing pipeline.name
    // (Validator only checks stages, jobs, needs and cycles)
    // A pipeline without a name passes validation as a file
    String yaml = """
        pipeline:
          description: no name here
        stages:
          - build
        compile:
          stage: build
          image: alpine
          script: echo ok
        """;
    Path f = tmp.resolve("noname.yaml");
    Files.writeString(f, yaml);

    int code = new CommandLine(new VerifyCmd()).execute(f.toString());
    restoreStreams();
    assertEquals(0, code);
  }

  @Test
  void pipelineWithNeedsJobInDifferentStageIsValid() throws IOException {
    // The current Validator only checks that needed jobs exist by name,
    // not that they are in the same stage. Cross-stage needs are currently valid.
    String yaml = """
        pipeline:
          name: cross-stage
        stages:
          - build
          - test
        compile:
          stage: build
          image: alpine
          script: echo compile
        unittest:
          stage: test
          image: alpine
          script: echo test
          needs:
            - compile
        """;
    Path f = tmp.resolve("crossstage.yaml");
    Files.writeString(f, yaml);

    int code = new CommandLine(new VerifyCmd()).execute(f.toString());
    restoreStreams();
    // Cross-stage needs is currently not rejected by the Validator
    assertEquals(0, code);
  }

  @Test
  void pipelineWithEmptyNeedsListIsValid() throws IOException {
    // The current Validator does not check for empty needs lists.
    // A job with needs: [] passes validation.
    String yaml = """
        pipeline:
          name: emptyneedslist
        stages:
          - build
        compile:
          stage: build
          image: alpine
          script: echo ok
          needs: []
        """;
    Path f = tmp.resolve("emptyneedsy.yaml");
    Files.writeString(f, yaml);

    int code = new CommandLine(new VerifyCmd()).execute(f.toString());
    restoreStreams();
    // Empty needs list is not currently rejected
    assertEquals(0, code);
  }

  @Test
  void pipelineWithNeedsReferencingUnknownJobIsInvalid() throws IOException {
    String yaml = """
        pipeline:
          name: unknowndep
        stages:
          - build
        compile:
          stage: build
          image: alpine
          script: echo ok
          needs:
            - nonexistentjob
        """;
    Path f = tmp.resolve("unknowndep.yaml");
    Files.writeString(f, yaml);

    int code = new CommandLine(new VerifyCmd()).execute(f.toString());
    restoreStreams();
    assertEquals(1, code);
  }

  @Test
  void singleFileVerifySkipsUniquenessCheck() throws IOException {
    // When verifying a single file, pipeline name uniqueness is not checked
    // Two separate files with same name should each be valid individually
    Path f = tmp.resolve("single.yaml");
    String yaml = """
        pipeline:
          name: samename
        stages:
          - build
        compile:
          stage: build
          image: alpine
          script: echo ok
        """;
    Files.writeString(f, yaml);

    int code = new CommandLine(new VerifyCmd()).execute(f.toString());
    restoreStreams();
    assertEquals(0, code);
  }

  @Test
  void directoryWithMixedValidAndInvalidFilesReturnsError() throws IOException {
    Path dir = tmp.resolve("mixed");
    Files.createDirectory(dir);

    String validYaml = """
        pipeline:
          name: valid-one
        stages:
          - build
        compile:
          stage: build
          image: alpine
          script: echo valid
        """;
    String invalidYaml = """
        pipeline:
          name: invalid-one
        stages:
          - build
          - test
        compile:
          stage: build
          image: alpine
          script: echo valid
        """;
    // invalid: 'test' stage has no jobs
    Files.writeString(dir.resolve("valid.yaml"), validYaml);
    Files.writeString(dir.resolve("invalid.yaml"), invalidYaml);

    int code = new CommandLine(new VerifyCmd()).execute(dir.toString());
    restoreStreams();
    assertEquals(1, code);
  }

  @Test
  void defaultPipelineFileUsedWhenNoArgGiven() {
    // No argument means default .pipelines/default.yaml is verified
    // Workdir is repo root which has a valid .pipelines/default.yaml
    int code = new CommandLine(new VerifyCmd()).execute();
    restoreStreams();
    assertEquals(0, code);
  }

  @Test
  void pipelineWithMultipleScriptsInJobIsValid() throws IOException {
    String yaml = """
        pipeline:
          name: multiscript
        stages:
          - test
        check:
          stage: test
          image: gradle:jdk21
          script:
            - gradle checkstyleMain
            - gradle test
        """;
    Path f = tmp.resolve("multiscript.yaml");
    Files.writeString(f, yaml);

    int code = new CommandLine(new VerifyCmd()).execute(f.toString());
    restoreStreams();
    assertEquals(0, code);
    assertTrue(outContent.toString().contains("Valid!"));
  }

  @Test
  void pipelineWithNoStagesKeyIsInvalid() throws IOException {
    // When stages key is absent, p.stages is empty → Validator rejects it
    // with "at least 1 stage must be defined". Default stages require
    // explicit declaration in the YAML file.
    String yaml = """
        pipeline:
          name: nostageskey
        compile:
          stage: build
          image: alpine
          script: echo build
        """;
    Path f = tmp.resolve("nostageskey.yaml");
    Files.writeString(f, yaml);

    int code = new CommandLine(new VerifyCmd()).execute(f.toString());
    restoreStreams();
    assertEquals(1, code);
    assertTrue(errContent.toString().contains("at least 1 stage"));
  }
}
