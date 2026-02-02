package cicd.validator;

import cicd.model.*;
import cicd.parser.YamlParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ValidatorTest {

    @TempDir
    Path tmp;

    @Test
    void validPipeline() throws IOException {
        String yaml = """
            pipeline:
              name: test
            stages:
              - build
            compile:
              stage: build
              image: alpine
              script: echo hi
            """;
        
        List<String> errors = validate(yaml);
        assertTrue(errors.isEmpty());
    }

    @Test
    void noStages() throws IOException {
        String yaml = """
            pipeline:
              name: test
            stages: []
            """;
        
        List<String> errors = validate(yaml);
        assertTrue(errors.stream().anyMatch(e -> e.contains("at least 1 stage")));
    }

    @Test
    void emptyStage() throws IOException {
        String yaml = """
            pipeline:
              name: test
            stages:
              - build
              - test
            compile:
              stage: build
              image: alpine
              script: echo
            """;
        
        List<String> errors = validate(yaml);
        assertTrue(errors.stream().anyMatch(e -> e.contains("stage `test` has no jobs")));
    }

    @Test
    void needsNotExist() throws IOException {
        String yaml = """
            pipeline:
              name: test
            stages:
              - build
            compile:
              stage: build
              image: alpine
              script: echo
              needs:
                - notexist
            """;
        
        List<String> errors = validate(yaml);
        assertTrue(errors.stream().anyMatch(e -> e.contains("does not exist")));
    }

    @Test
    void cycleDetection() throws IOException {
        String yaml = """
            pipeline:
              name: test
            stages:
              - build
            job1:
              stage: build
              image: alpine
              script: echo
              needs:
                - job2
            job2:
              stage: build
              image: alpine
              script: echo
              needs:
                - job1
            """;
        
        List<String> errors = validate(yaml);
        assertTrue(errors.stream().anyMatch(e -> e.contains("cycle detected")));
    }

    private List<String> validate(String yaml) throws IOException {
        Path f = tmp.resolve("p.yaml");
        Files.writeString(f, yaml);
        
        YamlParser parser = new YamlParser(f.toString());
        Pipeline p = parser.parse();
        
        List<String> errors = new java.util.ArrayList<>(parser.getErrors());
        if (p != null) {
            Validator v = new Validator(f.toString(), p);
            errors.addAll(v.validate());
        }
        return errors;
    }
}
