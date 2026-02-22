package cicd.parser;

import cicd.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class YamlParserTest {

    @TempDir
    Path tmp;

    @Test
    void parseValid() throws IOException {
        String yaml = """
            pipeline:
              name: test
              description: a test
            stages:
              - build
              - test
            compile:
              stage: build
              image: gradle:jdk17
              script: ./gradlew build
            unittest:
              stage: test
              image: gradle:jdk17
              script:
                - ./gradlew test
            """;
        
        Path f = tmp.resolve("p.yaml");
        Files.writeString(f, yaml);
        
        YamlParser parser = new YamlParser(f.toString());
        Pipeline p = parser.parse();
        
        assertTrue(parser.getErrors().isEmpty());
        assertEquals("test", p.name);
        assertEquals(2, p.stages.size());
        assertEquals(2, p.jobs.size());
    }

    @Test
    void parseNeeds() throws IOException {
        String yaml = """
            pipeline:
              name: test
            stages:
              - test
            job1:
              stage: test
              image: alpine
              script: echo 1
            job2:
              stage: test
              image: alpine
              script: echo 2
              needs:
                - job1
            """;
        
        Path f = tmp.resolve("p.yaml");
        Files.writeString(f, yaml);
        
        YamlParser parser = new YamlParser(f.toString());
        Pipeline p = parser.parse();
        
        assertTrue(parser.getErrors().isEmpty());
        assertEquals(1, p.jobs.get("job2").needs.size());
    }

    @Test
    void parseNotFound() {
        YamlParser parser = new YamlParser("/no/such/file.yaml");
        parser.parse();
        assertFalse(parser.getErrors().isEmpty());
    }

    @Test
    void parseEmpty() throws IOException {
        Path f = tmp.resolve("empty.yaml");
        Files.writeString(f, "");
        
        YamlParser parser = new YamlParser(f.toString());
        parser.parse();
        assertFalse(parser.getErrors().isEmpty());
    }
}
