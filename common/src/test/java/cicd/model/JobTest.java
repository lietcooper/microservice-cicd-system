package cicd.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class JobTest {

  @Test
  void defaultValuesAreSet() {
    Job jj = new Job();
    assertNull(jj.name);
    assertNull(jj.stage);
    assertNull(jj.image);
    assertNotNull(jj.script);
    assertTrue(jj.script.isEmpty());
    assertNotNull(jj.needs);
    assertTrue(jj.needs.isEmpty());
    assertEquals(-1, jj.line);
    assertEquals(-1, jj.col);
    assertEquals(-1, jj.needsLine);
    assertEquals(-1, jj.needsCol);
  }

  @Test
  void fieldsCanBeSetDirectly() {
    Job jj = new Job();
    jj.name = "compile";
    jj.stage = "build";
    jj.image = "gradle:jdk21";
    jj.line = 5;
    jj.col = 1;
    jj.needsLine = 10;
    jj.needsCol = 3;

    assertEquals("compile", jj.name);
    assertEquals("build", jj.stage);
    assertEquals("gradle:jdk21", jj.image);
    assertEquals(5, jj.line);
    assertEquals(1, jj.col);
    assertEquals(10, jj.needsLine);
    assertEquals(3, jj.needsCol);
  }

  @Test
  void scriptListIsModifiable() {
    Job jj = new Job();
    jj.script.add("echo step1");
    jj.script.add("echo step2");

    assertEquals(2, jj.script.size());
    assertEquals("echo step1", jj.script.get(0));
    assertEquals("echo step2", jj.script.get(1));
  }

  @Test
  void needsListIsModifiable() {
    Job jj = new Job();
    jj.needs.add("jobA");
    jj.needs.add("jobB");

    assertEquals(2, jj.needs.size());
    assertTrue(jj.needs.contains("jobA"));
    assertTrue(jj.needs.contains("jobB"));
  }

  @Test
  void scriptCanBeReplacedWithImmutableList() {
    Job jj = new Job();
    jj.script = List.of("cmd1", "cmd2", "cmd3");

    assertEquals(3, jj.script.size());
    assertEquals("cmd1", jj.script.get(0));
  }

  @Test
  void needsCanBeReplacedWithImmutableList() {
    Job jj = new Job();
    jj.needs = List.of("dep1", "dep2");

    assertEquals(2, jj.needs.size());
    assertTrue(jj.needs.contains("dep1"));
    assertTrue(jj.needs.contains("dep2"));
  }
}
