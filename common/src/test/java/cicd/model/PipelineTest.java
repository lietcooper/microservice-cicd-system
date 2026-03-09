package cicd.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PipelineTest {

  @Test
  void defaultValuesAreSet() {
    Pipeline pp = new Pipeline();
    assertNull(pp.name);
    assertNull(pp.desc);
    assertNotNull(pp.stages);
    assertTrue(pp.stages.isEmpty());
    assertNotNull(pp.jobs);
    assertTrue(pp.jobs.isEmpty());
    assertEquals(-1, pp.nameLine);
    assertEquals(-1, pp.nameCol);
  }

  @Test
  void fieldsCanBeSetDirectly() {
    Pipeline pp = new Pipeline();
    pp.name = "myPipeline";
    pp.desc = "A test pipeline";
    pp.nameLine = 2;
    pp.nameCol = 10;

    assertEquals("myPipeline", pp.name);
    assertEquals("A test pipeline", pp.desc);
    assertEquals(2, pp.nameLine);
    assertEquals(10, pp.nameCol);
  }

  @Test
  void stagesListIsModifiable() {
    Pipeline pp = new Pipeline();
    pp.stages.add("build");
    pp.stages.add("test");
    assertEquals(2, pp.stages.size());
    assertEquals("build", pp.stages.get(0));
    assertEquals("test", pp.stages.get(1));
  }

  @Test
  void jobsMapIsModifiable() {
    Pipeline pp = new Pipeline();
    Job job = new Job();
    job.name = "compile";
    pp.jobs.put("compile", job);

    assertEquals(1, pp.jobs.size());
    assertSame(job, pp.jobs.get("compile"));
  }

  @Test
  void jobsMapPreservesInsertionOrder() {
    Pipeline pp = new Pipeline();

    Job job1 = new Job();
    job1.name = "first";
    pp.jobs.put("first", job1);

    Job job2 = new Job();
    job2.name = "second";
    pp.jobs.put("second", job2);

    Job job3 = new Job();
    job3.name = "third";
    pp.jobs.put("third", job3);

    String[] keys = pp.jobs.keySet().toArray(new String[0]);
    assertEquals("first", keys[0]);
    assertEquals("second", keys[1]);
    assertEquals("third", keys[2]);
  }
}
