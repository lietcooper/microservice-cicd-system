package cicd.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JobTest {

  @Test
  void defaultValuesAreSet() {
    Job j = new Job();
    assertNull(j.name);
    assertNull(j.stage);
    assertNull(j.image);
    assertNotNull(j.script);
    assertTrue(j.script.isEmpty());
    assertNotNull(j.needs);
    assertTrue(j.needs.isEmpty());
    assertEquals(-1, j.line);
    assertEquals(-1, j.col);
    assertEquals(-1, j.needsLine);
    assertEquals(-1, j.needsCol);
  }

  @Test
  void fieldsCanBeSetDirectly() {
    Job j = new Job();
    j.name = "compile";
    j.stage = "build";
    j.image = "gradle:jdk21";
    j.line = 5;
    j.col = 1;
    j.needsLine = 10;
    j.needsCol = 3;

    assertEquals("compile", j.name);
    assertEquals("build", j.stage);
    assertEquals("gradle:jdk21", j.image);
    assertEquals(5, j.line);
    assertEquals(1, j.col);
    assertEquals(10, j.needsLine);
    assertEquals(3, j.needsCol);
  }

  @Test
  void scriptListIsModifiable() {
    Job j = new Job();
    j.script.add("echo step1");
    j.script.add("echo step2");

    assertEquals(2, j.script.size());
    assertEquals("echo step1", j.script.get(0));
    assertEquals("echo step2", j.script.get(1));
  }

  @Test
  void needsListIsModifiable() {
    Job j = new Job();
    j.needs.add("jobA");
    j.needs.add("jobB");

    assertEquals(2, j.needs.size());
    assertTrue(j.needs.contains("jobA"));
    assertTrue(j.needs.contains("jobB"));
  }

  @Test
  void scriptCanBeReplacedWithImmutableList() {
    Job j = new Job();
    j.script = List.of("cmd1", "cmd2", "cmd3");

    assertEquals(3, j.script.size());
    assertEquals("cmd1", j.script.get(0));
  }

  @Test
  void needsCanBeReplacedWithImmutableList() {
    Job j = new Job();
    j.needs = List.of("dep1", "dep2");

    assertEquals(2, j.needs.size());
    assertTrue(j.needs.contains("dep1"));
    assertTrue(j.needs.contains("dep2"));
  }
}
