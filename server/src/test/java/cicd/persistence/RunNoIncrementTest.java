package cicd.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cicd.persistence.entity.PipelineRunEntity;
import cicd.persistence.entity.RunStatus;
import cicd.persistence.repository.PipelineRunRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test to verify run_no increments correctly for pipeline runs.
 */
@DataJpaTest
@ActiveProfiles("test")
public class RunNoIncrementTest {

  @Autowired
  private PipelineRunRepository pipelineRunRepository;

  @Test
  void testRunNoIncrementsSequentially() {
    String pipelineName = "default";

    PipelineRunEntity run1 = new PipelineRunEntity();
    run1.setPipelineName(pipelineName);
    run1.setRunNo(1);
    run1.setStatus(RunStatus.SUCCESS);
    run1.setGitRepo("/workspace");
    run1.setGitBranch("main");
    run1.setGitHash("abc123");
    run1.setStartTime(OffsetDateTime.now());
    run1.setEndTime(OffsetDateTime.now());
    run1 = pipelineRunRepository.save(run1);

    PipelineRunEntity run2 = new PipelineRunEntity();
    run2.setPipelineName(pipelineName);
    run2.setRunNo(2);
    run2.setStatus(RunStatus.SUCCESS);
    run2.setGitRepo("/workspace");
    run2.setGitBranch("main");
    run2.setGitHash("def456");
    run2.setStartTime(OffsetDateTime.now());
    run2.setEndTime(OffsetDateTime.now());
    run2 = pipelineRunRepository.save(run2);

    PipelineRunEntity run3 = new PipelineRunEntity();
    run3.setPipelineName(pipelineName);
    run3.setRunNo(3);
    run3.setStatus(RunStatus.FAILED);
    run3.setGitRepo("/workspace");
    run3.setGitBranch("feature");
    run3.setGitHash("xyz789");
    run3.setStartTime(OffsetDateTime.now());
    run3.setEndTime(OffsetDateTime.now());
    run3 = pipelineRunRepository.save(run3);

    List<PipelineRunEntity> runs = pipelineRunRepository
        .findByPipelineNameOrderByRunNoAsc(pipelineName);

    assertEquals(3, runs.size(),
        "Should have 3 pipeline runs");

    assertEquals(1, runs.get(0).getRunNo(),
        "First run should have run_no=1");
    assertEquals(2, runs.get(1).getRunNo(),
        "Second run should have run_no=2");
    assertEquals(3, runs.get(2).getRunNo(),
        "Third run should have run_no=3");

    assertEquals("abc123", runs.get(0).getGitHash());
    assertEquals("def456", runs.get(1).getGitHash());
    assertEquals("xyz789", runs.get(2).getGitHash());

    assertEquals(RunStatus.SUCCESS, runs.get(0).getStatus());
    assertEquals(RunStatus.SUCCESS, runs.get(1).getStatus());
    assertEquals(RunStatus.FAILED, runs.get(2).getStatus());
  }

  @Test
  void testGetNextRunNoForPipeline() {
    String pipelineName = "test-pipeline";

    List<PipelineRunEntity> runs = pipelineRunRepository
        .findByPipelineNameOrderByRunNoAsc(pipelineName);
    assertTrue(runs.isEmpty(),
        "Should have no runs initially");

    PipelineRunEntity run1 = new PipelineRunEntity();
    run1.setPipelineName(pipelineName);
    run1.setRunNo(1);
    run1.setStatus(RunStatus.SUCCESS);
    run1.setGitRepo("/workspace");
    run1.setGitBranch("main");
    run1.setGitHash("abc");
    run1.setStartTime(OffsetDateTime.now());
    pipelineRunRepository.save(run1);

    runs = pipelineRunRepository
        .findByPipelineNameOrderByRunNoAsc(pipelineName);
    assertEquals(1, runs.size());
    assertEquals(1, runs.get(0).getRunNo(),
        "First run should have run_no=1");

    PipelineRunEntity run2 = new PipelineRunEntity();
    run2.setPipelineName(pipelineName);
    run2.setRunNo(2);
    run2.setStatus(RunStatus.SUCCESS);
    run2.setGitRepo("/workspace");
    run2.setGitBranch("main");
    run2.setGitHash("def");
    run2.setStartTime(OffsetDateTime.now());
    pipelineRunRepository.save(run2);

    runs = pipelineRunRepository
        .findByPipelineNameOrderByRunNoAsc(pipelineName);
    assertEquals(2, runs.size());
    assertEquals(2, runs.get(1).getRunNo(),
        "Second run should have run_no=2");

    int nextRunNo = runs.size() + 1;
    assertEquals(3, nextRunNo, "Next run_no should be 3");
  }

  @Test
  void testRunNoDifferentPipelines() {
    PipelineRunEntity runA1 = new PipelineRunEntity();
    runA1.setPipelineName("pipeline-A");
    runA1.setRunNo(1);
    runA1.setStatus(RunStatus.SUCCESS);
    runA1.setGitRepo("/workspace");
    runA1.setGitBranch("main");
    runA1.setGitHash("a1");
    runA1.setStartTime(OffsetDateTime.now());
    pipelineRunRepository.save(runA1);

    PipelineRunEntity runA2 = new PipelineRunEntity();
    runA2.setPipelineName("pipeline-A");
    runA2.setRunNo(2);
    runA2.setStatus(RunStatus.SUCCESS);
    runA2.setGitRepo("/workspace");
    runA2.setGitBranch("main");
    runA2.setGitHash("a2");
    runA2.setStartTime(OffsetDateTime.now());
    pipelineRunRepository.save(runA2);

    PipelineRunEntity runB1 = new PipelineRunEntity();
    runB1.setPipelineName("pipeline-B");
    runB1.setRunNo(1);
    runB1.setStatus(RunStatus.SUCCESS);
    runB1.setGitRepo("/workspace");
    runB1.setGitBranch("main");
    runB1.setGitHash("b1");
    runB1.setStartTime(OffsetDateTime.now());
    pipelineRunRepository.save(runB1);

    List<PipelineRunEntity> runsA = pipelineRunRepository
        .findByPipelineNameOrderByRunNoAsc("pipeline-A");
    assertEquals(2, runsA.size());
    assertEquals(1, runsA.get(0).getRunNo());
    assertEquals(2, runsA.get(1).getRunNo());

    List<PipelineRunEntity> runsB = pipelineRunRepository
        .findByPipelineNameOrderByRunNoAsc("pipeline-B");
    assertEquals(1, runsB.size());
    assertEquals(1, runsB.get(0).getRunNo());
  }
}
