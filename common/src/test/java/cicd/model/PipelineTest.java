package cicd.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PipelineTest {

  @Test
  void defaultValuesAreSet() {
    Pipeline p = new Pipeline();
    assertNull(p.name);
    assertNull(p.desc);
    assertNotNull(p.stages);
    assertTrue(p.stages.isEmpty());
    assertNotNull(p.jobs);
    assertTrue(p.jobs.isEmpty());
    assertEquals(-1, p.nameLine);
    assertEquals(-1, p.nameCol);
  }

  @Test
  void fieldsCanBeSetDirectly() {
    Pipeline p = new Pipeline();
    p.name = "myPipeline";
    p.desc = "A test pipeline";
    p.nameLine = 2;
    p.nameCol = 10;

    assertEquals("myPipeline", p.name);
    assertEquals("A test pipeline", p.desc);
    assertEquals(2, p.nameLine);
    assertEquals(10, p.nameCol);
  }

  @Test
  void stagesListIsModifiable() {
    Pipeline p = new Pipeline();
    p.stages.add("build");
    p.stages.add("test");
    assertEquals(2, p.stages.size());
    assertEquals("build", p.stages.get(0));
    assertEquals("test", p.stages.get(1));
  }

  @Test
  void jobsMapIsModifiable() {
    Pipeline p = new Pipeline();
    Job job = new Job();
    job.name = "compile";
    p.jobs.put("compile", job);

    assertEquals(1, p.jobs.size());
    assertSame(job, p.jobs.get("compile"));
  }

  @Test
  void jobsMapPreservesInsertionOrder() {
    Pipeline p = new Pipeline();

    Job job1 = new Job();
    job1.name = "first";
    p.jobs.put("first", job1);

    Job job2 = new Job();
    job2.name = "second";
    p.jobs.put("second", job2);

    Job job3 = new Job();
    job3.name = "third";
    p.jobs.put("third", job3);

    String[] keys = p.jobs.keySet().toArray(new String[0]);
    assertEquals("first", keys[0]);
    assertEquals("second", keys[1]);
    assertEquals("third", keys[2]);
  }
}
